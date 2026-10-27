package com.photounifier

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photounifier.ui.PhotoUnifierTheme
import com.photounifier.ui.screens.CropWorkspaceBottomBar
import com.photounifier.ui.screens.CropWorkspaceView
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LandscapeCompactWorkflowLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cropWorkspaceAndPersistentActionsFitCompactLandscapeViewport() {
        val source = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.LTGRAY) }
        val state = PhotoUiState(
            selected = listOf(android.net.Uri.parse("content://photo-unifier/landscape-crop-test")),
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
                        .size(width = 430.dp, height = 320.dp)
                        .testTag("landscape-crop-viewport")
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
                                .testTag("landscape-crop-workspace")
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
        saveScreenshot("landscape-crop-viewport", "crop-workspace-landscape.png")
        val viewport = bounds("landscape-crop-viewport")
        val workspace = bounds("landscape-crop-workspace")
        val cancelAction = boundsForDescription("إلغاء القص")
        val finishAction = boundsForText("إنهاء")

        assertInsideViewport("Landscape crop workspace", workspace, viewport)
        assertInsideViewport("Landscape crop cancel action", cancelAction, viewport)
        assertInsideViewport("Landscape crop finish action", finishAction, viewport)
        assertTrue(
            "Landscape crop actions must remain below the workspace without overlap",
            workspace.bottom <= cancelAction.top + 1f
        )
        assertTrue(
            "Landscape crop finish action must remain visible above the viewport edge",
            finishAction.bottom <= viewport.bottom + 1f
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

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun boundsForText(text: String): Rect =
        composeRule.onNodeWithText(text, useUnmergedTree = true)
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
