package com.photounifier

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

data class SelectionThumbnail(val uri: Uri, val bitmap: Bitmap)

data class PhotoUiState(
    val selected: List<Uri> = emptyList(),
    val selectionThumbnails: List<SelectionThumbnail> = emptyList(),
    val previewIndex: Int = 0,
    val busy: Boolean = false,
    val progress: Int = 0,
    val progressMax: Int = 1,
    val savedCount: Int = 0,
    val status: String = "جاهز لاختيار الصور",
    val errors: List<String> = emptyList(),
    val sourcePreview: Bitmap? = null,
    val enhancedPreview: Bitmap? = null,
    val resultPreview: Bitmap? = null,
    val brightness: Int = 0,
    val contrastPercent: Int = 100,
    val cropSource: Bitmap? = null,
    val cropPreviewSource: Bitmap? = null,
    val cropPreview: Bitmap? = null,
    val cropApplying: Boolean = false,
    val cropCorners: List<CropPoint> = emptyList(),
    val cropUndoStack: List<List<CropPoint>> = emptyList(),
    val cropOpen: Boolean = false,
    val cropApplied: Boolean = false,
    val cropEdits: Map<Uri, DocumentPageEdit> = emptyMap(),
    val cropPageUri: Uri? = null,
    val cropRotationDegrees: Int = 0
)

