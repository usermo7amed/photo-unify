package com.photounifier

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingExportCleanupInstrumentedTest {

    @Test
    fun cleanupRemovesOnlyFilesInThePrivatePendingExportDirectory() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Isolate this test from pending files owned by other instrumentation tests.
        val isolatedRoot = File(targetContext.cacheDir, "pending-cleanup-test-${System.nanoTime()}")
        assertTrue("Isolated test directory should be creatable", isolatedRoot.mkdirs())
        val context = object : ContextWrapper(targetContext) {
            override fun getFilesDir(): File = isolatedRoot
        }
        val pendingDirectory = File(context.filesDir, "pending_exports")
        assertTrue("Pending export directory should be creatable", pendingDirectory.mkdirs() || pendingDirectory.isDirectory)

        val completedOutput = File(pendingDirectory, "PU_orphaned-output.jpg")
        val interruptedOutput = File(pendingDirectory, "PU_interrupted-output.jpg.part")
        val nestedDirectory = File(pendingDirectory, "nested")
        val nestedFile = File(nestedDirectory, "leave-alone.txt")
        val unrelatedFile = File(context.filesDir, "pending-export-cleanup-test.txt")

        try {
            assertTrue("Completed temporary output should be created", completedOutput.writeText("temporary output").let { completedOutput.isFile })
            assertTrue("Interrupted temporary output should be created", interruptedOutput.writeText("partial output").let { interruptedOutput.isFile })
            assertTrue("Nested directory should be created", nestedDirectory.mkdirs() || nestedDirectory.isDirectory)
            assertTrue("Nested test file should be created", nestedFile.writeText("nested").let { nestedFile.isFile })
            assertTrue("Unrelated app-private file should be created", unrelatedFile.writeText("keep").let { unrelatedFile.isFile })

            val removed = PendingExportCleanup.removeOrphanedFiles(context)

            assertEquals("Both orphaned direct files should be removed", 2, removed)
            assertFalse("Completed orphaned output should be deleted", completedOutput.exists())
            assertFalse("Interrupted .part file should be deleted", interruptedOutput.exists())
            assertTrue("Cleanup must not recursively delete nested directories", nestedDirectory.isDirectory)
            assertTrue("Cleanup must not touch nested contents", nestedFile.isFile)
            assertTrue("Cleanup must not touch files outside pending_exports", unrelatedFile.isFile)
        } finally {
            completedOutput.delete()
            interruptedOutput.delete()
            nestedFile.delete()
            nestedDirectory.delete()
            unrelatedFile.delete()
            pendingDirectory.delete()
            isolatedRoot.delete()
        }
    }
}
