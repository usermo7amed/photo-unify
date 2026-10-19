package com.photounifier

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

data class SelectionThumbnail(val uri: Uri, val bitmap: Bitmap)

data class ExportImage(
    val id: String,
    val sourceUri: Uri,
    val file: File,
    val displayName: String
)

enum class ExportFormat { IMAGES, PDF }

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
    val processedImages: List<ExportImage> = emptyList(),
    val selectedExportIds: Set<String> = emptySet(),
    val exportBusy: Boolean = false,
    val exportProgress: Int = 0,
    val exportProgressMax: Int = 1,
    val exportStatus: String = "جاهز للتصدير",
    val exportErrors: List<String> = emptyList(),
    val exportedCount: Int = 0,
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
    val cropRotationDegrees: Int = 0,
    val cropDirty: Boolean = false
)

/** Owns screen state and coordinates background work; the Activity only renders state. */
class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository()
    private val exportRepository = ExportRepository()
    private val executor = Executors.newSingleThreadExecutor()
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private val previewExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private val imagePreviewExecutor = Executors.newSingleThreadExecutor()
    private val previewGeneration = AtomicInteger(0)
    private val imagePreviewGeneration = AtomicInteger(0)
    private val cropGeneration = AtomicInteger(0)
    private val selectionGeneration = AtomicInteger(0)
    private val thumbnailRequests = mutableSetOf<Uri>()
    private val mutableState = MutableLiveData(PhotoUiState())
    val state: LiveData<PhotoUiState> = mutableState
    @Volatile private var currentState = PhotoUiState()

    @Synchronized
    private fun update(transform: (PhotoUiState) -> PhotoUiState) {
        currentState = transform(currentState)
        mutableState.postValue(currentState)
    }

    fun selectImages(uris: List<Uri>) {
        if (currentState.busy || currentState.exportBusy) return
        currentState.processedImages.forEach { runCatching { it.file.delete() } }
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
            processedImages = emptyList(),
            selectedExportIds = emptySet(),
            exportBusy = false,
            exportProgress = 0,
            exportProgressMax = 1,
            exportStatus = "جاهز للتصدير",
            exportErrors = emptyList(),
            exportedCount = 0,
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
            cropRotationDegrees = 0,
            cropDirty = false
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
                    generateEnhancedPreview(preview, generation)
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

    /**
     * Builds a bounded, on-device enhanced preview before the user starts saving.
     * The full-resolution pipeline still runs independently when the user processes
     * the batch; this result is for interactive review only.
     */
    private fun generateEnhancedPreview(source: Bitmap, generation: Int) {
        previewExecutor.execute {
            if (generation != imagePreviewGeneration.get() || source.isRecycled) return@execute

            var processed: Bitmap? = null
            var failureMessage: String? = null
            val classifier = try {
                SceneClassifier()
            } catch (_: Exception) {
                null
            }

            try {
                val kind = classifier?.classify(source)?.kind ?: ImageKind.GENERAL
                if (generation != imagePreviewGeneration.get() || source.isRecycled) return@execute
                processed = Processor.enhance(source, kind)
            } catch (_: Exception) {
                failureMessage = "تعذر تجهيز معاينة التحسين. تقدر تراجع الأصل وتتابع لو مكتبة OpenCV متاحة."
            } finally {
                runCatching { classifier?.close() }
            }

            val enhanced = processed
            if (enhanced == null) {
                failureMessage?.let { message ->
                    update { old ->
                        if (generation != imagePreviewGeneration.get() || old.sourcePreview !== source) old
                        else old.copy(status = message)
                    }
                }
                return@execute
            }

            var installed = false
            var replacedResult: Bitmap? = null
            update { old ->
                if (generation != imagePreviewGeneration.get() ||
                    old.sourcePreview !== source ||
                    source.isRecycled
                ) {
                    old
                } else {
                    installed = true
                    replacedResult = old.resultPreview
                    old.copy(
                        enhancedPreview = enhanced,
                        resultPreview = null,
                        status = "معاينة التحسين جاهزة للصورة ${old.previewIndex + 1} من ${old.selected.size}"
                    )
                }
            }

            if (installed) {
                releaseBitmaps(listOf(replacedResult), setOf(source, enhanced))
                refreshPreview()
            } else {
                releaseBitmaps(listOf(enhanced))
            }
        }
    }

    @Synchronized
    fun ensurePageThumbnail(index: Int) {
        val snapshot = currentState
        val uri = snapshot.selected.getOrNull(index) ?: return
        if (index < MAX_SELECTION_THUMBNAILS) return
        if (snapshot.selectionThumbnails.any { it.uri == uri && !it.bitmap.isRecycled }) return
        if (!thumbnailRequests.add(uri)) return

        val generation = selectionGeneration.get()
        thumbnailExecutor.execute {
            var loadedThumbnail: Bitmap? = null
            var accepted = false
            var evicted = emptyList<SelectionThumbnail>()
            try {
                if (selectionGeneration.get() != generation) return@execute
                val context = getApplication<Application>().applicationContext
                val thumbnail = Processor.loadPreview(context, uri, PAGE_THUMBNAIL_SIDE)
                loadedThumbnail = thumbnail
                if (selectionGeneration.get() != generation) return@execute

                update { old ->
                    if (generation != selectionGeneration.get() ||
                        old.selected.getOrNull(index) != uri ||
                        old.selectionThumbnails.any { it.uri == uri && !it.bitmap.isRecycled }
                    ) {
                        old
                    } else {
                        val initialUris = old.selected.take(MAX_SELECTION_THUMBNAILS).toSet()
                        val dynamic = old.selectionThumbnails.filter {
                            it.uri !in initialUris && it.uri != uri && !it.bitmap.isRecycled
                        } + SelectionThumbnail(uri, thumbnail)
                        val retainedDynamic = dynamic.takeLast(MAX_DYNAMIC_PAGE_THUMBNAILS)
                        val retainedUris = initialUris + retainedDynamic.map { it.uri }
                        evicted = old.selectionThumbnails.filter { it.uri !in retainedUris }
                        accepted = true
                        old.copy(
                            selectionThumbnails = old.selectionThumbnails.filter {
                                it.uri in initialUris && !it.bitmap.isRecycled
                            } + retainedDynamic
                        )
                    }
                }

                if (accepted) {
                    releaseBitmaps(evicted.map { it.bitmap })
                    loadedThumbnail = null
                }
            } catch (_: Exception) {
                // Keep the numbered placeholder when a source URI cannot be decoded.
            } finally {
                loadedThumbnail?.let { if (!it.isRecycled) it.recycle() }
                synchronized(this@PhotoViewModel) { thumbnailRequests.remove(uri) }
            }
        }
    }

    fun openCropEditor(openCvReady: Boolean) {
        val snapshot = currentState
        val index = snapshot.previewIndex
        val uri = snapshot.selected.getOrNull(index) ?: return
        if (!openCvReady) {
            update { it.copy(status = "مكتبة OpenCV مش جاهزة") }
            return
        }
        if (snapshot.cropApplying) return

        val savedEdit = snapshot.cropEdits[uri]
        val generation = cropGeneration.incrementAndGet()
        update { old ->
            if (old.selected.getOrNull(index) != uri) old
            else old.copy(
                cropSource = null,
                cropPreviewSource = null,
                cropPreview = null,
                cropCorners = emptyList(),
                cropUndoStack = emptyList(),
                cropDirty = false,
                cropApplying = true,
                cropOpen = true,
                status = "جاري تجهيز الصفحة للقص..."
            )
        }

        executor.execute {
            releaseBitmaps(listOf(snapshot.cropSource, snapshot.cropPreviewSource, snapshot.cropPreview))
            var source: Bitmap? = null
            var previewSource: Bitmap? = null
            var preview: Bitmap? = null
            try {
                val context = getApplication<Application>().applicationContext
                val decoded = Processor.load(context, uri)
                val rotated = rotatePageBitmap(decoded, savedEdit?.rotationDegrees ?: 0)
                if (rotated !== decoded && !decoded.isRecycled) decoded.recycle()
                source = rotated

                if (snapshot.selectionThumbnails.none { it.uri == uri }) {
                    val scale = minOf(1.0, 112.0 / maxOf(rotated.width, rotated.height).toDouble())
                    val width = (rotated.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (rotated.height * scale).roundToInt().coerceAtLeast(1)
                    val pageThumbnail = Bitmap.createScaledBitmap(rotated, width, height, true)
                    var accepted = false
                    var evicted = emptyList<SelectionThumbnail>()
                    update { old ->
                        if (old.selected.getOrNull(index) != uri || old.selectionThumbnails.any { it.uri == uri }) {
                            old
                        } else {
                            val firstUris = old.selected.take(MAX_SELECTION_THUMBNAILS).toSet()
                            val dynamic = old.selectionThumbnails.filter { it.uri !in firstUris } +
                                SelectionThumbnail(uri, pageThumbnail)
                            val keptDynamic = dynamic.takeLast(MAX_DYNAMIC_PAGE_THUMBNAILS)
                            val keepUris = firstUris + keptDynamic.map { it.uri }
                            evicted = old.selectionThumbnails.filter { it.uri !in keepUris }
                            accepted = true
                            old.copy(
                                selectionThumbnails = old.selectionThumbnails.filter { it.uri in firstUris } + keptDynamic
                            )
                        }
                    }
                    if (accepted) {
                        releaseBitmaps(evicted.map { it.bitmap }, setOf(pageThumbnail))
                    } else if (!pageThumbnail.isRecycled) {
                        pageThumbnail.recycle()
                    }
                }

                if (generation != cropGeneration.get() || rotated.isRecycled) {
                    releaseBitmaps(listOf(source, previewSource, preview))
                    return@execute
                }

                val detectedCorners = if (savedEdit?.normalizedCorners == null) {
                    DocumentCrop.detect(rotated, context)
                } else {
                    null
                }
                val corners = savedEdit?.normalizedCorners?.let {
                    mapNormalizedCropPoints(it, rotated.width, rotated.height)
                } ?: detectedCorners ?: DocumentCrop.fullFrame(rotated.width, rotated.height)

                previewSource = createCropPreviewSource(rotated)
                preview = DocumentCrop.warp(
                    previewSource!!,
                    mapCropPoints(rotated, previewSource!!, corners),
                    Processor.PREVIEW_MAX_SIDE
                )

                if (generation != cropGeneration.get()) {
                    releaseBitmaps(listOf(source, previewSource, preview))
                    return@execute
                }

                var installed = false
                update { old ->
                    if (generation != cropGeneration.get() ||
                        old.selected.getOrNull(index) != uri ||
                        old.previewIndex != index
                    ) {
                        old
                    } else {
                        installed = true
                        old.copy(
                            cropSource = rotated,
                            cropPreviewSource = previewSource,
                            cropPreview = preview,
                            cropCorners = corners,
                            cropUndoStack = emptyList(),
                            cropPageUri = uri,
                            cropRotationDegrees = savedEdit?.rotationDegrees ?: 0,
                            cropDirty = false,
                            cropOpen = true,
                            cropApplying = false,
                            cropApplied = old.cropEdits.isNotEmpty(),
                            status = when {
                                savedEdit != null -> "تم تحميل تعديلات الصفحة ${index + 1}. تقدر تراجعها أو تدوّر الصفحة."
                                detectedCorners == null -> "مفيش ورقة واضحة؛ ظبط الأربع زوايا يدويًا."
                                else -> "تم اكتشاف حدود الورقة. اسحب النقاط للتعديل."
                            }
                        )
                    }
                }
                if (installed) {
                    releaseBitmaps(
                        listOf(snapshot.cropSource, snapshot.cropPreviewSource, snapshot.cropPreview),
                        setOfNotNull(rotated, previewSource, preview)
                    )
                } else {
                    releaseBitmaps(listOf(source, previewSource, preview))
                }
            } catch (error: Exception) {
                releaseBitmaps(listOf(source, previewSource, preview))
                update { old ->
                    if (generation != cropGeneration.get() || old.selected.getOrNull(index) != uri) old
                    else old.copy(
                        cropApplying = false,
                        status = "تعذر تجهيز القص: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun rotateCropPage() {
        val snapshot = currentState
        val source = snapshot.cropSource ?: return
        val uri = snapshot.cropPageUri ?: return
        if (!snapshot.cropOpen || snapshot.cropApplying || snapshot.busy || source.isRecycled) return

        val generation = cropGeneration.incrementAndGet()
        val nextRotation = (snapshot.cropRotationDegrees + 90) % 360
        update { it.copy(cropApplying = true, status = "جاري تدوير الصفحة...") }

        executor.execute {
            var rotated: Bitmap? = null
            var previewSource: Bitmap? = null
            var preview: Bitmap? = null
            try {
                val rotatedBitmap = rotatePageBitmap(source, 90)
                rotated = rotatedBitmap
                val context = getApplication<Application>().applicationContext
                val corners = DocumentCrop.detect(rotatedBitmap, context)
                    ?: DocumentCrop.fullFrame(rotatedBitmap.width, rotatedBitmap.height)
                previewSource = createCropPreviewSource(rotatedBitmap)
                preview = DocumentCrop.warp(
                    previewSource!!,
                    mapCropPoints(rotatedBitmap, previewSource!!, corners),
                    Processor.PREVIEW_MAX_SIDE
                )

                if (generation != cropGeneration.get()) {
                    releaseBitmaps(listOf(rotated, previewSource, preview), setOf(source))
                    return@execute
                }

                var installed = false
                update { old ->
                    if (!old.cropOpen || old.cropPageUri != uri || old.cropSource !== source) old
                    else {
                        installed = true
                        old.copy(
                            cropSource = rotated,
                            cropPreviewSource = previewSource,
                            cropPreview = preview,
                            cropCorners = corners,
                            cropUndoStack = emptyList(),
                            cropRotationDegrees = nextRotation,
                            cropDirty = true,
                            cropApplying = false,
                            status = "اتدورت الصفحة 90°. راجع الزوايا قبل الحفظ."
                        )
                    }
                }
                if (installed) {
                    releaseBitmaps(
                        listOf(snapshot.cropSource, snapshot.cropPreviewSource, snapshot.cropPreview),
                        setOfNotNull(rotated, previewSource, preview)
                    )
                } else {
                    releaseBitmaps(listOf(rotated, previewSource, preview), setOf(source))
                }
            } catch (error: Exception) {
                releaseBitmaps(listOf(rotated, previewSource, preview), setOf(source))
                update { old ->
                    if (generation != cropGeneration.get()) old
                    else old.copy(
                        cropApplying = false,
                        status = "تعذر تدوير الصفحة: ${error.message ?: error.javaClass.simpleName}"
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
        update { it.copy(cropCorners = candidate, cropUndoStack = it.cropUndoStack + listOf(it.cropCorners), cropDirty = true) }
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
        update { it.copy(cropCorners = previous, cropUndoStack = it.cropUndoStack.dropLast(1), cropDirty = true) }
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
        update { it.copy(cropCorners = corners, cropUndoStack = it.cropUndoStack + listOf(it.cropCorners), cropDirty = true) }
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
        val snapshot = currentState
        val index = snapshot.previewIndex.coerceIn(0, (snapshot.selected.size - 1).coerceAtLeast(0))
        update {
            it.copy(
                cropSource = null,
                cropPreviewSource = null,
                cropPreview = null,
                cropApplying = false,
                cropCorners = emptyList(),
                cropUndoStack = emptyList(),
                cropOpen = false,
                cropApplied = it.cropEdits.isNotEmpty(),
                cropPageUri = null,
                cropRotationDegrees = 0,
                cropDirty = false,
                sourcePreview = null,
                enhancedPreview = null,
                resultPreview = null
            )
        }
        releaseBitmaps(listOf(snapshot.cropSource, snapshot.cropPreviewSource, snapshot.cropPreview))
        if (snapshot.selected.isNotEmpty()) showPreview(index)
    }


    fun applyCrop() {
        val snapshot = currentState
        val source = snapshot.cropSource ?: return
        val uri = snapshot.cropPageUri ?: return
        val corners = snapshot.cropCorners
        if (!snapshot.cropOpen || snapshot.cropApplying || source.isRecycled ||
            !DocumentCrop.isValid(corners, source.width, source.height)
        ) return

        val normalizedCorners = normalizeCropPoints(corners, source.width, source.height)
        val previewSource = snapshot.cropPreviewSource?.takeIf { !it.isRecycled } ?: source
        val previewCorners = mapCropPoints(source, previewSource, corners)
        val generation = cropGeneration.incrementAndGet()
        update { it.copy(cropApplying = true, status = "جاري حفظ تعديل الصفحة...") }

        executor.execute {
            if (generation != cropGeneration.get() || source.isRecycled) return@execute
            val preview = try {
                DocumentCrop.warp(previewSource, previewCorners, Processor.PREVIEW_MAX_SIDE)
            } catch (error: Exception) {
                update { old ->
                    if (generation != cropGeneration.get()) old
                    else old.copy(
                        cropApplying = false,
                        status = "تعذر تجهيز معاينة القص: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
                return@execute
            }
            if (generation != cropGeneration.get()) {
                if (!preview.isRecycled) preview.recycle()
                return@execute
            }

            var installed = false
            update { old ->
                if (generation != cropGeneration.get() || old.cropPageUri != uri ||
                    old.cropSource !== source || !old.cropOpen
                ) {
                    old
                } else {
                    installed = true
                    old.copy(
                        cropEdits = old.cropEdits + (uri to DocumentPageEdit(
                            rotationDegrees = snapshot.cropRotationDegrees,
                            normalizedCorners = normalizedCorners
                        )),
                        cropPreview = preview,
                        cropApplying = false,
                        cropApplied = true,
                        cropDirty = false,
                        status = "اتحفظ تعديل الصفحة ${snapshot.previewIndex + 1} من ${snapshot.selected.size}. اختار صفحة تانية أو انهي المراجعة."
                    )
                }
            }
            if (installed) {
                releaseBitmaps(listOf(snapshot.cropPreview), setOf(preview))
            } else if (!preview.isRecycled) {
                preview.recycle()
            }
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
        snapshot.processedImages.forEach { runCatching { it.file.delete() } }
        update {
            it.copy(
                busy = true,
                progress = 0,
                savedCount = 0,
                progressMax = uris.size,
                errors = emptyList(),
                processedImages = emptyList(),
                selectedExportIds = emptySet(),
                exportBusy = false,
                exportProgress = 0,
                exportProgressMax = 1,
                exportStatus = "جاهز للتصدير",
                exportErrors = emptyList(),
                exportedCount = 0,
                status = "بدء معالجة الصور…"
            )
        }

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
                    override fun onSaved(index: Int, bitmap: Bitmap, file: File) {
                        val item = ExportImage(
                            id = file.name,
                            sourceUri = uris[index - 1],
                            file = file,
                            displayName = file.name
                        )
                        update {
                            it.copy(
                                progress = index,
                                savedCount = it.savedCount + 1,
                                resultPreview = bitmap,
                                processedImages = it.processedImages + item,
                                selectedExportIds = it.selectedExportIds + item.id,
                                status = "تم تجهيز الصورة ${it.savedCount + 1} من ${uris.size} للتصدير"
                            )
                        }
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
                pageEdits = snapshot.cropEdits
            )
        }
    }

    fun toggleExportImage(id: String, selected: Boolean) {
        if (currentState.busy || currentState.exportBusy) return
        update { old ->
            if (old.processedImages.none { it.id == id }) old
            else old.copy(
                selectedExportIds = if (selected) old.selectedExportIds + id
                else old.selectedExportIds - id
            )
        }
    }

    fun selectAllExportImages(selected: Boolean) {
        if (currentState.busy || currentState.exportBusy) return
        update { old ->
            old.copy(
                selectedExportIds = if (selected) old.processedImages.map { it.id }.toSet()
                else emptySet()
            )
        }
    }

    fun exportSelected(format: ExportFormat) {
        val snapshot = currentState
        if (snapshot.busy || snapshot.exportBusy) return
        val items = snapshot.processedImages.filter { it.id in snapshot.selectedExportIds }
        if (items.isEmpty()) {
            update { it.copy(exportStatus = "اختار صورة واحدة على الأقل قبل التصدير.") }
            return
        }

        update {
            it.copy(
                exportBusy = true,
                exportProgress = 0,
                exportProgressMax = items.size,
                exportStatus = if (format == ExportFormat.PDF)
                    "جاري تجهيز ملف PDF..."
                else "جاري تصدير الصور...",
                exportErrors = emptyList(),
                exportedCount = 0
            )
        }

        exportExecutor.execute {
            val observer = object : ExportRepository.Observer {
                override fun onProgress(completed: Int, total: Int, message: String) {
                    update { old ->
                        old.copy(
                            exportProgress = completed.coerceIn(0, total.coerceAtLeast(1)),
                            exportProgressMax = total.coerceAtLeast(1),
                            exportStatus = message
                        )
                    }
                }

                override fun onError(message: String) {
                    update { old ->
                        old.copy(
                            exportErrors = old.exportErrors + message,
                            exportStatus = message
                        )
                    }
                }

                override fun onFinished(success: Int, failed: Int, error: String?) {
                    update { old ->
                        val finalStatus = when {
                            failed == 0 && format == ExportFormat.PDF ->
                                "تم تصدير ملف PDF بنجاح إلى Documents/PhotoUnifier"
                            failed == 0 ->
                                "تم تصدير $success صورة بنجاح إلى Pictures/PhotoUnifier"
                            success > 0 ->
                                "اكتمل التصدير جزئيًا: نجاح $success، فشل $failed"
                            else ->
                                "فشل التصدير: ${error ?: "راجع تفاصيل الأخطاء أدناه"}"
                        }
                        old.copy(
                            exportBusy = false,
                            exportProgress = old.exportProgressMax,
                            exportedCount = success,
                            exportStatus = finalStatus,
                            exportErrors = if (failed > 0 && old.exportErrors.isEmpty() && error != null)
                                old.exportErrors + error else old.exportErrors
                        )
                    }
                }
            }

            try {
                when (format) {
                    ExportFormat.IMAGES -> exportRepository.exportAsImages(
                        getApplication<Application>().applicationContext,
                        items,
                        observer
                    )
                    ExportFormat.PDF -> exportRepository.exportAsPdf(
                        getApplication<Application>().applicationContext,
                        items,
                        observer
                    )
                }
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                update { old ->
                    old.copy(
                        exportBusy = false,
                        exportStatus = "فشل التصدير: $message",
                        exportErrors = old.exportErrors + message
                    )
                }
            }
        }
    }

    override fun onCleared() {
        imagePreviewGeneration.incrementAndGet()
        previewGeneration.incrementAndGet()
        executor.shutdownNow()
        exportExecutor.shutdownNow()
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
        currentState.selectionThumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        currentState.processedImages.forEach { runCatching { it.file.delete() } }
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

    private fun releaseBitmaps(bitmaps: Iterable<Bitmap?>, keep: Set<Bitmap> = emptySet()) {
        bitmaps.filterNotNull().distinct().forEach { bitmap ->
            if (bitmap !in keep && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_SELECTION_THUMBNAILS = 6
        const val MAX_DYNAMIC_PAGE_THUMBNAILS = 12
        const val SELECTION_THUMBNAIL_SIDE = 256
        const val PAGE_THUMBNAIL_SIDE = 112
    }
}
