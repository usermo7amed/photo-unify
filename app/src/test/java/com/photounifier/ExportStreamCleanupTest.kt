package com.photounifier

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportStreamCleanupTest {
    @Test
    fun destinationStreamClosesWhenTemporarySourceCannotBeOpened() {
        val missingFile = File(
            System.getProperty("java.io.tmpdir"),
            "photo-unifier-missing-${System.nanoTime()}.jpg"
        )
        assertFalse("Test source must not already exist", missingFile.exists())
        val output = TrackingOutputStream()

        try {
            copyTemporaryImageToOutput(missingFile, output)
            org.junit.Assert.fail("Opening a missing source should fail")
        } catch (_: FileNotFoundException) {
            // Expected: the destination still must be closed on this failure path.
        }

        assertTrue("Destination stream must close if opening the source fails", output.wasClosed)
    }

    @Test
    fun destinationStreamClosesAfterSuccessfulCopy() {
        val source = File.createTempFile("photo-unifier-export-", ".jpg")
        val expected = byteArrayOf(0, 1, 2, 3, 10, 20, 30)
        val output = TrackingOutputStream()

        try {
            source.writeBytes(expected)
            copyTemporaryImageToOutput(source, output)

            assertArrayEquals(expected, output.toByteArray())
            assertTrue("Destination stream must close after copying", output.wasClosed)
        } finally {
            source.delete()
        }
    }

    @Test
    fun observerFailureIsReportedWithoutEscapingIntoExportFlow() {
        val expected = IllegalStateException("UI observer failed")
        var reported: Exception? = null
        var continued = false

        safelyNotifyExportObserver(
            callback = { throw expected },
            onFailure = { reported = it }
        )
        continued = true

        assertTrue("Export flow should continue after an observer failure", continued)
        assertTrue("The original observer exception should be reported", reported === expected)
    }

    @Test
    fun successfulObserverCallbackDoesNotReportFailure() {
        var reported = false
        var invoked = false

        safelyNotifyExportObserver(
            callback = { invoked = true },
            onFailure = { reported = true }
        )

        assertTrue("Observer callback should run", invoked)
        assertFalse("Successful observer callbacks should not report errors", reported)
    }

    @Test
    fun processingObserverFailureIsReportedWithoutEscapingIntoBatch() {
        val expected = IllegalStateException("UI observer failed")
        var reported: Exception? = null
        var nextImageProcessed = false

        safelyNotifyProcessingObserver(
            callback = { throw expected },
            onFailure = { reported = it }
        )
        nextImageProcessed = true

        assertTrue("The batch should continue after an observer failure", nextImageProcessed)
        assertTrue("The original observer exception should be reported", reported === expected)
    }

    @Test
    fun successfulProcessingObserverDoesNotReportFailure() {
        var reported = false
        var invoked = false

        safelyNotifyProcessingObserver(
            callback = { invoked = true },
            onFailure = { reported = true }
        )

        assertTrue("Observer callback should run", invoked)
        assertFalse("Successful observer callbacks should not report errors", reported)
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var wasClosed = false
            private set

        override fun close() {
            wasClosed = true
            super.close()
        }
    }
}
