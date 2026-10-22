package com.photounifier

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.photounifier.R
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
    val status: String = "",
    val errors: List<String> = emptyList(),
    val processedImages: List<ExportImage> = emptyList(),
    val selectedExportIds: Set<String> = emptySet(),
    val exportBusy: Boolean = false,
    val exportProgress: Int = 0,
    val exportProgressMax: Int = 1,
    val exportStatus: String = "",
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
    private val appContext = application.applicationContext
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
    private val initialUiState = PhotoUiState(
        status = appContext.getString(R.string.status_ready_select),
        exportStatus = appContext.getString(R.string.status_ready_export)
    )
    private val mutableState = MutableLiveData(initialUiState)
    val state: LiveData<PhotoUiState> = mutableState
    @Volatile private var currentState = initialUiState
    @Volatile private var cleared = false

    @Synchronized
    private fun update(transform: (PhotoUiState) -> PhotoUiState): Boolean {
        if (cleared) return false
        currentState = transform(currentState)
        mutableState.postValue(currentState)
        return true
    }

    fun selectImages(uris: List<Uri>) {
        if (cleared || currentState.busy || currentState.exportBusy) return
        val previous = currentState
        previous.processedImages.forEach { runCatching { it.file.delete() } }
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
            exportStatus = appContext.getString(R.string.status_ready_export),
            exportErrors = emptyList(),
            exportedCount = 0,
            status = if (normalizedUris.isEmpty()) appContext.getString(R.string.status_no_images) else appContext.getString(R.string.status_images_selected, normalizedUris.size),
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
        // Each owner executor is serial, so release old bitmaps only after its
        // queued work has finished reading them.
        releaseBitmapsAfterQueuedWork(
            previewExecutor,
            listOf(previous.sourcePreview, previous.enhancedPreview, previous.resultPreview)
        )
        releaseBitmapsAfterQueuedWork(
            executor,
            listOf(previous.cropSource, previous.cropPreviewSource, previous.cropPreview)
        )
        releaseBitmapsAfterQueuedWork(
            thumbnailExecutor,
            previous.selectionThumbnails.map { it.bitmap }
        )
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
                val retained = update { old ->
                    if (selectionGeneration.get() != thumbnailToken || old.selected != normalizedUris) {
                        thumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                        old
                    } else {
                        old.copy(selectionThumbnails = thumbnails.toList())
                    }
                }
                if (!retained) thumbnails.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
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
        if (cleared) return
        val snapshot = currentState
        if (snapshot.busy || snapshot.cropApplying) return
        val uri = snapshot.selected.getOrNull(index) ?: return
        val existingPreview = snapshot.sourcePreview
        if (snapshot.previewIndex == index && existingPreview != null && !existingPreview.isRecycled) return

        val generation = imagePreviewGeneration.incrementAndGet()
        previewGeneration.incrementAndGet()
        var replacedPreviews: List<Bitmap?> = emptyList()
        var clearedPreviews = false
        update { old ->
            if (old.selected.getOrNull(index) != uri) old
            else {
                clearedPreviews = true
                replacedPreviews = listOf(old.sourcePreview, old.enhancedPreview, old.resultPreview)
                old.copy(
                    previewIndex = index,
                    sourcePreview = null,
                    enhancedPreview = null,
                    resultPreview = null,
                    status = appContext.getString(R.string.status_loading_image_preview, index + 1, old.selected.size)
                )
            }
        }
        if (clearedPreviews) {
            releaseBitmapsAfterQueuedWork(previewExecutor, replacedPreviews)
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
                            Processor.PREVIEW_MAX_SIDE, context = appContext
                        )
                        if (cropped !== preview) preview.recycle()
                        preview = cropped
                    }
                }

                var stalePreview = false
                val retained = update { old ->
                    if (generation != imagePreviewGeneration.get() ||
                        old.previewIndex != index ||
                        old.selected.getOrNull(index) != uri
                    ) {
                        stalePreview = true
                        old
                    } else {
                        old.copy(
                            sourcePreview = preview,
                            enhancedPreview = null,
                            resultPreview = null,
                            status = appContext.getString(R.string.status_image_preview, index + 1, old.selected.size)
                        )
                    }
                }
                if (!retained || stalePreview) releaseBitmaps(listOf(preview))
                if (retained && !stalePreview && generation == imagePreviewGeneration.get() &&
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
                        status = appContext.getString(R.string.status_image_preview_failed, index + 1, error.message ?: appContext.getString(R.string.error_generic))
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
                failureMessage = appContext.getString(R.string.status_enhance_preview_failed)
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
                        status = appContext.getString(R.string.status_enhance_preview_ready, old.previewIndex + 1, old.selected.size)
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
            update { it.copy(status = appContext.getString(R.string.status_opencv_unavailable)) }
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
                status = appContext.getString(R.string.status_crop_preparing)
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

                val cropPreviewSrc = createCropPreviewSource(rotated)
                previewSource = cropPreviewSrc
                preview = DocumentCrop.warp(
                    cropPreviewSrc,
                    mapCropPoints(rotated, cropPreviewSrc, corners),
                    Processor.PREVIEW_MAX_SIDE, context = appContext
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
                                savedEdit != null -> appContext.getString(R.string.status_crop_saved_edit_loaded, index + 1)
                                detectedCorners == null -> appContext.getString(R.string.status_crop_no_document)
                                else -> appContext.getString(R.string.status_crop_detected)
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
                        status = appContext.getString(R.string.status_crop_failed, error.message ?: error.javaClass.simpleName)
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
        update { it.copy(cropApplying = true, status = appContext.getString(R.string.status_rotating_page)) }

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
                val cropPreviewSrc = createCropPreviewSource(rotatedBitmap)
                previewSource = cropPreviewSrc
                preview = DocumentCrop.warp(
                    cropPreviewSrc,
                    mapCropPoints(rotatedBitmap, cropPreviewSrc, corners),
                    Processor.PREVIEW_MAX_SIDE, context = appContext
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
                            status = appContext.getString(R.string.status_page_rotated)
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
                        status = appContext.getString(R.string.status_rotate_failed, error.message ?: error.javaClass.simpleName)
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
            val preview = try { DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, candidate), Processor.PREVIEW_MAX_SIDE, context = appContext) } catch (_: Exception) { return@execute }
            if (generation != cropGeneration.get()) {
                releaseBitmaps(listOf(preview))
                return@execute
            }
            var installed = false
            var replacedPreview: Bitmap? = null
            update { old ->
                if (generation != cropGeneration.get() || !old.cropOpen ||
                    old.cropSource !== source || old.cropCorners != candidate
                ) {
                    old
                } else {
                    installed = true
                    replacedPreview = old.cropPreview
                    old.copy(cropPreview = preview)
                }
            }
            if (installed) {
                releaseBitmaps(listOf(replacedPreview), setOf(preview))
            } else {
                releaseBitmaps(listOf(preview))
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
                DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, previous), Processor.PREVIEW_MAX_SIDE, context = appContext)
            } catch (_: Exception) { return@execute }
            if (generation != cropGeneration.get()) {
                releaseBitmaps(listOf(preview))
                return@execute
            }
            var installed = false
            var replacedPreview: Bitmap? = null
            update { old ->
                if (generation != cropGeneration.get() || !old.cropOpen ||
                    old.cropCorners != previous || old.cropSource !== source
                ) {
                    old
                } else {
                    installed = true
                    replacedPreview = old.cropPreview
                    old.copy(cropPreview = preview)
                }
            }
            if (installed) {
                releaseBitmaps(listOf(replacedPreview), setOf(preview))
            } else {
                releaseBitmaps(listOf(preview))
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
                DocumentCrop.warp(previewSource, mapCropPoints(source, previewSource, corners), Processor.PREVIEW_MAX_SIDE, context = appContext)
            } catch (_: Exception) { return@execute }
            if (generation != cropGeneration.get()) {
                releaseBitmaps(listOf(preview))
                return@execute
            }
            var installed = false
            var replacedPreview: Bitmap? = null
            update { old ->
                if (generation != cropGeneration.get() || !old.cropOpen ||
                    old.cropCorners != corners || old.cropSource !== source
                ) {
                    old
                } else {
                    installed = true
                    replacedPreview = old.cropPreview
                    old.copy(cropPreview = preview)
                }
            }
            if (installed) {
                releaseBitmaps(listOf(replacedPreview), setOf(preview))
            } else {
                releaseBitmaps(listOf(preview))
            }
        }
    }

    fun cancelCrop() {
        if (cleared) return
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
        releaseBitmapsAfterQueuedWork(
            executor,
            listOf(snapshot.cropSource, snapshot.cropPreviewSource, snapshot.cropPreview)
        )
        releaseBitmapsAfterQueuedWork(
            previewExecutor,
            listOf(snapshot.sourcePreview, snapshot.enhancedPreview, snapshot.resultPreview)
        )
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
        update { it.copy(cropApplying = true, status = appContext.getString(R.string.status_saving_crop)) }

        executor.execute {
            if (generation != cropGeneration.get() || source.isRecycled) return@execute
            val preview = try {
                DocumentCrop.warp(previewSource, previewCorners, Processor.PREVIEW_MAX_SIDE, context = appContext)
            } catch (error: Exception) {
                update { old ->
                    if (generation != cropGeneration.get()) old
                    else old.copy(
                        cropApplying = false,
                        status = appContext.getString(R.string.status_crop_preview_failed, error.message ?: error.javaClass.simpleName)
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
                        status = appContext.getString(R.string.status_crop_saved, snapshot.previewIndex + 1, snapshot.selected.size)
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
        if (cleared) return
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
            var installed = false
            update { old ->
                if ((old.enhancedPreview !== base && old.sourcePreview !== base) ||
                    generation != previewGeneration.get() || base.isRecycled
                ) {
                    old
                } else {
                    installed = true
                    old.copy(resultPreview = preview)
                }
            }
            if (!installed) releaseBitmaps(listOf(preview))
        }
    }

    fun process(openCvReady: Boolean) {
        val snapshot = currentState
        if (snapshot.busy || snapshot.selected.isEmpty()) return
        if (!openCvReady) {
            update { it.copy(status = appContext.getString(R.string.status_image_processing_unavailable)) }
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
                exportStatus = appContext.getString(R.string.status_ready_export),
                exportErrors = emptyList(),
                exportedCount = 0,
                status = appContext.getString(R.string.status_processing_started)
            )
        }

        executor.execute {
            repository.process(
                getApplication<Application>().applicationContext, uris, brightness, contrast,
                object : PhotoRepository.Observer {
                    override fun onSource(index: Int, total: Int, bitmap: Bitmap) {
                        var replaced: List<Bitmap?> = emptyList()
                        val retained = update { old ->
                            replaced = listOf(old.sourcePreview, old.enhancedPreview, old.resultPreview)
                            old.copy(
                                status = appContext.getString(R.string.status_analyzing_image, index, total),
                                previewIndex = index - 1,
                                sourcePreview = bitmap,
                                enhancedPreview = null,
                                resultPreview = null
                            )
                        }
                        if (retained) releaseBitmapsAfterQueuedWork(previewExecutor, replaced)
                        else releaseBitmaps(listOf(bitmap))
                    }
                    override fun onClassification(index: Int, total: Int, kind: ImageKind) {
                        update { it.copy(status = appContext.getString(R.string.status_processing_image, index, total, getApplication<Application>().getString(kind.labelRes))) }
                    }
                    override fun onEnhanced(bitmap: Bitmap) {
                        var replaced: List<Bitmap?> = emptyList()
                        val retained = update { old ->
                            replaced = listOf(old.enhancedPreview, old.resultPreview)
                            old.copy(enhancedPreview = bitmap, resultPreview = null)
                        }
                        if (!retained) {
                            releaseBitmaps(listOf(bitmap))
                            return
                        }
                        releaseBitmapsAfterQueuedWork(previewExecutor, replaced)
                        refreshPreview()
                    }
                    override fun onSaved(index: Int, bitmap: Bitmap, file: File) {
                        val item = ExportImage(
                            id = file.name,
                            sourceUri = uris[index - 1],
                            file = file,
                            displayName = file.name
                        )
                        var replacedResult: Bitmap? = null
                        val retained = update { old ->
                            replacedResult = old.resultPreview
                            old.copy(
                                progress = index,
                                savedCount = old.savedCount + 1,
                                resultPreview = bitmap,
                                processedImages = old.processedImages + item,
                                selectedExportIds = old.selectedExportIds + item.id,
                                status = appContext.getString(R.string.status_image_ready_export, old.savedCount + 1, uris.size)
                            )
                        }
                        if (!retained) {
                            releaseBitmaps(listOf(bitmap))
                            runCatching { file.delete() }
                            return
                        }
                        releaseBitmapsAfterQueuedWork(previewExecutor, listOf(replacedResult))
                        // Reapply the latest manual settings after the saved-result event.
                        refreshPreview()
                    }
                    override fun onError(index: Int, total: Int, uri: Uri, message: String) {
                        val item = appContext.getString(R.string.status_image_error, index, total, uri.lastPathSegment ?: appContext.getString(R.string.error_image_file), message)
                        update { old -> old.copy(progress = maxOf(old.progress, index), errors = old.errors + item, status = appContext.getString(R.string.status_image_processing_failed, index, total, message)) }
                    }
                    override fun onFinished(success: Int, failed: Int, error: String?) {
                        val detail = error?.let { appContext.getString(R.string.status_last_error, it) }.orEmpty()
                        update {
                            it.copy(
                                busy = false,
                                progress = it.progressMax,
                                status = appContext.getString(R.string.status_processing_complete, success, failed, detail)
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
            update { it.copy(exportStatus = appContext.getString(R.string.status_select_before_export)) }
            return
        }

        update {
            it.copy(
                exportBusy = true,
                exportProgress = 0,
                exportProgressMax = items.size,
                exportStatus = if (format == ExportFormat.PDF)
                    appContext.getString(R.string.status_preparing_pdf)
                else appContext.getString(R.string.status_exporting_images),
                exportErrors = emptyList(),
                exportedCount = 0
            )
        }

        exportExecutor.execute {
            val successfulImageIds = mutableSetOf<String>()
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

                override fun onItemFinished(itemId: String, success: Boolean) {
                    if (success) successfulImageIds += itemId
                }

                override fun onFinished(success: Int, failed: Int, error: String?) {
                    update { old ->
                        val finalStatus = when {
                            failed == 0 && format == ExportFormat.PDF ->
                                appContext.getString(R.string.status_pdf_export_success)
                            failed == 0 ->
                                appContext.getString(R.string.status_images_export_success, success)
                            success > 0 ->
                                appContext.getString(R.string.status_export_partial, success, failed)
                            else ->
                                appContext.getString(R.string.status_export_failed, error ?: appContext.getString(R.string.status_export_error_detail))
                        }
                        old.copy(
                            exportBusy = false,
                            exportProgress = old.exportProgressMax,
                            exportedCount = success,
                            selectedExportIds = remainingExportIdsAfterExport(
                                format = format,
                                selectedIds = old.selectedExportIds,
                                successfulImageIds = successfulImageIds,
                                failedCount = failed
                            ),
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
                        exportStatus = appContext.getString(R.string.status_export_failed, message),
                        exportErrors = old.exportErrors + message
                    )
                }
            }
        }
    }

    @Synchronized
    override fun onCleared() {
        cleared = true
        imagePreviewGeneration.incrementAndGet()
        previewGeneration.incrementAndGet()
        cropGeneration.incrementAndGet()
        selectionGeneration.incrementAndGet()
        val previous = currentState

        // Retire each bitmap group on the same serial executor that may still be
        // reading it. Enqueue cleanup before shutting executors down gracefully.
        releaseBitmapsAfterQueuedWork(
            previewExecutor,
            listOf(previous.sourcePreview, previous.enhancedPreview, previous.resultPreview)
        )
        releaseBitmapsAfterQueuedWork(
            executor,
            listOf(previous.cropSource, previous.cropPreviewSource, previous.cropPreview)
        )
        releaseBitmapsAfterQueuedWork(
            thumbnailExecutor,
            previous.selectionThumbnails.map { it.bitmap }
        )

        executor.shutdown()
        exportExecutor.shutdown()
        previewExecutor.shutdown()
        thumbnailExecutor.shutdown()
        imagePreviewExecutor.shutdown()
        previous.processedImages.forEach { runCatching { it.file.delete() } }
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

    private fun releaseBitmapsAfterQueuedWork(
        owner: java.util.concurrent.ExecutorService,
        bitmaps: Iterable<Bitmap?>,
        keep: Set<Bitmap> = emptySet()
    ) {
        val pending = bitmaps.filterNotNull().distinct().filter { it !in keep && !it.isRecycled }
        if (pending.isEmpty()) return
        try {
            owner.execute { releaseBitmaps(pending, keep) }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // A rejected cleanup must not recycle synchronously while an in-flight
            // worker could still be reading the bitmap.
        }
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
