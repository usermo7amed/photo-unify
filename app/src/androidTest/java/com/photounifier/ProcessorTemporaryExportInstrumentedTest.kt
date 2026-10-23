package com.photounifier

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessorTemporaryExportInstrumentedTest {
    @Test
    fun failedReplacementPreservesPreviouslyCompletedOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "pending_exports")
        assertTrue("Pending export directory should be creatable", directory.mkdirs() || directory.isDirectory)

        val displayName = "PU_replace-failure-${System.nanoTime()}.jpg"
        val target = File(directory, displayName)
        val partial = File(directory, "$displayName.part")
        val previousContents = byteArrayOf(12, 34, 56, 78)
        assertTrue("Previous output should be written", target.writeBytes(previousContents).let { target.isFile })

        val recycledBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { recycle() }
        try {
            try {
                Processor.saveTemporary(context, recycledBitmap, displayName)
                fail("Saving a recycled bitmap should fail")
            } catch (_: IllegalStateException) {
                // Expected: a failed replacement must not remove the existing output.
            }

            assertArrayEquals(
                "Previous completed output must survive a failed replacement",
                previousContents,
                target.readBytes()
            )
            assertFalse("Incomplete .part file must be removed", partial.exists())
        } finally {
            target.delete()
            partial.delete()
        }
    }

    @Test
    fun successfulReplacementPublishesCompleteJpegAndRemovesPartialFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "pending_exports")
        assertTrue("Pending export directory should be creatable", directory.mkdirs() || directory.isDirectory)

        val displayName = "PU_replace-success-${System.nanoTime()}.jpg"
        val target = File(directory, displayName)
        val partial = File(directory, "$displayName.part")
        assertTrue("Previous output should be written", target.writeBytes(byteArrayOf(1, 2, 3)).let { target.isFile })
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

        try {
            val saved = Processor.saveTemporary(context, bitmap, displayName)

            assertEquals(target, saved)
            val contents = target.readBytes()
            assertTrue("Saved JPEG should not be empty", contents.size > 4)
            assertEquals("JPEG should begin with the SOI marker", 0xFF, contents[0].toInt() and 0xFF)
            assertEquals("JPEG should begin with the SOI marker", 0xD8, contents[1].toInt() and 0xFF)
            assertFalse("Committed output must not leave a .part file", partial.exists())
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            target.delete()
            partial.delete()
        }
    }
}
