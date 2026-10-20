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
                check(item.file.isFile) { "الملف المؤقت للصورة غير موجود" }
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
                    ?: throw IllegalStateException("تعذر إنشاء ملف الصورة")
                val output = resolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException("تعذر فتح ملف الصورة للتصدير")
                item.file.inputStream().use { input ->
                    output.use { input.copyTo(it) }
                }
                val published = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                if (resolver.update(destination, published, null, null) <= 0) {
                    throw IllegalStateException("تعذر إتاحة الصورة بعد التصدير")
                }
                success++
                itemSucceeded = true
            } catch (error: Exception) {
                failed++
                lastError = error.message ?: error.javaClass.simpleName
                destination?.let { runCatching { resolver.delete(it, null, null) } }
                observer.onError("${item.displayName}: ${lastError}")
            } finally {
                observer.onItemFinished(item.id, itemSucceeded)
                observer.onProgress(index + 1, items.size, "تصدير الصورة ${index + 1} من ${items.size}")
            }
        }
        observer.onFinished(success, failed, lastError)
    }

    fun exportAsPdf(context: Context, items: List<ExportImage>, observer: Observer) {
        require(items.isNotEmpty()) { "اختار صورة واحدة على الأقل لإنشاء PDF" }
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
        try {
            destination = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw IllegalStateException("تعذر إنشاء ملف PDF")
            val pdf = PdfDocument()
            document = pdf
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            items.forEachIndexed { index, item ->
                check(item.file.isFile) { "الملف المؤقت غير موجود للصورة: ${item.displayName}" }
                val bitmap = item.file.inputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: throw IllegalStateException("تعذر قراءة الصورة: ${item.displayName}")
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
                observer.onProgress(index + 1, items.size, "إضافة الصفحة ${index + 1} من ${items.size} إلى PDF")
            }

            val output = resolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException("تعذر فتح ملف PDF للحفظ")
            output.use { pdf.writeTo(it) }
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(destination, published, null, null) <= 0) {
                throw IllegalStateException("تعذر إتاحة ملف PDF بعد الحفظ")
            }
            observer.onFinished(1, 0, null)
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            destination?.let { runCatching { resolver.delete(it, null, null) } }
            observer.onError("ملف PDF: $message")
            observer.onFinished(0, items.size, message)
        } finally {
            runCatching { document?.close() }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private companion object {
        const val PAGE_SHORT_SIDE = 595
        const val PAGE_LONG_SIDE = 842
        const val PAGE_MARGIN = 24
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
