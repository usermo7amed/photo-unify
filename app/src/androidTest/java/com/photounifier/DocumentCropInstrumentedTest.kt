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


}
