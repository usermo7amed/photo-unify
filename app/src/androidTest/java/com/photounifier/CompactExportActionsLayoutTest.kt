package com.photounifier

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.photounifier.ui.PhotoUnifierTheme
import com.photounifier.ui.screens.ResultScreen
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CompactExportActionsLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportActionsAndPreviewStayVisibleWithLongListOnCompactScreen() {
        val preview = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.LTGRAY)
        }
        val images = List(50) { index ->
            ExportImage(
                id = "page-$index",
                sourceUri = android.net.Uri.parse("content://photo-unifier/page/$index"),
                file = File("page-$index.jpg"),
                displayName = "Page ${index + 1}.jpg"
            )
        }
        val state = PhotoUiState(
            savedCount = images.size,
            processedImages = images,
            selectedExportIds = images.map { it.id }.toSet(),
            resultPreview = preview,
            exportStatus = "جاهز للتصدير"
        )

        composeRule.setContent {
            DisposableEffect(preview) {
                onDispose {
                    if (!preview.isRecycled) preview.recycle()
                }
            }
            PhotoUnifierTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 430.dp)
                        .testTag("compact-viewport")
                ) {
                    ResultScreen(
                        state = state,
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
        val viewport = composeRule.onNodeWithTag(
            "compact-viewport",
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot

        val pdfButton = composeRule.onNodeWithText(
            "تصدير PDF",
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val imageButton = composeRule.onNodeWithText(
            "حفظ كصور",
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val documentPreview = composeRule.onNodeWithContentDescription(
            "المستند النهائي",
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot

        assertInsideViewport("PDF export action", pdfButton, viewport)
        assertInsideViewport("Image export action", imageButton, viewport)
        assertInsideViewport("Final document preview", documentPreview, viewport)
        assertTrue(
            "The final preview must not overlap the primary export actions",
            documentPreview.bottom <= minOf(pdfButton.top, imageButton.top) + 1f
        )

        // The list has far more entries than fit in the viewport, but its primary actions
        // remain composed and reachable without scrolling past the page rows.
        composeRule.onNodeWithText("Page 50.jpg", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertInsideViewport(label: String, child: Rect, viewport: Rect) {
        assertTrue("$label must not extend left of the viewport: $child / $viewport", child.left >= viewport.left - 1f)
        assertTrue("$label must not extend right of the viewport: $child / $viewport", child.right <= viewport.right + 1f)
        assertTrue("$label must not extend above the viewport: $child / $viewport", child.top >= viewport.top - 1f)
        assertTrue("$label must not extend below the viewport: $child / $viewport", child.bottom <= viewport.bottom + 1f)
    }
}
