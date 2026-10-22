package com.photounifier

import android.graphics.Bitmap
import android.graphics.Color
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
}
