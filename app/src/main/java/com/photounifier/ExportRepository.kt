package com.photounifier

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs explicit user-requested exports and reports progress without retaining full batches in memory. */
class ExportRepository {
    interface Observer {
        fun onProgress(completed: Int, total: Int, message: String)
        fun onError(message: String)
        fun onFinished(success: Int, failed: Int, error: String?)
        fun onItemFinished(itemId: String, success: Boolean) {}
    }

    fun exportAsImages(context: Context, items: List<ExportImage>, observer: Observer) {
        val resolver = context.contentResolver
        val stamp = timestamp()
        var success = 0
        var failed = 0
        var lastError: String? = null

        items.forEachIndexed { index, item ->
            var destination: Uri? = null
            var itemSucceeded = false
            try {
                check(item.file.isFile) { context.getString(R.string.export_temp_image_missing) }
                val name = "${item.file.nameWithoutExtension}_${stamp}_${index + 1}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/PhotoUnifier"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException(context.getString(R.string.error_create_image_file))
                val output = resolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException(context.getString(R.string.error_open_image_export))
                copyTemporaryImageToOutput(item.file, output)
                val published = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                if (resolver.update(destination, published, null, null) <= 0) {
                    throw IllegalStateException(context.getString(R.string.error_image_unavailable_after_export))
                }
                success++
                itemSucceeded = true
            } catch (error: Exception) {
                failed++
                lastError = error.message ?: error.javaClass.simpleName
                destination?.let { runCatching { resolver.delete(it, null, null) } }
                notifyObserver("onError") { observer.onError("${item.displayName}: ${lastError}") }
            } finally {
                notifyObserver("onItemFinished") { observer.onItemFinished(item.id, itemSucceeded) }
                notifyObserver("onProgress") {
                    observer.onProgress(index + 1, items.size, context.getString(R.string.export_image_progress, index + 1, items.size))
                }
            }
        }
        notifyObserver("onFinished") { observer.onFinished(success, failed, lastError) }
    }

    fun exportAsPdf(context: Context, items: List<ExportImage>, observer: Observer) {
        require(items.isNotEmpty()) { context.getString(R.string.error_select_images_for_pdf) }
        val resolver = context.contentResolver
        val stamp = timestamp()
        val name = "PhotoUnifier_${stamp}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOCUMENTS}/PhotoUnifier"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var destination: Uri? = null
        var document: PdfDocument? = null
        var completed = 0
        var failed = items.size
        var resultError: String? = null
        try {
            destination = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw IllegalStateException(context.getString(R.string.error_create_pdf_file))
            val pdf = PdfDocument()
            document = pdf
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            items.forEachIndexed { index, item ->
                check(item.file.isFile) { context.getString(R.string.error_temp_image_missing_for_item, item.displayName) }
                val bitmap = item.file.inputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: throw IllegalStateException(context.getString(R.string.error_read_image_for_export, item.displayName))
                try {
                    val landscape = bitmap.width > bitmap.height
                    val pageWidth = if (landscape) PAGE_LONG_SIDE else PAGE_SHORT_SIDE
                    val pageHeight = if (landscape) PAGE_SHORT_SIDE else PAGE_LONG_SIDE
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(pageInfo)
                    try {
                        val canvas: Canvas = page.canvas
                        canvas.drawColor(Color.WHITE)
                        val availableWidth = pageWidth - PAGE_MARGIN * 2f
                        val availableHeight = pageHeight - PAGE_MARGIN * 2f
                        val scale = minOf(
                            availableWidth / bitmap.width.toFloat(),
                            availableHeight / bitmap.height.toFloat()
                        )
                        val drawnWidth = bitmap.width * scale
                        val drawnHeight = bitmap.height * scale
                        val left = (pageWidth - drawnWidth) / 2f
                        val top = (pageHeight - drawnHeight) / 2f
                        canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(left, top, left + drawnWidth, top + drawnHeight),
                            paint
                        )
                    } finally {
                        pdf.finishPage(page)
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                notifyObserver("onProgress") {
                    observer.onProgress(index + 1, items.size, context.getString(R.string.export_pdf_progress, index + 1, items.size))
                }
            }

            val output = resolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException(context.getString(R.string.error_open_pdf_write))
            output.use { pdf.writeTo(it) }
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(destination, published, null, null) <= 0) {
                throw IllegalStateException(context.getString(R.string.error_pdf_unavailable_after_save))
            }
            // Publication is the commit point. Observer callbacks happen only after
            // this point and must never turn a completed export into a failed one.
            completed = 1
            failed = 0
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            resultError = message
            destination?.let { runCatching { resolver.delete(it, null, null) } }
            notifyObserver("onError") {
                observer.onError(context.getString(R.string.error_pdf_export_item, message))
            }
        } finally {
            runCatching { document?.close() }
        }
        notifyObserver("onFinished") { observer.onFinished(completed, failed, resultError) }
    }

    private fun notifyObserver(name: String, callback: () -> Unit) {
        safelyNotifyExportObserver(callback) { error ->
            Log.w(EXPORT_LOG_TAG, "Export observer callback $name failed", error)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private companion object {
        const val PAGE_SHORT_SIDE = 595
        const val PAGE_LONG_SIDE = 842
        const val PAGE_MARGIN = 24
        const val EXPORT_LOG_TAG = "PhotoUnifierExport"
    }
}

/** Observer/UI failures must not roll back a file that has already been published. */
internal fun safelyNotifyExportObserver(
    callback: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    try {
        callback()
    } catch (error: Exception) {
        onFailure(error)
    }
}

/**
 * Closes the destination even when opening the temporary source fails.
 * The destination is a MediaStore stream and must not be left open if a
 * processing file is missing or cannot be read.
 */
internal fun copyTemporaryImageToOutput(file: File, output: OutputStream) {
    output.use { destination ->
        file.inputStream().use { source ->
            source.copyTo(destination)
        }
    }
}

/**
 * Keeps failed inputs selected so retrying an export does not repeat successful items.
 * PDF exports are all-or-nothing, so the current selection is cleared only on success.
 */
internal fun remainingExportIdsAfterExport(
    format: ExportFormat,
    selectedIds: Set<String>,
    successfulImageIds: Set<String>,
    failedCount: Int
): Set<String> = when (format) {
    ExportFormat.IMAGES -> selectedIds - successfulImageIds
    ExportFormat.PDF -> if (failedCount == 0) emptySet() else selectedIds
}