/** Owns screen state and coordinates background work; the Activity only renders state. */
class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository()
    private val executor = Executors.newSingleThreadExecutor()
    private val previewExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private val imagePreviewExecutor = Executors.newSingleThreadExecutor()
    private val previewGeneration = AtomicInteger(0)
    private val imagePreviewGeneration = AtomicInteger(0)
    private val cropGeneration = AtomicInteger(0)
    private val selectionGeneration = AtomicInteger(0)
    private val mutableState = MutableLiveData(PhotoUiState())
    val state: LiveData<PhotoUiState> = mutableState
    @Volatile private var currentState = PhotoUiState()

    @Synchronized
    private fun update(transform: (PhotoUiState) -> PhotoUiState) {
        currentState = transform(currentState)
        mutableState.postValue(currentState)
    }

    fun selectImages(uris: List<Uri>) {
        val normalizedUris = uris.distinct()
        previewGeneration.incrementAndGet()
        imagePreviewGeneration.incrementAndGet()
        cropGeneration.incrementAndGet()
        val thumbnailToken = selectionGeneration.incrementAndGet()
        update { it.copy(
            selected = normalizedUris,
            selectionThumbnails = emptyList(),
            previewIndex = 0,
            progress = 0,
            savedCount = 0,
            errors = emptyList(),
            status = if (normalizedUris.isEmpty()) "لم يتم اختيار صور" else "تم اختيار ${normalizedUris.size} صورة",
            sourcePreview = null,
            enhancedPreview = null,
            resultPreview = null,
            cropSource = null,
            cropPreviewSource = null,
            cropPreview = null,
            cropApplying = false,
            cropCorners = emptyList(),
            cropUndoStack = emptyList(),
            cropOpen = false,
            cropApplied = false,
            cropEdits = emptyMap(),
            cropPageUri = null,
            cropRotationDegrees = 0
        ) }
        if (normalizedUris.isEmpty()) return

        val context = getApplication<Application>().applicationContext
        showPreview(0)

        // Keep the gallery interaction light: decode at most six small thumbnails,
        // never full-resolution images for the entire selected batch.
        thumbnailExecutor.execute {
            val thumbnails = mutableListOf<SelectionThumbnail>()
            try {
                for (uri in normalizedUris.take(MAX_SELECTION_THUMBNAILS)) {
                    if (selectionGeneration.get() != thumbnailToken) {
                        thumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                        return@execute
                    }
                    try {
                        thumbnails += SelectionThumbnail(
                            uri,
                            Processor.loadPreview(context, uri, SELECTION_THUMBNAIL_SIDE)
                        )
                    } catch (_: Exception) {
                        // A single corrupt or unavailable URI should not hide other thumbnails.
                    }
                }
                if (selectionGeneration.get() != thumbnailToken) {
                    thumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                    return@execute
                }
                update { old ->
                    if (selectionGeneration.get() != thumbnailToken || old.selected != normalizedUris) {
                        thumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                        old
                    } else {
                        old.copy(selectionThumbnails = thumbnails.toList())
                    }
                }
            } catch (_: Exception) {
                thumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            }
        }
    }


    /**
     * Loads the active image at preview resolution. A generation token prevents slow
     * decodes from older selections from overwriting the image currently being reviewed.
     */
    fun showPreview(index: Int) {
        val snapshot = currentState
        if (snapshot.busy || snapshot.cropApplying) return
        val uri = snapshot.selected.getOrNull(index) ?: return
        val existingPreview = snapshot.sourcePreview
        if (snapshot.previewIndex == index && existingPreview != null && !existingPreview.isRecycled) return

        val generation = imagePreviewGeneration.incrementAndGet()
        previewGeneration.incrementAndGet()
        update { old ->
            if (old.selected.getOrNull(index) != uri) old
            else old.copy(
                previewIndex = index,
                sourcePreview = null,
                enhancedPreview = null,
                resultPreview = null,
                status = "جاري تحميل معاينة الصورة ${index + 1} من ${old.selected.size}..."
            )
        }

        imagePreviewExecutor.execute {
            if (generation != imagePreviewGeneration.get()) return@execute
            try {
                var preview = Processor.loadPreview(
                    getApplication<Application>().applicationContext,
                    uri
                )
                val savedEdit = snapshot.cropEdits[uri]
                if (savedEdit != null) {
                    val rotated = rotatePageBitmap(preview, savedEdit.rotationDegrees)
                    if (rotated !== preview) {
                        preview.recycle()
                        preview = rotated
                    }
                    savedEdit.normalizedCorners?.let { normalized ->
                        val cropped = DocumentCrop.warp(
                            preview,
                            mapNormalizedCropPoints(normalized, preview.width, preview.height),
                            Processor.PREVIEW_MAX_SIDE
                        )
                        if (cropped !== preview) preview.recycle()
                        preview = cropped
                    }
                }

                update { old ->
                    if (generation != imagePreviewGeneration.get() ||
                        old.previewIndex != index ||
                        old.selected.getOrNull(index) != uri
                    ) {
                        if (!preview.isRecycled) preview.recycle()
                        old
                    } else {
                        old.copy(
                            sourcePreview = preview,
                            enhancedPreview = null,
                            resultPreview = null,
                            status = "معاينة الصورة ${index + 1} من ${old.selected.size}"
                        )
                    }
                }
                if (generation == imagePreviewGeneration.get() &&
                    currentState.sourcePreview === preview
                ) {
                    refreshPreview()
                }
            } catch (error: Exception) {
                update { old ->
                    if (generation != imagePreviewGeneration.get() || old.previewIndex != index) old
                    else old.copy(
                        sourcePreview = null,
                        enhancedPreview = null,
                        resultPreview = null,
                        status = "تعذر تحميل معاينة الصورة ${index + 1}: ${error.message ?: "خطأ"}"
                    )
                }
            }
        }
    }

    fun openCropEditor(openCvReady: Boolean) {
        val snapshot = currentState
        val uri = snapshot.selected.firstOrNull() ?: return
        if (!openCvReady) {
            update { it.copy(status = "مكتبة OpenCV مش جاهزة") }
            return
        }
        if (snapshot.cropApplying) return
        val existingSource = snapshot.cropSource?.takeIf { !it.isRecycled }
        val generation = cropGeneration.incrementAndGet()
        update { it.copy(cropApplying = true, status = "جاري تجهيز الصورة للقص...") }
        executor.execute {
            var newlyLoadedSource: Bitmap? = null
            try {
                val source = existingSource ?: Processor.load(
                    getApplication<Application>().applicationContext,
                    uri
                ).also { newlyLoadedSource = it }
                if (generation != cropGeneration.get() || source.isRecycled) {
                    newlyLoadedSource?.let { if (!it.isRecycled) it.recycle() }
                    return@execute
                }
                val detected = try { DocumentCrop.detect(source, getApplication<Application>().applicationContext) } catch (_: Exception) { null }
                if (generation != cropGeneration.get() || source.isRecycled) {
                    newlyLoadedSource?.let { if (!it.isRecycled) it.recycle() }
                    return@execute
                }
                val corners = detected ?: DocumentCrop.fullFrame(source.width, source.height)
                val previewSource = createCropPreviewSource(source)
                val preview = try {
                    DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, corners), Processor.PREVIEW_MAX_SIDE)
                } catch (error: Exception) {
                    if (previewSource !== source && !previewSource.isRecycled) previewSource.recycle()
                    newlyLoadedSource?.let { if (!it.isRecycled) it.recycle() }
                    throw error
                }
                if (generation != cropGeneration.get()) {
                    preview.recycle()
                    if (previewSource !== source && !previewSource.isRecycled) previewSource.recycle()
                    newlyLoadedSource?.let { if (!it.isRecycled) it.recycle() }
                    return@execute
                }
                update { old ->
                    if (generation != cropGeneration.get() || old.selected.firstOrNull() != uri) {
                        if (!preview.isRecycled) preview.recycle()
                        if (previewSource !== source && !previewSource.isRecycled) previewSource.recycle()
                        newlyLoadedSource?.let { if (!it.isRecycled) it.recycle() }
                        old
                    } else {
                        old.copy(
                            cropSource = source,
                            cropPreviewSource = previewSource,
                            cropPreview = preview,
                            cropCorners = corners,
                            cropUndoStack = emptyList(),
                            cropOpen = true,
                            cropApplying = false,
                            cropApplied = false,
                                            status = if (detected == null)
                                "مفيش ورقة واضحة؛ ظبط الأربع زوايا يدويًا."
                            else
                                "تم اكتشاف حدود الورقة. اسحب النقاط للتعديل."
                        )
                    }
                }
            } catch (error: Exception) {
                newlyLoadedSource?.let { if (!it.isRecycled) it.recycle() }
                update { old ->
                    if (generation != cropGeneration.get() || old.selected.firstOrNull() != uri) old
                    else old.copy(
                        cropApplying = false,
                        status = "تعذر تجهيز القص: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun moveCropCorner(index: Int, x: Float, y: Float) {
        val snapshot = currentState
        val source = snapshot.cropSource ?: return
        if (!snapshot.cropOpen || index !in 0..3 || snapshot.cropCorners.size != 4) return
        val candidate = snapshot.cropCorners.toMutableList()
        candidate[index] = CropPoint(x.coerceIn(0f, (source.width - 1).toFloat()), y.coerceIn(0f, (source.height - 1).toFloat()))
        if (!DocumentCrop.isValid(candidate, source.width, source.height)) return
        val generation = cropGeneration.incrementAndGet()
        update { it.copy(cropCorners = candidate, cropUndoStack = it.cropUndoStack + listOf(it.cropCorners)) }
        executor.execute {
            if (generation != cropGeneration.get() || source.isRecycled) return@execute
            val previewSource = currentState.cropPreviewSource?.takeIf { !it.isRecycled } ?: source
            val preview = try { DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, candidate), Processor.PREVIEW_MAX_SIDE) } catch (_: Exception) { return@execute }
            if (generation != cropGeneration.get()) { preview.recycle(); return@execute }
            update { old ->
                if (!old.cropOpen || old.cropCorners != candidate) { preview.recycle(); old }
                else old.copy(cropPreview = preview)
            }
        }
    }

    fun undoCrop() {
        val snapshot = currentState
        val previous = snapshot.cropUndoStack.lastOrNull() ?: return
        val source = snapshot.cropSource ?: return
        val generation = cropGeneration.incrementAndGet()
        update { it.copy(cropCorners = previous, cropUndoStack = it.cropUndoStack.dropLast(1)) }
        executor.execute {
            if (generation != cropGeneration.get() || source.isRecycled) return@execute
            val preview = try {
                val previewSource = currentState.cropPreviewSource?.takeIf { !it.isRecycled } ?: source
                DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, previous), Processor.PREVIEW_MAX_SIDE)
            } catch (_: Exception) { return@execute }
            if (generation != cropGeneration.get()) { preview.recycle(); return@execute }
            update { old ->
                if (old.cropCorners != previous || old.cropSource !== source) {
                    preview.recycle()
                    old
                } else old.copy(cropPreview = preview)
            }
        }
    }

    fun resetCrop() {
        val source = currentState.cropSource ?: return
        val corners = DocumentCrop.fullFrame(source.width, source.height)
        val generation = cropGeneration.incrementAndGet()
        update { it.copy(cropCorners = corners, cropUndoStack = it.cropUndoStack + listOf(it.cropCorners)) }
        executor.execute {
            if (generation != cropGeneration.get() || source.isRecycled) return@execute
            val preview = try {
                val previewSource = currentState.cropPreviewSource?.takeIf { !it.isRecycled } ?: source
                DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, corners), Processor.PREVIEW_MAX_SIDE)
            } catch (_: Exception) { return@execute }
            if (generation != cropGeneration.get()) { preview.recycle(); return@execute }
            update { old ->
                if (!old.cropOpen || old.cropCorners != corners || old.cropSource !== source) {
                    preview.recycle()
                    old
                } else old.copy(cropPreview = preview)
            }
        }
    }

    fun cancelCrop() {
        cropGeneration.incrementAndGet()
        imagePreviewGeneration.incrementAndGet()
        val hasSelection = currentState.selected.isNotEmpty()
        update {
            it.copy(
                cropSource = null,
                cropPreviewSource = null,
                cropPreview = null,
                    cropApplying = false,
                cropCorners = emptyList(),
                cropUndoStack = emptyList(),
                cropOpen = false,
                cropApplied = false,
                previewIndex = 0,
                sourcePreview = null,
                enhancedPreview = null,
                resultPreview = null
            )
        }
        if (hasSelection) showPreview(0)
    }

    fun applyCrop() {
        val snapshot = currentState
        val source = snapshot.cropSource ?: return
        val uri = snapshot.selected.firstOrNull() ?: return
        val corners = snapshot.cropCorners
        if (!snapshot.cropOpen || snapshot.cropApplying ||
            !DocumentCrop.isValid(corners, source.width, source.height)
        ) return

        val generation = cropGeneration.incrementAndGet()
        update {
            it.copy(
                cropOpen = false,
                cropApplying = true,
                status = "جاري تطبيق القص بالجودة الكاملة..."
            )
        }
        executor.execute {
            if (generation != cropGeneration.get() || source.isRecycled) return@execute
            val fullResolutionCrop = try {
                DocumentCrop.warp(source, corners)
            } catch (error: Exception) {
                update { old ->
                    if (generation != cropGeneration.get()) old
                    else old.copy(
                        cropApplying = false,
                        status = "تعذر تطبيق القص: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
                return@execute
            }
            if (generation != cropGeneration.get()) {
                fullResolutionCrop.recycle()
                return@execute
            }
            val preview = try {
                Processor.createPreview(fullResolutionCrop)
            } catch (error: Exception) {
                fullResolutionCrop.recycle()
                update { old ->
                    if (generation != cropGeneration.get()) old
                    else old.copy(
                        cropApplying = false,
                        status = "تعذر تجهيز معاينة القص: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
                return@execute
            }
            update { old ->
                if (generation != cropGeneration.get() || old.selected.firstOrNull() != uri) {
                    if (!preview.isRecycled) preview.recycle()
                    if (!fullResolutionCrop.isRecycled) fullResolutionCrop.recycle()
                    old
                } else {
                    old.copy(
                        sourcePreview = preview,
                        previewIndex = 0,
                        enhancedPreview = null,
                        resultPreview = null,
                        cropSource = null,
                        cropPreviewSource = null,
                        cropPreview = null,
                        cropAppliedSource = fullResolutionCrop,
                        cropCorners = emptyList(),
                        cropUndoStack = emptyList(),
                        cropOpen = false,
                        cropApplying = false,
                        cropApplied = true,
                        status = "تم تطبيق القص على الصورة الأولى بجودتها الكاملة؛ ابدأ التحسين."
                    )
                }
            }
            if (currentState.cropAppliedSource === fullResolutionCrop) refreshPreview()
        }
    }

    fun setBrightness(value: Int) {
        update { it.copy(brightness = value.coerceIn(-50, 50)) }
        refreshPreview()
    }

    fun setContrast(value: Int) {
        update { it.copy(contrastPercent = value.coerceIn(50, 200)) }
        refreshPreview()
    }

    private fun refreshPreview() {
        val snapshot = currentState
        // Before processing, use the loaded (or cropped) source as the preview base.
        // After processing starts producing enhanced frames, prefer those instead.
        val base = snapshot.enhancedPreview ?: snapshot.sourcePreview ?: return
        val generation = previewGeneration.incrementAndGet()
        previewExecutor.execute {
            // Slider changes can queue faster than a full-resolution bitmap can be drawn.
            // Skip obsolete work before allocating or touching the bitmap.
            if (generation != previewGeneration.get() || base.isRecycled) return@execute
            val preview = try {
                BitmapAdjustments.apply(base, snapshot.brightness, snapshot.contrastPercent / 100f)
            } catch (_: Exception) { return@execute }
            if (generation != previewGeneration.get()) {
                preview.recycle()
                return@execute
            }
            update { old ->
                if ((old.enhancedPreview !== base && old.sourcePreview !== base) ||
                    generation != previewGeneration.get()) {
                    preview.recycle()
                    old
                } else old.copy(resultPreview = preview)
            }
        }
    }

    fun process(openCvReady: Boolean) {
        val snapshot = currentState
        if (snapshot.busy || snapshot.selected.isEmpty()) return
        if (!openCvReady) {
            update { it.copy(status = "مكتبة معالجة الصور غير جاهزة") }
            return
        }
        imagePreviewGeneration.incrementAndGet()
        previewGeneration.incrementAndGet()
        val uris = snapshot.selected
        val brightness = snapshot.brightness
        val contrast = snapshot.contrastPercent
        update { it.copy(busy = true, progress = 0, savedCount = 0, progressMax = uris.size, errors = emptyList(), status = "بدء معالجة الصور…") }

        executor.execute {
            repository.process(
                getApplication<Application>().applicationContext, uris, brightness, contrast,
                object : PhotoRepository.Observer {
                    override fun onSource(index: Int, total: Int, bitmap: Bitmap) {
                        update { it.copy(
                            status = "تحليل الصورة $index من $total",
                            previewIndex = index - 1,
                            sourcePreview = bitmap,
                            enhancedPreview = null,
                            resultPreview = null
                        ) }
                    }
                    override fun onClassification(index: Int, total: Int, kind: ImageKind) {
                        update { it.copy(status = "معالجة الصورة $index من $total — ${kind.title}") }
                    }
                    override fun onEnhanced(bitmap: Bitmap) {
                        update { it.copy(enhancedPreview = bitmap) }
                        refreshPreview()
                    }
                    override fun onSaved(index: Int, bitmap: Bitmap) {
                        update { it.copy(progress = index, savedCount = it.savedCount + 1, resultPreview = bitmap, status = "تم حفظ الصورة ${it.savedCount + 1} من ${uris.size}") }
                        // Reapply the latest manual settings after the saved-result event.
                        refreshPreview()
                    }
                    override fun onError(index: Int, total: Int, uri: Uri, message: String) {
                        val item = "الصورة $index من $total (${uri.lastPathSegment ?: "ملف الصورة"}): $message"
                        update { old -> old.copy(progress = maxOf(old.progress, index), errors = old.errors + item, status = "فشل معالجة الصورة $index من $total: $message") }
                    }
                    override fun onFinished(success: Int, failed: Int, error: String?) {
                        val detail = error?.let { "\nآخر خطأ: $it" }.orEmpty()
                        update {
                            it.copy(
                                busy = false,
                                progress = it.progressMax,
                                status = "اكتملت المعالجة: نجاح $success، فشل $failed$detail"
                            )
                        }
                    }
                },
                firstSourceOverride = if (snapshot.cropApplied) snapshot.cropAppliedSource else null
            )
        }
    }

    override fun onCleared() {
        imagePreviewGeneration.incrementAndGet()
        previewGeneration.incrementAndGet()
        executor.shutdownNow()
        previewExecutor.shutdownNow()
        thumbnailExecutor.shutdownNow()
        imagePreviewExecutor.shutdownNow()
        currentState.sourcePreview?.let { if (!it.isRecycled) it.recycle() }
        currentState.enhancedPreview?.let { if (!it.isRecycled) it.recycle() }
        currentState.resultPreview?.let { if (!it.isRecycled) it.recycle() }
        currentState.cropSource?.let { if (!it.isRecycled) it.recycle() }
        currentState.cropPreviewSource?.let { bitmap ->
            if (bitmap !== currentState.cropSource && !bitmap.isRecycled) bitmap.recycle()
        }
        currentState.cropPreview?.let { if (!it.isRecycled) it.recycle() }
        currentState.cropAppliedSource?.let { if (!it.isRecycled) it.recycle() }
        currentState.selectionThumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        super.onCleared()
    }
    private fun createCropPreviewSource(source: Bitmap): Bitmap {
        val maxSide = Processor.PREVIEW_MAX_SIDE
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(source.width, source.height).toDouble())
        val width = (source.width * scale).roundToInt().coerceAtLeast(2)
        val height = (source.height * scale).roundToInt().coerceAtLeast(2)
        return if (width == source.width && height == source.height) source
        else Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun mapCropPoints(source: Bitmap, target: Bitmap, points: List<CropPoint>): List<CropPoint> {
        if (source === target) return points
        val scaleX = (target.width - 1).toFloat() / (source.width - 1).coerceAtLeast(1)
        val scaleY = (target.height - 1).toFloat() / (source.height - 1).coerceAtLeast(1)
        return points.map { CropPoint(it.x * scaleX, it.y * scaleY) }
    }

    private companion object {
        const val MAX_SELECTION_THUMBNAILS = 6
        const val SELECTION_THUMBNAIL_SIDE = 256
    }
}
