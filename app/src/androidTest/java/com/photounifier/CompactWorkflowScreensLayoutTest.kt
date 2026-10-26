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
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.weight
import com.photounifier.ui.PhotoUnifierTheme
import com.photounifier.ui.screens.AdjustPreviewScreen
import com.photounifier.ui.screens.CropWorkspaceBottomBar
import com.photounifier.ui.screens.CropWorkspaceView
import com.photounifier.ui.screens.HomeScreen
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
