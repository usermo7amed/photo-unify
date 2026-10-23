package com.photounifier

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingExportCleanupTest {
    @Test
    fun removesOnlyDirectFilesAndLeavesNestedDirectoriesUntouched() {
        val root = createTempDir(prefix = "photo-unifier-cleanup-")
        val pendingImage = File(root, "PU_test_001.jpg")
        val partialExport = File(root, "incomplete.part")
        val nestedDirectory = File(root, "nested").apply { mkdirs() }
        val nestedFile = File(nestedDirectory, "keep.tmp")

        try {
            assertTrue(pendingImage.writeText("image").isEmpty())
            assertTrue(partialExport.writeText("partial").isEmpty())
            assertTrue(nestedFile.writeText("keep").isEmpty())

            assertEquals(2, PendingExportCleanup.removeOrphanedFiles(root))

            assertFalse("Direct pending image should be removed", pendingImage.exists())
            assertFalse("Direct partial file should be removed", partialExport.exists())
            assertTrue("Nested directory should be preserved", nestedDirectory.isDirectory)
            assertTrue("Cleanup must not recurse into nested directories", nestedFile.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingDirectoryIsAHarmlessNoOp() {
        val parent = createTempDir(prefix = "photo-unifier-cleanup-")
        val missingDirectory = File(parent, "does-not-exist")

        try {
            assertEquals(0, PendingExportCleanup.removeOrphanedFiles(missingDirectory))
        } finally {
            parent.deleteRecursively()
        }
    }
}
