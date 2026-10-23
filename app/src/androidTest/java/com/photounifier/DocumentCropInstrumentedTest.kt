package com.photounifier

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Device-level regression tests for the real OpenCV perspective warp.
 * Unlike JVM geometry tests, this verifies the returned Android Bitmap and pixels.
 */
@RunWith(AndroidJUnit4::class)
class DocumentCropInstrumentedTest {

    @Test
    fun detectFindsSkewedDocumentAndItsCornersProduceACroppedBitmap() {
        assertTrue("OpenCV native library must load for automatic detection", OpenCVLoader.initLocal())

        val sourceWidth = 320
        val sourceHeight = 260
        val source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        try {
            val paperColor = Color.rgb(245, 245, 235)
            Canvas(source).apply {
                drawColor(Color.rgb(48, 50, 54))
                val paper = Path().apply {
                    moveTo(55f, 30f)
                    lineTo(270f, 45f)
                    lineTo(250f, 225f)
                    lineTo(35f, 210f)
                    close()
                }
                drawPath(
                    paper,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = paperColor
                        style = Paint.Style.FILL
                    }
                )
            }

            // Null context deliberately exercises the OpenCV fallback without
            // making this geometry regression depend on neural-model inference.
            val detected = DocumentCrop.detect(source)
                ?: throw AssertionError("A high-contrast skewed document should be detected")
            assertEquals("Detection must return exactly four crop corners", 4, detected.size)
            assertTrue(
                "Detected crop corners must form a valid quadrilateral",
                DocumentCrop.isValid(detected, sourceWidth, sourceHeight)
            )

            val expectedCorners = listOf(
                CropPoint(55f, 30f),
                CropPoint(270f, 45f),
                CropPoint(250f, 225f),
                CropPoint(35f, 210f)
            )
            detected.forEachIndexed { index, point ->
                val expected = expectedCorners[index]
                val dx = point.x - expected.x
                val dy = point.y - expected.y
                assertTrue(
                    "Corner $index was too far from the rendered document boundary: $point",
                    dx * dx + dy * dy <= 18f * 18f
                )
            }

            val cropped = DocumentCrop.warp(source, detected)
            try {
                assertTrue("Detected crop should be narrower than the source", cropped.width < source.width)
                assertTrue("Detected crop should be shorter than the source", cropped.height < source.height)
                assertEquals(
                    "The warped center should contain document pixels, not background",
                    paperColor,
                    cropped.getPixel(cropped.width / 2, cropped.height / 2)
                )
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    @Test
    fun detectReturnsNullForFeaturelessImageInsteadOfInventingCropCorners() {
        assertTrue("OpenCV native library must load for automatic detection", OpenCVLoader.initLocal())

        val source = Bitmap.createBitmap(320, 260, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.rgb(128, 128, 128))
            assertEquals(
                "A featureless image must fall back to manual cropping",
                null,
                DocumentCrop.detect(source)
            )
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }



    @Test
    fun warpCorrectsPerspectiveAndKeepsOutputWithinPreviewLimit() {
        assertTrue("OpenCV native library must load for perspective cropping", OpenCVLoader.initLocal())

        val sourceWidth = 220
        val sourceHeight = 170
        val source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(sourceWidth * sourceHeight) { Color.RED }
            for (y in 5 until 150) {
                for (x in 10 until 200) {
                    pixels[y * sourceWidth + x] = Color.GREEN
                }
            }
            source.setPixels(pixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)

            val cropped = DocumentCrop.warp(
                source,
                listOf(
                    CropPoint(30f, 10f),
                    CropPoint(180f, 20f),
                    CropPoint(170f, 140f),
                    CropPoint(20f, 130f)
                )
            )
            try {
                assertEquals(150, cropped.width)
                assertEquals(120, cropped.height)
                assertEquals(Color.GREEN, cropped.getPixel(cropped.width / 2, cropped.height / 2))
                assertTrue(cropped.width < source.width)
                assertTrue(cropped.height < source.height)
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }

            val preview = DocumentCrop.warp(
                source,
                listOf(
                    CropPoint(10f, 10f),
                    CropPoint(200f, 10f),
                    CropPoint(200f, 150f),
                    CropPoint(10f, 150f)
                ),
                maxOutputSide = 80
            )
            try {
                assertTrue("Preview width must not exceed configured limit", preview.width <= 80)
                assertTrue("Preview height must not exceed configured limit", preview.height <= 80)
                assertTrue(preview.width > 1 && preview.height > 1)
            } finally {
                if (!preview.isRecycled) preview.recycle()
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    @Test
    fun warpProducesCroppedBitmapWithCroppedPixelContent() {
        assertTrue("OpenCV native library must load for perspective cropping", OpenCVLoader.initLocal())

        val sourceWidth = 220
        val sourceHeight = 170
        val source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        try {
            // Red outside the selected page; green within it. A real warp should
            // return only the selected region instead of handing the source back.
            val pixels = IntArray(sourceWidth * sourceHeight) { Color.RED }
            for (y in 10 until 140) {
                for (x in 20 until 180) {
                    pixels[y * sourceWidth + x] = Color.GREEN
                }
            }
            source.setPixels(pixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)

            val cropped = DocumentCrop.warp(
                source,
                listOf(
                    CropPoint(20f, 10f),
                    CropPoint(180f, 10f),
                    CropPoint(180f, 140f),
                    CropPoint(20f, 140f)
                )
            )
            try {
                assertEquals(160, cropped.width)
                assertEquals(130, cropped.height)
                assertEquals(Color.GREEN, cropped.getPixel(cropped.width / 2, cropped.height / 2))
                assertTrue(cropped.width < source.width)
                assertTrue(cropped.height < source.height)
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    @Test
    fun repositoryAppliesSavedCropEditToProcessedOutput() {
        assertTrue("OpenCV native library must load for the crop processing pipeline", OpenCVLoader.initLocal())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceWidth = 220
        val sourceHeight = 170
        val source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        val inputFile = File(context.cacheDir, "crop-pipeline-${System.nanoTime()}.png")
        var sourcePreview: Bitmap? = null
        var enhancedPreview: Bitmap? = null
        var savedPreview: Bitmap? = null
        var savedFile: File? = null
        var errorMessage: String? = null
        var finishedSuccess = -1
        var finishedFailed = -1

        try {
            val pixels = IntArray(sourceWidth * sourceHeight) { Color.RED }
            for (y in 10 until 140) {
                for (x in 20 until 180) {
                    pixels[y * sourceWidth + x] = Color.GREEN
                }
            }
            source.setPixels(pixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)
            FileOutputStream(inputFile).use { output ->
                assertTrue("Synthetic source image must be written", source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            val uri = Uri.fromFile(inputFile)
            val corners = normalizeCropPoints(
                listOf(
                    CropPoint(20f, 10f),
                    CropPoint(180f, 10f),
                    CropPoint(180f, 140f),
                    CropPoint(20f, 140f)
                ),
                sourceWidth,
                sourceHeight
            )
            val observer = object : PhotoRepository.Observer {
                override fun onSource(index: Int, total: Int, bitmap: Bitmap) {
                    sourcePreview = bitmap
                }

                override fun onClassification(index: Int, total: Int, kind: ImageKind) = Unit

                override fun onEnhanced(bitmap: Bitmap) {
                    enhancedPreview = bitmap
                }

                override fun onSaved(index: Int, bitmap: Bitmap, file: File) {
                    savedPreview = bitmap
                    savedFile = file
                }

                override fun onError(index: Int, total: Int, uri: Uri, message: String) {
                    errorMessage = message
                }

                override fun onFinished(success: Int, failed: Int, error: String?) {
                    finishedSuccess = success
                    finishedFailed = failed
                    if (error != null) errorMessage = error
                }
            }

            PhotoRepository().process(
                context = context,
                uris = listOf(uri),
                brightness = 0,
                contrastPercent = 100,
                observer = observer,
                pageEdits = mapOf(
                    uri to DocumentPageEdit(
                        rotationDegrees = 0,
                        normalizedCorners = corners
                    )
                )
            )

            assertEquals("Saved crop should complete as one successful processing item", 1, finishedSuccess)
            assertEquals("Crop processing must not report a failed item: $errorMessage", 0, finishedFailed)
            assertEquals("Crop processing must not report an error: $errorMessage", null, errorMessage)

            val croppedSourcePreview = sourcePreview
                ?: throw AssertionError("Repository should publish the cropped source preview")
            assertEquals("Crop width should reach the processing pipeline", 160, croppedSourcePreview.width)
            assertEquals("Crop height should reach the processing pipeline", 130, croppedSourcePreview.height)
            assertEquals(
                "The source preview should contain pixels from inside the selected crop",
                Color.GREEN,
                croppedSourcePreview.getPixel(croppedSourcePreview.width / 2, croppedSourcePreview.height / 2)
            )

            val finalPreview = savedPreview
                ?: throw AssertionError("Repository should publish the processed output preview")
            assertEquals("Final processing output must retain crop width", 160, finalPreview.width)
            assertEquals("Final processing output must retain crop height", 130, finalPreview.height)

            val file = savedFile ?: throw AssertionError("Repository should write its output image")
            val finalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: throw AssertionError("Saved output must be a readable image")
            try {
                assertEquals("Saved file must contain cropped pixels, not the original frame width", 160, finalBitmap.width)
                assertEquals("Saved file must contain cropped pixels, not the original frame height", 130, finalBitmap.height)
            } finally {
                if (!finalBitmap.isRecycled) finalBitmap.recycle()
            }
        } finally {
            sourcePreview?.let { if (!it.isRecycled) it.recycle() }
            enhancedPreview?.let { if (!it.isRecycled) it.recycle() }
            savedPreview?.let { if (!it.isRecycled) it.recycle() }
            if (!source.isRecycled) source.recycle()
            inputFile.delete()
            savedFile?.delete()
        }
    }



    @Test
    fun replacingCropPreviewsRecyclesPreviousBitmapsAfterDragUndoAndReset() {
        assertTrue("OpenCV native library must load for crop preview lifecycle tests", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "crop-preview-lifecycle-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(220, 170, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.rgb(230, 230, 225))
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for crop preview state: $description")
        }

        fun awaitReplacement(previous: Bitmap, description: String): PhotoUiState =
            awaitState(description) {
                it.cropOpen && it.cropPreview != null &&
                    it.cropPreview !== previous && !it.cropApplying
            }

        fun moveTopLeftInward(state: PhotoUiState) {
            val sourceBitmap = state.cropSource
                ?: throw AssertionError("Crop source must remain available while editing")
            val topLeft = state.cropCorners.firstOrNull()
                ?: throw AssertionError("Crop corners must be initialized")
            val moved = CropPoint(
                (topLeft.x + 4f).coerceAtMost((sourceBitmap.width - 1).toFloat()),
                (topLeft.y + 4f).coerceAtMost((sourceBitmap.height - 1).toFloat())
            )
            val candidate = state.cropCorners.toMutableList().apply { set(0, moved) }
            assertTrue("The test drag must leave a valid crop quadrilateral", DocumentCrop.isValid(candidate, sourceBitmap.width, sourceBitmap.height))
            viewModel.moveCropCorner(0, moved.x, moved.y)
        }

        try {
            viewModel.selectImages(listOf(Uri.fromFile(sourceFile)))
            viewModel.openCropEditor(openCvReady = true)
            var state = awaitState("initial crop editor") {
                it.cropOpen && !it.cropApplying && it.cropSource != null && it.cropPreview != null
            }

            var oldPreview = state.cropPreview
                ?: throw AssertionError("Initial crop preview must exist")
            moveTopLeftInward(state)
            state = awaitReplacement(oldPreview, "preview after first drag")
            assertTrue("First drag must recycle the replaced preview bitmap", oldPreview.isRecycled)

            oldPreview = state.cropPreview
                ?: throw AssertionError("Preview after first drag must exist")
            moveTopLeftInward(state)
            state = awaitReplacement(oldPreview, "preview after second drag")
            assertTrue("Second drag must recycle the replaced preview bitmap", oldPreview.isRecycled)

            oldPreview = state.cropPreview
                ?: throw AssertionError("Preview after second drag must exist")
            viewModel.undoCrop()
            state = awaitReplacement(oldPreview, "preview after undo")
            assertTrue("Undo must recycle the replaced preview bitmap", oldPreview.isRecycled)

            oldPreview = state.cropPreview
                ?: throw AssertionError("Preview after undo must exist")
            viewModel.resetCrop()
            state = awaitReplacement(oldPreview, "preview after reset")
            assertTrue("Reset must recycle the replaced preview bitmap", oldPreview.isRecycled)
        } finally {
            viewModelStore.clear()
            sourceFile.delete()
        }
    }


    @Test
    fun switchingSelectionRecyclesSupersededPreviewAndThumbnailBitmaps() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val firstFile = File(app.cacheDir, "bitmap-selection-first-${System.nanoTime()}.png")
        val secondFile = File(app.cacheDir, "bitmap-selection-second-${System.nanoTime()}.png")

        fun writeImage(file: File, color: Int) {
            val bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                FileOutputStream(file).use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for bitmap lifecycle state: $description")
        }

        try {
            writeImage(firstFile, Color.rgb(232, 229, 220))
            writeImage(secondFile, Color.rgb(220, 229, 235))
            val firstUri = Uri.fromFile(firstFile)
            val secondUri = Uri.fromFile(secondFile)

            viewModel.selectImages(listOf(firstUri))
            awaitState("first selection preview and thumbnail") {
                it.selected == listOf(firstUri) &&
                    it.sourcePreview != null &&
                    it.selectionThumbnails.any { thumbnail -> thumbnail.uri == firstUri }
            }
            viewModel.setBrightness(8)
            val previousState = awaitState("first result preview") {
                it.selected == listOf(firstUri) && it.sourcePreview != null && it.resultPreview != null
            }
            val oldBitmaps = (
                listOf(previousState.sourcePreview, previousState.enhancedPreview, previousState.resultPreview) +
                    previousState.selectionThumbnails.map { it.bitmap }
                ).filterNotNull().distinct()
            assertTrue("The first selection must produce bitmaps to retire", oldBitmaps.isNotEmpty())

            viewModel.selectImages(listOf(secondUri))
            awaitState("second selection and release of old bitmaps") {
                it.selected == listOf(secondUri) &&
                    it.sourcePreview != null &&
                    oldBitmaps.all { bitmap -> bitmap.isRecycled }
            }
            assertTrue(
                "All prior selection thumbnails must be recycled when the selection changes",
                previousState.selectionThumbnails.all { it.bitmap.isRecycled }
            )
        } finally {
            viewModelStore.clear()
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test
    fun cancelCropReleasesCropAndPreviewBitmapsAfterQueuedWorkFinishes() {
        assertTrue("OpenCV native library must load for crop lifecycle tests", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "bitmap-cancel-crop-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for crop cancellation state: $description")
        }

        try {
            source.eraseColor(Color.rgb(230, 230, 225))
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            viewModel.selectImages(listOf(Uri.fromFile(sourceFile)))
            awaitState("source preview before crop") { it.sourcePreview != null }
            viewModel.openCropEditor(openCvReady = true)
            val cropState = awaitState("crop editor bitmaps") {
                it.cropOpen && !it.cropApplying &&
                    it.cropSource != null && it.cropPreview != null
            }
            val retired = (
                listOf(
                    cropState.sourcePreview,
                    cropState.enhancedPreview,
                    cropState.resultPreview,
                    cropState.cropSource,
                    cropState.cropPreviewSource,
                    cropState.cropPreview
                )
                ).filterNotNull().distinct()
            assertTrue("Crop cancellation test needs active crop bitmaps", retired.any { it === cropState.cropSource })

            viewModel.cancelCrop()
            awaitState("crop cancellation and safe bitmap release") {
                !it.cropOpen &&
                    it.cropSource == null &&
                    it.cropPreviewSource == null &&
                    it.cropPreview == null &&
                    it.sourcePreview != null &&
                    retired.all { bitmap -> bitmap.isRecycled }
            }
            assertTrue("Cancel must recycle the full-resolution crop source", cropState.cropSource!!.isRecycled)
        } finally {
            viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }


    @Test
    fun clearingViewModelRecyclesOwnedBitmapsAfterQueuedReadersFinish() {
        assertTrue("OpenCV native library must load for ViewModel teardown tests", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "bitmap-viewmodel-clear-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(300, 240, Bitmap.Config.ARGB_8888)
        var storeCleared = false

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for ViewModel teardown state: $description")
        }

        try {
            source.eraseColor(Color.rgb(231, 229, 222))
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            val uri = Uri.fromFile(sourceFile)
            viewModel.selectImages(listOf(uri))
            awaitState("source preview and selection thumbnail") {
                it.sourcePreview != null &&
                    it.selectionThumbnails.any { thumbnail -> thumbnail.uri == uri }
            }

            viewModel.setBrightness(12)
            awaitState("enhanced and adjusted previews") {
                it.sourcePreview != null &&
                    it.enhancedPreview != null &&
                    it.resultPreview != null
            }

            viewModel.openCropEditor(openCvReady = true)
            val active = awaitState("active crop editor with previews") {
                it.cropOpen && !it.cropApplying &&
                    it.cropSource != null && it.cropPreview != null &&
                    it.selectionThumbnails.isNotEmpty()
            }
            val trackedBitmaps = (
                listOf(
                    active.sourcePreview,
                    active.enhancedPreview,
                    active.resultPreview,
                    active.cropSource,
                    active.cropPreviewSource,
                    active.cropPreview
                ) + active.selectionThumbnails.map { it.bitmap }
                ).filterNotNull().distinct()
            assertTrue(
                "Teardown test should track source, crop, and thumbnail bitmap ownership",
                trackedBitmaps.any { it === active.cropSource } &&
                    trackedBitmaps.any { it === active.sourcePreview } &&
                    active.selectionThumbnails.any { thumbnail -> trackedBitmaps.contains(thumbnail.bitmap) }
            )

            viewModelStore.clear()
            storeCleared = true

            val deadline = SystemClock.uptimeMillis() + 10_000
            while (SystemClock.uptimeMillis() < deadline && trackedBitmaps.any { !it.isRecycled }) {
                SystemClock.sleep(50)
            }
            val unrecycled = trackedBitmaps.filterNot { it.isRecycled }
            assertTrue(
                "ViewModel teardown must recycle all owned bitmaps after serial executor queues drain; " +
                    "remaining=${unrecycled.size}",
                unrecycled.isEmpty()
            )
        } finally {
            if (!storeCleared) viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }


    @Test
    fun clearingViewModelDefersOutputDeletionUntilExportExecutorDrains() {
        assertTrue("OpenCV native library must load for export cleanup tests", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "export-cleanup-source-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(300, 240, Bitmap.Config.ARGB_8888)
        val uri = Uri.fromFile(sourceFile)
        var storeCleared = false
        val releaseExportQueue = java.util.concurrent.CountDownLatch(1)
        val exportTaskStarted = java.util.concurrent.CountDownLatch(1)
        val fileExistedBeforeQueueDrained = java.util.concurrent.atomic.AtomicBoolean(false)

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for export cleanup state: $description")
        }

        try {
            val pixels = IntArray(source.width * source.height) { Color.RED }
            for (y in 10 until source.height - 10) {
                for (x in 10 until source.width - 10) {
                    pixels[y * source.width + x] = Color.GREEN
                }
            }
            source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            viewModel.selectImages(listOf(uri))
            awaitState("selected image preview") {
                it.selected == listOf(uri) && it.sourcePreview != null
            }

            viewModel.process(openCvReady = true)
            val processed = awaitState("completed processing output") {
                !it.busy && it.processedImages.isNotEmpty()
            }
            val outputFile = processed.processedImages.first().file
            assertTrue("The processed output must exist before teardown", outputFile.exists())

            // Occupy the same serial executor used by exports. The cleanup task
            // must queue behind this gate rather than deleting output synchronously.
            val executorField = PhotoViewModel::class.java.getDeclaredField("exportExecutor")
            executorField.isAccessible = true
            val exportExecutor = executorField.get(viewModel) as java.util.concurrent.ExecutorService
            exportExecutor.execute {
                exportTaskStarted.countDown()
                releaseExportQueue.await()
                fileExistedBeforeQueueDrained.set(outputFile.exists())
            }
            assertTrue(
                "The export executor blocker must start before clearing the ViewModel",
                exportTaskStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)
            )

            viewModelStore.clear()
            storeCleared = true
            assertTrue(
                "ViewModel teardown must not delete a processed file while export work is still active",
                outputFile.exists()
            )

            releaseExportQueue.countDown()
            val deleteDeadline = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < deleteDeadline && outputFile.exists()) {
                SystemClock.sleep(50)
            }
            assertTrue(
                "The file should remain readable until the export queue reaches cleanup",
                fileExistedBeforeQueueDrained.get()
            )
            assertTrue(
                "The queued cleanup should eventually delete the processed temporary file",
                !outputFile.exists()
            )
        } finally {
            releaseExportQueue.countDown()
            if (!storeCleared) viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }


    @Test
    fun backgroundActionsAfterViewModelClearAreIgnored() {
        assertTrue("OpenCV native library must load for ViewModel teardown guards", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "bitmap-clear-guard-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(300, 240, Bitmap.Config.ARGB_8888)
        var storeCleared = false

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for lifecycle guard state: $description")
        }

        val pixels = IntArray(source.width * source.height) { Color.RED }
        for (y in 10 until source.height - 10) {
            for (x in 10 until source.width - 10) {
                pixels[y * source.width + x] = Color.GREEN
            }
        }
        source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        try {
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val uri = Uri.fromFile(sourceFile)
            viewModel.selectImages(listOf(uri))
            awaitState("source preview") { it.sourcePreview != null }

            viewModel.process(openCvReady = true)
            val processed = awaitState("processed image output") {
                !it.busy && it.processedImages.isNotEmpty()
            }
            val output = processed.processedImages.first()
            viewModel.toggleExportImage(output.id, true)

            viewModel.openCropEditor(openCvReady = true)
            val active = awaitState("crop editor before ViewModel clear") {
                it.cropOpen && !it.cropApplying &&
                    it.cropSource != null && it.cropPreview != null
            }
            val selectedBeforeClear = active.selected
            val cropUriBeforeClear = active.cropPageUri
            val selectedExportIdsBeforeClear = active.selectedExportIds

            viewModelStore.clear()
            storeCleared = true

            try {
                // These methods previously could submit to executors already shut
                // down by onCleared(), or touch bitmaps that teardown was recycling.
                viewModel.selectImages(emptyList())
                viewModel.showPreview(0)
                viewModel.ensurePageThumbnail(10)
                viewModel.openCropEditor(openCvReady = true)
                viewModel.rotateCropPage()
                viewModel.moveCropCorner(0, 8f, 8f)
                viewModel.undoCrop()
                viewModel.resetCrop()
                viewModel.cancelCrop()
                viewModel.applyCrop()
                viewModel.setBrightness(10)
                viewModel.setContrast(120)
                viewModel.process(openCvReady = true)
                viewModel.toggleExportImage(output.id, false)
                viewModel.selectAllExportImages(false)
                viewModel.exportSelected(ExportFormat.IMAGES)
            } catch (error: Throwable) {
                throw AssertionError(
                    "Public background actions must safely no-op after ViewModel teardown",
                    error
                )
            }

            val afterClear = viewModel.state.value
                ?: throw AssertionError("ViewModel should retain its last immutable state snapshot")
            assertEquals("Selection should not be mutated after clear", selectedBeforeClear, afterClear.selected)
            assertEquals("Crop session should not be mutated after clear", cropUriBeforeClear, afterClear.cropPageUri)
            assertEquals("Export selection should not be mutated after clear", selectedExportIdsBeforeClear, afterClear.selectedExportIds)
        } finally {
            if (!storeCleared) viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }


    @Test
    fun repeatedAdjustmentsRecycleSupersededResultPreviews() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "adjusted-preview-lifecycle-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(280, 220, Bitmap.Config.ARGB_8888)

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for adjusted preview state: $description")
        }

        try {
            val pixels = IntArray(source.width * source.height) { Color.WHITE }
            for (y in 12 until source.height - 12) {
                for (x in 12 until source.width - 12) {
                    pixels[y * source.width + x] = if ((x / 12 + y / 12) % 2 == 0) Color.BLACK else Color.GRAY
                }
            }
            source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            viewModel.selectImages(listOf(Uri.fromFile(sourceFile)))
            var state = awaitState("source, enhanced, and adjusted preview") {
                it.sourcePreview != null && it.enhancedPreview != null && it.resultPreview != null
            }

            val adjustmentValues = listOf(8, 16, 24)
            for (brightness in adjustmentValues) {
                val previousResult = state.resultPreview
                    ?: throw AssertionError("An adjusted result preview must exist before changing brightness")
                viewModel.setBrightness(brightness)
                state = awaitState("replacement adjusted preview at brightness $brightness") {
                    it.brightness == brightness &&
                        it.resultPreview != null &&
                        it.resultPreview !== previousResult &&
                        previousResult.isRecycled
                }
                assertTrue(
                    "Superseded result preview must be recycled after brightness $brightness",
                    previousResult.isRecycled
                )
            }
        } finally {
            viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }


    @Test
    fun processingIsIgnoredWhileExportIsReadingExistingOutputs() {
        assertTrue("OpenCV native library must load for export/processing lifecycle tests", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "process-during-export-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(300, 240, Bitmap.Config.ARGB_8888)
        val releaseExportQueue = java.util.concurrent.CountDownLatch(1)
        val exportTaskStarted = java.util.concurrent.CountDownLatch(1)
        var storeCleared = false

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for export/process state: $description")
        }

        try {
            val pixels = IntArray(source.width * source.height) { Color.RED }
            for (y in 10 until source.height - 10) {
                for (x in 10 until source.width - 10) {
                    pixels[y * source.width + x] = Color.GREEN
                }
            }
            source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            val uri = Uri.fromFile(sourceFile)
            viewModel.selectImages(listOf(uri))
            awaitState("source preview") { it.selected == listOf(uri) && it.sourcePreview != null }

            viewModel.process(openCvReady = true)
            val processed = awaitState("first processing result") {
                !it.busy && it.processedImages.isNotEmpty() && it.selectedExportIds.isNotEmpty()
            }
            val output = processed.processedImages.first()
            assertTrue("Processed temporary output must exist before export", output.file.exists())

            // Hold the export executor at a deterministic queue barrier so the
            // test can call process() while exportBusy is definitely true.
            val executorField = PhotoViewModel::class.java.getDeclaredField("exportExecutor")
            executorField.isAccessible = true
            val exportExecutor = executorField.get(viewModel) as java.util.concurrent.ExecutorService
            exportExecutor.execute {
                exportTaskStarted.countDown()
                releaseExportQueue.await()
            }
            assertTrue(
                "Export executor barrier should start before beginning export",
                exportTaskStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)
            )

            viewModel.exportSelected(ExportFormat.IMAGES)
            awaitState("export is queued and owns the current result list") {
                it.exportBusy && !it.busy &&
                    it.processedImages.any { image -> image.id == output.id }
            }

            // This used to delete the output and clear exportBusy while the export
            // task was pending, leaving the exporter with a missing input file.
            viewModel.process(openCvReady = true)
            awaitState("reprocessing ignored during export", timeoutMillis = 5_000) {
                it.exportBusy && !it.busy &&
                    it.processedImages.any { image -> image.id == output.id } &&
                    output.file.exists()
            }

            assertTrue(
                "Processing must preserve the currently exported temporary file",
                output.file.exists()
            )
        } finally {
            releaseExportQueue.countDown()
            if (!storeCleared) {
                // Wait for an accepted export to settle before destroying its owner.
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (SystemClock.uptimeMillis() < deadline && viewModel.state.value?.exportBusy == true) {
                    SystemClock.sleep(50)
                }
                viewModelStore.clear()
                storeCleared = true
            }
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }




    @Test
    fun rapidBrightnessAndContrastChangesRenderLatestPreviewWithoutBacklog() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "coalesced-adjustment-preview-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for coalesced preview state: $description")
        }

        try {
            val pixels = IntArray(source.width * source.height) { Color.WHITE }
            for (y in 12 until source.height - 12) {
                for (x in 12 until source.width - 12) {
                    pixels[y * source.width + x] =
                        if ((x / 10 + y / 10) % 2 == 0) Color.BLACK else Color.GRAY
                }
            }
            source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            viewModel.selectImages(listOf(Uri.fromFile(sourceFile)))
            val initial = awaitState("initial enhanced and adjusted preview") {
                it.sourcePreview != null && it.enhancedPreview != null && it.resultPreview != null
            }
            val supersededResult = initial.resultPreview
                ?: throw AssertionError("Initial adjusted preview must exist")

            // Emulate a fast Compose slider drag: dozens of updates arrive before
            // the image-processing worker would normally finish a single render.
            repeat(60) { index ->
                viewModel.setBrightness((index % 90) - 45)
                viewModel.setContrast(70 + (index % 130))
            }
            val targetBrightness = 37
            val targetContrast = 173
            viewModel.setBrightness(targetBrightness)
            viewModel.setContrast(targetContrast)

            val finalState = awaitState("latest values are rendered and old result released", 45_000) {
                it.brightness == targetBrightness &&
                    it.contrastPercent == targetContrast &&
                    it.enhancedPreview != null &&
                    it.resultPreview != null &&
                    it.resultPreview !== supersededResult &&
                    supersededResult.isRecycled
            }
            assertEquals("The latest brightness value must remain selected", targetBrightness, finalState.brightness)
            assertEquals("The latest contrast value must remain selected", targetContrast, finalState.contrastPercent)
            assertEquals("The output preview must preserve its enhanced base width", finalState.enhancedPreview!!.width, finalState.resultPreview!!.width)
            assertEquals("The output preview must preserve its enhanced base height", finalState.enhancedPreview!!.height, finalState.resultPreview!!.height)
        } finally {
            viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }


    @Test
    fun rapidCropCornerChangesRenderLatestPreviewWithoutBacklog() {
        assertTrue("OpenCV native library must load for coalesced crop preview tests", OpenCVLoader.initLocal())

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoViewModel::class.java]
        val sourceFile = File(app.cacheDir, "coalesced-crop-preview-" + System.nanoTime() + ".png")
        val source = Bitmap.createBitmap(220, 170, Bitmap.Config.ARGB_8888)

        fun awaitState(
            description: String,
            timeoutMillis: Long = 30_000,
            predicate: (PhotoUiState) -> Boolean
        ): PhotoUiState {
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            while (SystemClock.uptimeMillis() < deadline) {
                val state = viewModel.state.value
                if (state != null && predicate(state)) return state
                SystemClock.sleep(50)
            }
            throw AssertionError("Timed out waiting for coalesced crop preview state: " + description)
        }

        try {
            source.eraseColor(Color.rgb(232, 232, 226))
            Canvas(source).apply {
                val paint = Paint().apply {
                    color = Color.DKGRAY
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                drawRect(4f, 4f, source.width - 5f, source.height - 5f, paint)
                drawLine(12f, 12f, source.width - 12f, source.height - 12f, paint)
            }
            FileOutputStream(sourceFile).use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

            viewModel.selectImages(listOf(Uri.fromFile(sourceFile)))
            viewModel.openCropEditor(openCvReady = true)
            val initial = awaitState("initial crop preview") {
                it.cropOpen && !it.cropApplying && it.cropSource != null &&
                    it.cropCorners.size == 4 && it.cropPreview != null
            }
            val sourceBitmap = initial.cropSource!!
            val firstCorner = initial.cropCorners.first()
            val initialPreview = initial.cropPreview
                ?: throw AssertionError("Initial crop preview must be available")

            // Emulate a fast pointer gesture with many intermediate positions.
            repeat(60) { index ->
                val offset = (index % 12 + 1).toFloat()
                viewModel.moveCropCorner(0, firstCorner.x + offset, firstCorner.y + offset)
            }
            val targetCorner = CropPoint(firstCorner.x + 7f, firstCorner.y + 7f)
            viewModel.moveCropCorner(0, targetCorner.x, targetCorner.y)

            val finalState = awaitState("latest corner position is previewed and replaced bitmap released", 45_000) {
                it.cropOpen && !it.cropApplying &&
                    it.cropCorners.firstOrNull() == targetCorner &&
                    it.cropPreview != null && it.cropPreview !== initialPreview &&
                    initialPreview.isRecycled
            }
            assertEquals("The latest crop corner must be retained", targetCorner, finalState.cropCorners.first())
            assertTrue("The preview must remain bounded by the configured side limit",
                maxOf(finalState.cropPreview!!.width, finalState.cropPreview!!.height) <= Processor.PREVIEW_MAX_SIDE)
            assertTrue("The crop source must remain valid while editing", !sourceBitmap.isRecycled)
        } finally {
            viewModelStore.clear()
            if (!source.isRecycled) source.recycle()
            sourceFile.delete()
        }
    }



    @Test
    fun warpRejectsInvalidPreviewLimitWithoutRecyclingCallerBitmap() {
        assertTrue("OpenCV native library must load for crop resource tests", OpenCVLoader.initLocal())

        val source = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.LTGRAY)
            var rejected = false
            try {
                DocumentCrop.warp(
                    source,
                    DocumentCrop.fullFrame(source.width, source.height),
                    maxOutputSide = 1
                )
            } catch (_: IllegalArgumentException) {
                rejected = true
            }

            assertTrue("An invalid preview size must be rejected", rejected)
            assertTrue("A failed crop request must not recycle the caller-owned source", !source.isRecycled)
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }



    @Test
    fun processingMultipleImagesKeepsOutputsValidAfterIntermediateBitmapRelease() {
        assertTrue("OpenCV native library must load for processing lifecycle tests", OpenCVLoader.initLocal())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inputFiles = mutableListOf<File>()
        val outputFiles = mutableListOf<File>()
        val previews = mutableListOf<Bitmap>()
        val errors = mutableListOf<String>()
        var finishedSuccess = -1
        var finishedFailed = -1
        var finishedError: String? = null

        try {
            repeat(2) { index ->
                val inputFile = File(context.cacheDir, "bitmap-release-input-\${System.nanoTime()}-$index.png")
                val source = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
                try {
                    source.eraseColor(Color.rgb(210 - index * 35, 220 - index * 25, 230 - index * 30))
                    Canvas(source).apply {
                        val paint = Paint().apply {
                            color = if (index == 0) Color.DKGRAY else Color.WHITE
                            strokeWidth = 4f
                            style = Paint.Style.STROKE
                        }
                        drawRect(8f, 8f, source.width - 9f, source.height - 9f, paint)
                        drawLine(15f, source.height / 2f, source.width - 15f, source.height / 2f, paint)
                    }
                    FileOutputStream(inputFile).use { output ->
                        assertTrue("Synthetic input image must be written", source.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                } finally {
                    if (!source.isRecycled) source.recycle()
                }
                inputFiles += inputFile
            }

            val observer = object : PhotoRepository.Observer {
                override fun onSource(index: Int, total: Int, bitmap: Bitmap) {
                    previews += bitmap
                }

                override fun onClassification(index: Int, total: Int, kind: ImageKind) = Unit

                override fun onEnhanced(bitmap: Bitmap) {
                    previews += bitmap
                }

                override fun onSaved(index: Int, bitmap: Bitmap, file: File) {
                    previews += bitmap
                    outputFiles += file
                }

                override fun onError(index: Int, total: Int, uri: Uri, message: String) {
                    errors += "Image $index: $message"
                }

                override fun onFinished(success: Int, failed: Int, error: String?) {
                    finishedSuccess = success
                    finishedFailed = failed
                    finishedError = error
                }
            }

            PhotoRepository().process(
                context = context,
                uris = inputFiles.map { Uri.fromFile(it) },
                brightness = 0,
                contrastPercent = 100,
                observer = observer
            )

            assertEquals("Both images must finish successfully. Errors: $errors; last error: $finishedError", 2, finishedSuccess)
            assertEquals("No image in the batch should fail. Errors: $errors", 0, finishedFailed)
            assertEquals(null, finishedError)
            assertEquals("Each image should publish source, enhanced and saved previews", 6, previews.size)
            assertEquals("Each image should produce one retained temporary output", 2, outputFiles.size)

            outputFiles.forEachIndexed { index, file ->
                assertTrue("Output file $index must exist after its callback", file.isFile && file.length() > 0)
                val decoded = BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw AssertionError("Output file $index must remain a readable image")
                try {
                    assertTrue("Output $index must have positive dimensions", decoded.width > 0 && decoded.height > 0)
                    val savedPreview = previews[index * 3 + 2]
                    assertEquals("Saved preview width must match the exported output", savedPreview.width, decoded.width)
                    assertEquals("Saved preview height must match the exported output", savedPreview.height, decoded.height)
                } finally {
                    if (!decoded.isRecycled) decoded.recycle()
                }
            }
            previews.forEachIndexed { index, bitmap ->
                assertTrue("Published preview $index must remain usable after processing", !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
            }
        } finally {
            previews.forEach { if (!it.isRecycled) it.recycle() }
            inputFiles.forEach { it.delete() }
            outputFiles.forEach { it.delete() }
        }
    }

}
