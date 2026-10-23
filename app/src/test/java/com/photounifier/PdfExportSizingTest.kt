package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfExportSizingTest {
    @Test
    fun keepsModerateImagesAtFullResolution() {
        assertEquals(1, pdfExportSampleSize(1600, 2400))
        assertEquals(1, pdfExportSampleSize(4000, 3000))
    }

    @Test
    fun downsamplesVeryLargeImagesForPdfMemorySafety() {
        assertEquals(2, pdfExportSampleSize(8000, 6000))
        assertEquals(4, pdfExportSampleSize(14032, 10000))
        assertEquals(8, pdfExportSampleSize(30000, 20000))
    }

    @Test
    fun usesLongestEdgeAndSupportsCustomTarget() {
        assertEquals(4, pdfExportSampleSize(2000, 8000, maxSide = 2000))
        assertEquals(1, pdfExportSampleSize(1000, 1000, maxSide = 2000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() {
        pdfExportSampleSize(0, 1200)
    }
}
