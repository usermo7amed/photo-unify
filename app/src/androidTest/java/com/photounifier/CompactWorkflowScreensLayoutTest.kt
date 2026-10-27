package com.photounifier

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.photounifier.ui.PhotoUnifierTheme
import com.photounifier.ui.screens.AdjustPreviewScreen
import com.photounifier.ui.screens.CropReviewCard
import com.photounifier.ui.screens.CropWorkspaceBottomBar
import com.photounifier.ui.screens.CropWorkspaceView
import com.photounifier.ui.screens.HomeScreen
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CompactWorkflowScreensLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedHomeScreenKeepsPreviewAndSelectionControlsInsideCompactViewport() {
        val source = testBitmap()
        val uri = Uri.parse("content://photo-unifier/home-test")
        val state = PhotoUiState(selected = listOf(uri), sourcePreview = source)

        composeRule.setContent {
            DisposableEffect(source) {
                onDispose { if (!source.isRecycled) source.recycle() }
            }
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("home-viewport")
                ) {
                    HomeScreen(
                        state = state,
                        onCaptureDocument = {},
                        onClearSelection = {},
                        onRemoveImage = {},
                        onPreviewSelect = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("home-viewport", "home-selected.png")
        val viewport = bounds("home-viewport")
        val clearAction = boundsForText("مسح الكل")
        val preview = boundsForDescription("معاينة الصفحة 1")

        assertInsideViewport("Home clear-selection action", clearAction, viewport)
        assertInsideViewport("Home selected-image preview", preview, viewport)
        assertTrue(
            "The home header action must remain above, not overlap, the image preview",
            clearAction.bottom <= preview.top + 1f
        )
    }

    @Test
    fun adjustmentScreenKeepsComparisonAndPrimaryActionVisibleOnCompactViewport() {
        val source = testBitmap()
        val enhanced = testBitmap(Color.WHITE)
        val uri = Uri.parse("content://photo-unifier/adjust-test")
        val state = PhotoUiState(
            selected = listOf(uri),
            sourcePreview = source,
            enhancedPreview = enhanced
        )

        composeRule.setContent {
            DisposableEffect(source, enhanced) {
                onDispose {
                    if (!source.isRecycled) source.recycle()
                    if (!enhanced.isRecycled) enhanced.recycle()
                }
            }
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("adjust-viewport")
                ) {
                    AdjustPreviewScreen(
                        state = state,
                        openCvReady = true,
                        onPreviewSelect = {},
                        onBrightnessChange = {},
                        onContrastChange = {},
                        onProcess = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("adjust-viewport", "adjustment.png")
        val viewport = bounds("adjust-viewport")
        val afterLabel = boundsForText("بعد (المحسّنة)")
        val saveAction = boundsForText("حفظ وتصدير المستندات", substring = true)

        assertInsideViewport("Adjustment comparison label", afterLabel, viewport)
        assertInsideViewport("Adjustment primary save action", saveAction, viewport)
        assertTrue(
            "The comparison preview must not overlap the bottom save action",
            afterLabel.bottom <= saveAction.top + 1f
        )
    }

    @Test
    fun cropEditorAndToolbarStayInsideCompactViewportWithoutOverlap() {
        val source = testBitmap()
        val uri = Uri.parse("content://photo-unifier/crop-test")
        val state = PhotoUiState(
            selected = listOf(uri),
            cropOpen = true,
            cropApplied = true,
            cropSource = source,
            cropPreview = source,
            cropCorners = listOf(
                CropPoint(12f, 12f),
                CropPoint(148f, 12f),
                CropPoint(148f, 228f),
                CropPoint(12f, 228f)
            ),
            cropDirty = false
        )

        composeRule.setContent {
            DisposableEffect(source) {
                onDispose { if (!source.isRecycled) source.recycle() }
            }
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("crop-viewport")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CropWorkspaceView(
                            state = state,
                            onMoveCropCorner = { _, _, _ -> },
                            onSelectPage = {},
                            onEnsurePageThumbnail = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("crop-workspace")
                        )
                        CropWorkspaceBottomBar(
                            state = state,
                            onRotatePage = {},
                            onUndoCrop = {},
                            onResetCrop = {},
                            onCancelCrop = {},
                            onApplyCrop = {},
                            onFinishCrop = {},
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("crop-viewport", "crop-workspace.png")
        val viewport = bounds("crop-viewport")
        val workspace = bounds("crop-workspace")
        val cancelAction = boundsForDescription("إلغاء القص")
        val finishAction = boundsForText("إنهاء")

        assertInsideViewport("Crop editor workspace", workspace, viewport)
        assertInsideViewport("Crop cancel action", cancelAction, viewport)
        assertInsideViewport("Crop finish action", finishAction, viewport)
        assertTrue(
            "Crop controls must be below the image-editing workspace",
            workspace.bottom <= cancelAction.top + 1f
        )
    }

    @Test
    fun emptyHomeStateKeepsGuidanceInsideCompactViewport() {
        composeRule.setContent {
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("empty-home-viewport")
                ) {
                    HomeScreen(
                        state = PhotoUiState(),
                        onCaptureDocument = {},
                        onClearSelection = {},
                        onRemoveImage = {},
                        onPreviewSelect = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("empty-home-viewport", "home-empty.png")
        val viewport = bounds("empty-home-viewport")
        val title = boundsForText("ابدأ بمسح مستند")
        val guidance = boundsForText("صوّر الورقة بالكاميرا أو اختر صورًا من المعرض.")

        assertInsideViewport("Empty home title", title, viewport)
        assertInsideViewport("Empty home guidance", guidance, viewport)
        assertTrue("Empty home guidance must be below the title", title.bottom <= guidance.top + 1f)
    }

    @Test
    fun cropReviewKeepsSourcePreviewAndCropActionInsideCompactViewport() {
        val source = testBitmap()
        val uri = Uri.parse("content://photo-unifier/crop-review-test")
        val state = PhotoUiState(
            selected = listOf(uri),
            sourcePreview = source
        )

        composeRule.setContent {
            DisposableEffect(source) {
                onDispose { if (!source.isRecycled) source.recycle() }
            }
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("crop-review-viewport")
                ) {
                    CropReviewCard(
                        state = state,
                        openCvReady = true,
                        onOpenCrop = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("crop-review-viewport", "crop-review.png")
        val viewport = bounds("crop-review-viewport")
        val preview = boundsForDescription("معاينة الصفحة 1")
        val cropAction = boundsForText("ضبط الزوايا")

        assertInsideViewport("Crop-review source preview", preview, viewport)
        assertInsideViewport("Crop-review primary action", cropAction, viewport)
        assertTrue(
            "Crop-review action must remain below the source preview",
            preview.bottom <= cropAction.top + 1f
        )
    }

    @Test
    fun processingResultKeepsProgressAndStatusInsideCompactViewport() {
        composeRule.setContent {
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("processing-result-viewport")
                ) {
                    com.photounifier.ui.screens.ResultScreen(
                        state = PhotoUiState(
                            busy = true,
                            progress = 1,
                            progressMax = 3,
                            status = "جاري معالجة الصور"
                        ),
                        onBackToAdjust = {},
                        onPickNewBatch = {},
                        onToggleExportImage = { _, _ -> },
                        onSelectAllExportImages = {},
                        onExportImages = {},
                        onExportPdf = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("processing-result-viewport", "result-processing.png")
        val viewport = bounds("processing-result-viewport")
        val title = boundsForText("جاري تحسين وحفظ المستندات...")
        val progress = boundsForText("المستند 1 من 3")
        val status = boundsForText("جاري معالجة الصور")

        assertInsideViewport("Processing title", title, viewport)
        assertInsideViewport("Processing progress", progress, viewport)
        assertInsideViewport("Processing status", status, viewport)
        assertTrue(
            "Progress should remain below the processing status",
            status.bottom <= progress.top + 1f
        )
    }

    private fun saveScreenshot(tag: String, fileName: String) {
        val image = composeRule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage()
        val bitmap = image.asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val externalFiles = context.getExternalFilesDir(null)
            ?: throw AssertionError("App-specific external files directory is unavailable for visual artifacts")
        val outputDirectory = File(externalFiles, "visual-audit")
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Cannot create emulator screenshot directory: ${outputDirectory.absolutePath}"
        }
        File(outputDirectory, fileName).outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Cannot encode emulator screenshot: $fileName"
            }
        }
    }

    private fun testBitmap(color: Int = Color.LTGRAY): Bitmap =
        Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun boundsForText(text: String, substring: Boolean = false): Rect =
        composeRule.onNodeWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun boundsForDescription(description: String): Rect =
        composeRule.onNodeWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun assertInsideViewport(label: String, child: Rect, viewport: Rect) {
        assertTrue("$label must not extend left of viewport: $child / $viewport", child.left >= viewport.left - 1f)
        assertTrue("$label must not extend right of viewport: $child / $viewport", child.right <= viewport.right + 1f)
        assertTrue("$label must not extend above viewport: $child / $viewport", child.top >= viewport.top - 1f)
        assertTrue("$label must not extend below viewport: $child / $viewport", child.bottom <= viewport.bottom + 1f)
    }
}
