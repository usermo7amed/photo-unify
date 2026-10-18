package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessorSampleSizeTest {
    @Test
    fun largeLandscapePreviewIsDownsampledToBoundedSize() {
        val sample = Processor.calculateSampleSize(6000, 4000, 1280)
        assertEquals(8, sample)
        assertTrue(6000 / sample <= 1280)
    }

    @Test
    fun portraitPreviewUsesLongestSideForSampling() {
        val sample = Processor.calculateSampleSize(3000, 5000, 1000)
        assertEquals(8, sample)
        assertTrue(5000 / sample <= 1000)
    }

    @Test
    fun smallImagesAreNotUpscaledDuringDecode() {
        assertEquals(1, Processor.calculateSampleSize(800, 600, 1280))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() {
        Processor.calculateSampleSize(0, 600, 1280)
    }
}
