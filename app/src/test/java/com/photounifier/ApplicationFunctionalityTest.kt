package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وظائف التطبيق وحسابات المعالجة والحالات
 */
class ApplicationFunctionalityTest {

    @Test
    fun testPhotoUiStateDefaults() {
        val state = PhotoUiState()
        assertTrue(state.selected.isEmpty())
        assertTrue(state.selectionThumbnails.isEmpty())
        assertEquals(0, state.previewIndex)
        assertFalse(state.busy)
        assertEquals(0, state.progress)
        assertEquals(1, state.progressMax)
        assertEquals(0, state.savedCount)
        assertEquals("", state.status)
        assertEquals("", state.exportStatus)
        assertTrue(state.errors.isEmpty())
        assertTrue(state.processedImages.isEmpty())
        assertTrue(state.selectedExportIds.isEmpty())
        assertFalse(state.exportBusy)
        assertEquals(0, state.brightness)
        assertEquals(100, state.contrastPercent)
        assertNull(state.sourcePreview)
        assertNull(state.enhancedPreview)
        assertNull(state.resultPreview)
        assertFalse(state.cropOpen)
        assertFalse(state.cropApplied)
        assertFalse(state.cropDirty)
        assertEquals(0, state.cropRotationDegrees)
    }

    @Test
    fun testPhotoUiStateCopyPreservesImmutability() {
        val original = PhotoUiState(
            progress = 3,
            progressMax = 10,
            brightness = 15,
            contrastPercent = 120,
            status = "جاري المعالجة"
        )
        val modified = original.copy(
            brightness = -20,
            status = "تم التعديل"
        )

        assertEquals(3, original.progress)
        assertEquals(15, original.brightness)
        assertEquals("جاري المعالجة", original.status)

        assertEquals(3, modified.progress)
        assertEquals(-20, modified.brightness)
        assertEquals("تم التعديل", modified.status)
        assertEquals(120, modified.contrastPercent)
    }

    @Test
    fun testFullFrameCornersGeneration() {
        val width = 1920
        val height = 1080
        val corners = DocumentCrop.fullFrame(width, height)

        assertEquals(4, corners.size)
        // Top-left
        assertEquals(CropPoint(0f, 0f), corners[0])
        // Top-right
        assertEquals(CropPoint(1919f, 0f), corners[1])
        // Bottom-right
        assertEquals(CropPoint(1919f, 1079f), corners[2])
        // Bottom-left
        assertEquals(CropPoint(0f, 1079f), corners[3])
    }

    @Test
    fun testNormalizeCropPointsBounds() {
        val width = 1000
        val height = 500
        val points = listOf(
            CropPoint(0f, 0f),
            CropPoint(999f, 0f),
            CropPoint(999f, 499f),
            CropPoint(0f, 499f)
        )

        val normalized = normalizeCropPoints(points, width, height)
        assertEquals(4, normalized.size)

        assertEquals(0f, normalized[0].x, 0.001f)
        assertEquals(0f, normalized[0].y, 0.001f)

        assertEquals(1f, normalized[1].x, 0.001f)
        assertEquals(0f, normalized[1].y, 0.001f)

        assertEquals(1f, normalized[2].x, 0.001f)
        assertEquals(1f, normalized[2].y, 0.001f)

        assertEquals(0f, normalized[3].x, 0.001f)
        assertEquals(1f, normalized[3].y, 0.001f)
    }

    @Test
    fun testNormalizeAndMapNormalizedPointsRoundTrip() {
        val width = 2400
        val height = 1800
        val originalPoints = listOf(
            CropPoint(120f, 150f),
            CropPoint(2200f, 180f),
            CropPoint(2150f, 1650f),
            CropPoint(140f, 1700f)
        )

        val normalized = normalizeCropPoints(originalPoints, width, height)
        val mappedBack = mapNormalizedCropPoints(normalized, width, height)

        assertEquals(originalPoints.size, mappedBack.size)
        for (i in originalPoints.indices) {
            assertEquals(originalPoints[i].x, mappedBack[i].x, 0.5f)
            assertEquals(originalPoints[i].y, mappedBack[i].y, 0.5f)
        }
    }

    @Test
    fun testDocumentPageEditDefaults() {
        val edit = DocumentPageEdit()
        assertEquals(0, edit.rotationDegrees)
        assertNull(edit.normalizedCorners)

        val customCorners = listOf(CropPoint(0.1f, 0.1f), CropPoint(0.9f, 0.9f))
        val modified = DocumentPageEdit(rotationDegrees = 90, normalizedCorners = customCorners)
        assertEquals(90, modified.rotationDegrees)
        assertEquals(customCorners, modified.normalizedCorners)
    }

    @Test
    fun testSampleSizeCalculation() {
        // اختبار صورة مربعة
        val squareSample = Processor.calculateSampleSize(2000, 2000, 1000)
        assertEquals(2, squareSample)

        // اختبار صورة عالية الدقة 8K
        val highResSample = Processor.calculateSampleSize(7680, 4320, 1280)
        assertEquals(8, highResSample)

        // اختبار صورة صغيرة لا تحتاج تصغير
        val smallSample = Processor.calculateSampleSize(640, 480, 1280)
        assertEquals(1, smallSample)
    }

    @Test
    fun testExportRetrySelectionLogic() {
        // تحديد كل الصور وتصدير بعضها بنجاح
        val allIds = setOf("img_1.jpg", "img_2.jpg", "img_3.jpg")
        val successIds = setOf("img_1.jpg", "img_3.jpg")

        val remaining = remainingExportIdsAfterExport(
            format = ExportFormat.IMAGES,
            selectedIds = allIds,
            successfulImageIds = successIds,
            failedCount = 1
        )

        assertEquals(setOf("img_2.jpg"), remaining)
    }

    @Test
    fun testContrastCoercingRange() {
        fun computeContrastScale(contrast: Float): Float = contrast.coerceIn(0.5f, 2.0f)

        assertEquals(0.5f, computeContrastScale(0.1f), 0.001f)
        assertEquals(0.5f, computeContrastScale(0.5f), 0.001f)
        assertEquals(1.0f, computeContrastScale(1.0f), 0.001f)
        assertEquals(1.75f, computeContrastScale(1.75f), 0.001f)
        assertEquals(2.0f, computeContrastScale(2.0f), 0.001f)
        assertEquals(2.0f, computeContrastScale(3.5f), 0.001f)
    }
}
