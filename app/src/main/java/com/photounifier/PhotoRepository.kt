package com.photounifier

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns image processing and persistence; it has no Activity or View references. */
class PhotoRepository {
    interface Observer {
        fun onSource(index: Int, total: Int, bitmap: Bitmap)
        fun onClassification(index: Int, total: Int, kind: ImageKind)
        fun onEnhanced(bitmap: Bitmap)
        fun onSaved(index: Int, bitmap: Bitmap, file: File)
        fun onError(index: Int, total: Int, uri: Uri, message: String)
        fun onFinished(success: Int, failed: Int, error: String?)
    }

    fun process(context: Context, uris: List<Uri>, brightness: Int, contrastPercent: Int, observer: Observer, firstSourceOverride: Bitmap? = null, pageEdits: Map<Uri, DocumentPageEdit> = emptyMap()) {
        val batchStarted = SystemClock.elapsedRealtimeNanos()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var success = 0
        var failed = 0
        var lastError: String? = null
        val classifier = try { SceneClassifier() } catch (_: Exception) { null }
        try {
            uris.forEachIndexed { index, uri ->
                val imageStarted = SystemClock.elapsedRealtimeNanos()
                var loadMs = 0.0
                var classifyMs = 0.0
                var sourcePreviewMs = 0.0
                var enhanceMs = 0.0
                var enhancedPreviewMs = 0.0
                var adjustmentMs = 0.0
                var saveMs = 0.0
                var savedPreviewMs = 0.0
                var source: Bitmap? = null
                var enhanced: Bitmap? = null
                var output: Bitmap? = null
                var temporaryFile: File? = null
                try {
                    val loadStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.decode")
                    try {
                        val pageEdit = pageEdits[uri]
                        source = if (pageEdit == null && index == 0 && firstSourceOverride != null) {
                            firstSourceOverride.copy(Bitmap.Config.ARGB_8888, false)
                                ?: throw IllegalStateException("تعذر تجهيز الصورة المقصوصة")
                        } else {
                            Processor.load(context, uri)
                        }
                        if (pageEdit != null) {
                            var editedSource = source ?: throw IllegalStateException("تعذر تحميل الصورة")
                            val rotated = rotatePageBitmap(editedSource, pageEdit.rotationDegrees)
                            if (rotated !== editedSource) {
                                if (!editedSource.isRecycled) editedSource.recycle()
                                editedSource = rotated
                                source = rotated
                            }
                            pageEdit.normalizedCorners?.let { normalized ->
                                val corners = mapNormalizedCropPoints(normalized, editedSource.width, editedSource.height)
                                check(DocumentCrop.isValid(corners, editedSource.width, editedSource.height)) {
                                    "حدود القص المحفوظة للصفحة غير صالحة"
                                }
                                val cropped = DocumentCrop.warp(editedSource, corners)
                                if (cropped !== editedSource) editedSource.recycle()
                                editedSource = cropped
                                source = cropped
                            }
                        }
                    } finally {
                        Trace.endSection()
                        loadMs = elapsedMs(loadStarted)
                    }
                    val loadedSource = source ?: throw IllegalStateException("تعذر تحميل الصورة")

                    val sourcePreviewStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.preview.source")
                    try {
                        observer.onSource(index + 1, uris.size, Processor.createPreview(loadedSource))
                    } finally {
                        Trace.endSection()
                        sourcePreviewMs = elapsedMs(sourcePreviewStarted)
                    }

                    val classifyStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.classify")
                    val classification = try {
                        classifier?.classify(loadedSource)
                            ?: SceneClassification(ImageKind.GENERAL, 0f, emptyList())
                    } finally {
                        Trace.endSection()
                        classifyMs = elapsedMs(classifyStarted)
                    }
                    observer.onClassification(index + 1, uris.size, classification.kind)

                    // Enhancement is strictly pixel-based: no OCR, text reconstruction,
                    // character replacement, or generated lettering. Document-specific
                    // contrast and illumination correction live in Processor.
                    val enhanceStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.enhance")
                    val finalEnhanced = try {
                        Processor.enhance(loadedSource, classification.kind)
                    } finally {
                        Trace.endSection()
                        enhanceMs = elapsedMs(enhanceStarted)
                    }
                    enhanced = finalEnhanced

                    // UI callbacks receive bounded previews; full-resolution processing
                    // bitmaps stay local to this one image and are released in finally.
                    val enhancedPreviewStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.preview.enhanced")
                    try {
                        observer.onEnhanced(Processor.createPreview(finalEnhanced))
                    } finally {
                        Trace.endSection()
                        enhancedPreviewMs = elapsedMs(enhancedPreviewStarted)
                    }

                    val adjustmentStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.adjust")
                    try {
                        output = BitmapAdjustments.apply(finalEnhanced, brightness, contrastPercent / 100f)
                    } finally {
                        Trace.endSection()
                        adjustmentMs = elapsedMs(adjustmentStarted)
                    }

                    val saveStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.save")
                    try {
                        temporaryFile = Processor.saveTemporary(
                            context,
                            output ?: throw IllegalStateException("تعذر تجهيز الصورة للحفظ"),
                            "PU_%s_%03d.jpg".format(Locale.US, stamp, index + 1)
                        )
                    } finally {
                        Trace.endSection()
                        saveMs = elapsedMs(saveStarted)
                    }

                    val savedPreviewStarted = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("PU.preview.saved")
                    try {
                        observer.onSaved(
                            index + 1,
                            Processor.createPreview(output
                                ?: throw IllegalStateException("تعذر تجهيز معاينة الصورة المحفوظة")),
                            temporaryFile ?: throw IllegalStateException("ملف الصورة المؤقت غير متاح")
                        )
                        temporaryFile = null
                    } finally {
                        Trace.endSection()
                        savedPreviewMs = elapsedMs(savedPreviewStarted)
                    }
                    success++
                    Log.i(
                        PERF_TAG,
                        String.format(
                            Locale.US,
                            "image=%d/%d size=%dx%d load=%.1fms classify=%.1fms sourcePreview=%.1fms enhance=%.1fms enhancedPreview=%.1fms adjust=%.1fms save=%.1fms savedPreview=%.1fms total=%.1fms %s",
                            index + 1, uris.size, loadedSource.width, loadedSource.height,
                            loadMs, classifyMs, sourcePreviewMs, enhanceMs, enhancedPreviewMs,
                            adjustmentMs, saveMs, savedPreviewMs, elapsedMs(imageStarted), memorySnapshot()
                        )
                    )
                } catch (error: Exception) {
                    failed++
                    lastError = error.message ?: error.javaClass.simpleName
                    Log.w(
                        PERF_TAG,
                        "image=${index + 1}/${uris.size} failed after ${String.format(Locale.US, "%.1f", elapsedMs(imageStarted))}ms: ${lastError}; ${memorySnapshot()}",
                        error
                    )
                    observer.onError(index + 1, uris.size, uri, lastError ?: "خطأ غير معروف")
                } finally {
                    source?.let { if (!it.isRecycled) it.recycle() }
                    enhanced?.let { if (!it.isRecycled) it.recycle() }
                    output?.let { if (!it.isRecycled) it.recycle() }
                    temporaryFile?.let { runCatching { it.delete() } }
                }
            }
        } finally {
            classifier?.close()
            Log.i(
                PERF_TAG,
                "batch count=${uris.size} success=$success failed=$failed total=${String.format(Locale.US, "%.1f", elapsedMs(batchStarted))}ms ${memorySnapshot()}"
            )
            observer.onFinished(success, failed, lastError)
        }
    }

    private fun elapsedMs(startedNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0

    private fun memorySnapshot(): String {
        val runtime = Runtime.getRuntime()
        val javaUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return "javaHeapMB=$javaUsedMb nativeHeapMB=$nativeHeapMb"
    }

    private companion object {
        const val PERF_TAG = "PhotoUnifierPerf"
    }
}
