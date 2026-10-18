package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCropTest {
    @Test fun acceptsOrderedRectangle() {
        assertTrue(DocumentCrop.isValid(
            listOf(CropPoint(10f, 10f), CropPoint(90f, 10f), CropPoint(90f, 90f), CropPoint(10f, 90f)),
            100, 100
        ))
    }

    @Test fun rejectsCrossedCorners() {
        assertFalse(DocumentCrop.isValid(
            listOf(CropPoint(10f, 10f), CropPoint(90f, 90f), CropPoint(90f, 10f), CropPoint(10f, 90f)),
            100, 100
        ))
    }

    @Test fun rejectsPointsOutsideImage() {
        assertFalse(DocumentCrop.isValid(
            listOf(CropPoint(-1f, 10f), CropPoint(90f, 10f), CropPoint(90f, 90f), CropPoint(10f, 90f)),
            100, 100
        ))
    }

    @Test fun rejectsDegenerateQuadrilateral() {
        assertFalse(DocumentCrop.isValid(
            listOf(CropPoint(10f, 10f), CropPoint(90f, 10f), CropPoint(50f, 10.5f), CropPoint(10f, 11f)),
            100, 100
        ))
    }

    @Test fun rejectsConcaveQuadrilateral() {
        assertFalse(DocumentCrop.isValid(
            listOf(CropPoint(10f, 10f), CropPoint(90f, 10f), CropPoint(40f, 40f), CropPoint(10f, 90f)),
            100, 100
        ))
    }

    @Test fun acceptsPerspectivePageAwayFromPhotoBorders() {
        assertTrue(DocumentCrop.isPlausibleDetection(
            listOf(
                CropPoint(15f, 10f),
                CropPoint(83f, 17f),
                CropPoint(91f, 85f),
                CropPoint(10f, 92f)
            ),
            100,
            100
        ))
    }

    @Test fun rejectsLargeQuadrilateralThatFollowsPhotoFrame() {
        assertFalse(DocumentCrop.isPlausibleDetection(
            listOf(
                CropPoint(1f, 1f),
                CropPoint(98f, 1f),
                CropPoint(98f, 98f),
                CropPoint(1f, 98f)
            ),
            100,
            100
        ))
    }

    @Test fun rejectsLargeCandidateWithThreeCornersOnPhotoFrame() {
        // When most of the candidate hugs the image border, the photo frame can
        // masquerade as paper. Prefer manual adjustment when that ambiguity is high.
        assertFalse(DocumentCrop.isPlausibleDetection(
            listOf(
                CropPoint(1f, 1f),
                CropPoint(98f, 1f),
                CropPoint(98f, 98f),
                CropPoint(40f, 90f)
            ),
            100,
            100
        ))
    }

    @Test fun rejectsQuadrilateralThatIsTooSmallToBeAPage() {
        assertFalse(DocumentCrop.isPlausibleDetection(
            listOf(
                CropPoint(40f, 40f),
                CropPoint(60f, 40f),
                CropPoint(60f, 60f),
                CropPoint(40f, 60f)
            ),
            100,
            100
        ))
    }

    @Test fun mapsReducedResolutionCornersBackToOriginalPixelBounds() {
        val sourcePoints = DocumentCrop.scalePointsToSource(
            listOf(
                CropPoint(0f, 0f),
                CropPoint(639f, 0f),
                CropPoint(639f, 479f),
                CropPoint(0f, 479f)
            ),
            sourceWidth = 2560,
            sourceHeight = 1920,
            analysisWidth = 640,
            analysisHeight = 480
        )

        assertEquals(CropPoint(0f, 0f), sourcePoints[0])
        assertEquals(CropPoint(2559f, 1919f), sourcePoints[2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensionsWhenScalingCoordinates() {
        DocumentCrop.scalePointsToSource(
            listOf(CropPoint(0f, 0f)), 0, 100, 10, 10
        )
    }
}
