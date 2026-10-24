package com.photounifier

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingMediaStoreExportCleanupInstrumentedTest {

    @Test
    fun cleanupRemovesOnlyOrphanedPendingRowsOwnedByExportFolders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val suffix = System.nanoTime().toString()
        val imageFolder = "${Environment.DIRECTORY_PICTURES}/PhotoUnifier"
        val documentsFolder = "${Environment.DIRECTORY_DOCUMENTS}/PhotoUnifier"
        val unrelatedFolder = "${Environment.DIRECTORY_PICTURES}/PhotoUnifierOther"

        var orphanedImage: Uri? = null
        var orphanedPdf: Uri? = null
        var unrelatedPendingInFolder: Uri? = null
        var unrelatedPendingOutsideFolder: Uri? = null
        var completedImage: Uri? = null

        try {
            orphanedImage = insertRow(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "PU_orphaned-$suffix.jpg",
                "image/jpeg",
                imageFolder,
                pending = true
            )
            orphanedPdf = insertRow(
                context,
                MediaStore.Files.getContentUri("external"),
                "PhotoUnifier_orphaned-$suffix.pdf",
                "application/pdf",
                documentsFolder,
                pending = true
            )
            unrelatedPendingInFolder = insertRow(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "unrelated-$suffix.jpg",
                "image/jpeg",
                imageFolder,
                pending = true
            )
            unrelatedPendingOutsideFolder = insertRow(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "PU_outside-$suffix.jpg",
                "image/jpeg",
                unrelatedFolder,
                pending = true
            )
            completedImage = insertRow(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "PU_completed-$suffix.jpg",
                "image/jpeg",
                imageFolder,
                pending = false
            )

            val removed = PendingMediaStoreExportCleanup.removeOrphanedRows(context)

            assertEquals("Only the orphaned image and PDF should be removed", 2, removed)
            assertFalse("Orphaned pending image should be deleted", rowExists(context, orphanedImage!!))
            assertFalse("Orphaned pending PDF should be deleted", rowExists(context, orphanedPdf!!))
            assertTrue("Unrelated pending row in the folder should remain", rowExists(context, unrelatedPendingInFolder!!))
            assertTrue("Pending row outside the target folder should remain", rowExists(context, unrelatedPendingOutsideFolder!!))
            assertTrue("Published image should never be deleted", rowExists(context, completedImage!!))
        } finally {
            listOf(
                orphanedImage,
                orphanedPdf,
                unrelatedPendingInFolder,
                unrelatedPendingOutsideFolder,
                completedImage
            ).filterNotNull().forEach { uri ->
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }


    @Test
    fun imageExportDeletesPendingRowWhenSourceCannotBeOpenedAfterValidation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val backingFile = File(context.cacheDir, "PU_failure-$suffix.jpg")
        assertFalse("Failure-injection source must not exist", backingFile.exists())
        // Model a file that passes the preflight check but disappears before it can be read.
        val disappearingFile = object : File(backingFile.path) {
            override fun isFile(): Boolean = true
        }
        val folder = "${Environment.DIRECTORY_PICTURES}/PhotoUnifier"
        val observer = RecordingExportObserver()
        val repository = ExportRepository()

        repository.exportAsImages(
            context,
            listOf(ExportImage("failure-$suffix", Uri.EMPTY, disappearingFile, disappearingFile.name)),
            observer
        )

        assertEquals("No image should be reported as exported", 0, observer.successCount)
        assertEquals("The unreadable image should be reported as failed", 1, observer.failedCount)
        assertEquals("The failed item should not be marked successful", false, observer.itemSucceeded)
        val prefix = "${disappearingFile.nameWithoutExtension}_"
        val pendingAfter = pendingRows(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, folder)
        assertTrue(
            "A failed image export must delete its pending MediaStore row",
            pendingAfter.none { it.substringAfter('|').startsWith(prefix) }
        )
    }

    @Test
    fun pdfExportDeletesPendingRowWhenSourceCannotBeOpenedAfterValidation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val backingFile = File(context.cacheDir, "PU_pdf-failure-$suffix.jpg")
        assertFalse("Failure-injection source must not exist", backingFile.exists())
        // PDF export validates isFile before decoding, so this injects a read failure
        // only after the pending PDF destination has been inserted.
        val unreadableFile = object : File(backingFile.path) {
            override fun isFile(): Boolean = true
        }
        val folder = "${Environment.DIRECTORY_DOCUMENTS}/PhotoUnifier"
        val collection = MediaStore.Files.getContentUri("external")
        val pendingBefore = pendingRows(context, collection, folder)
        val observer = RecordingExportObserver()

        ExportRepository().exportAsPdf(
            context,
            listOf(ExportImage("pdf-failure-$suffix", Uri.EMPTY, unreadableFile, unreadableFile.name)),
            observer
        )

        assertEquals("Failed PDF export must not report success", 0, observer.successCount)
        assertEquals("The PDF export should be reported as failed", 1, observer.failedCount)
        val pendingAfter = pendingRows(context, collection, folder)
        assertTrue(
            "A failed PDF export must not leave a new pending MediaStore row",
            (pendingAfter - pendingBefore).isEmpty()
        )
    }

    @Test
    fun pdfExportRemovesPendingRowWhenAnUnexpectedErrorEscapes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val backingFile = File(context.cacheDir, "PU_pdf-fatal-$suffix.jpg")
        assertFalse("Failure-injection source must not exist", backingFile.exists())
        // AssertionError is deliberately not an Exception: it verifies finally-based
        // cleanup rather than relying on the normal exception handler.
        val failingFile = object : File(backingFile.path) {
            override fun isFile(): Boolean = throw AssertionError("synthetic validation failure")
        }
        val collection = MediaStore.Files.getContentUri("external")
        val folder = "${Environment.DIRECTORY_DOCUMENTS}/PhotoUnifier"
        val pendingBefore = pendingRows(context, collection, folder)

        var thrown: AssertionError? = null
        try {
            ExportRepository().exportAsPdf(
                context,
                listOf(ExportImage("pdf-fatal-$suffix", Uri.EMPTY, failingFile, failingFile.name)),
                RecordingExportObserver()
            )
        } catch (error: AssertionError) {
            thrown = error
        }

        assertEquals("The unexpected error should be rethrown unchanged", "synthetic validation failure", thrown?.message)
        val pendingAfter = pendingRows(context, collection, folder)
        assertTrue(
            "An unexpected error must not leave a pending PDF row",
            (pendingAfter - pendingBefore).isEmpty()
        )
    }

    private fun insertRow(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        pending: Boolean
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0)
        }
        return context.contentResolver.insert(collection, values)
            ?: throw AssertionError("Could not create MediaStore test row: $displayName")
    }


    private fun pendingRows(context: Context, collection: Uri, relativePath: String): Set<String> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        // Match the folder prefix in SQL, then compare normalized paths below because
        // MediaStore providers may normalize RELATIVE_PATH with a trailing slash.
        val selection = "${MediaStore.MediaColumns.IS_PENDING} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("1", relativePath + "%")
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            resolver.query(collection, projection, queryArgs, null)
        } else {
            @Suppress("DEPRECATION")
            val includePendingUri = MediaStore.setIncludePending(collection)
            resolver.query(includePendingUri, projection, selection, selectionArgs, null)
        } ?: throw AssertionError("Could not query pending rows in $relativePath")

        return cursor.use { rows ->
            buildSet {
                while (rows.moveToNext()) {
                    val path = rows.getString(2)?.trimEnd('/') ?: continue
                    if (path == relativePath) {
                        add("${rows.getLong(0)}|${rows.getString(1)}")
                    }
                }
            }
        }
    }

    private class RecordingExportObserver : ExportRepository.Observer {
        var successCount: Int = -1
        var failedCount: Int = -1
        var itemSucceeded: Boolean? = null

        override fun onProgress(completed: Int, total: Int, message: String) = Unit

        override fun onError(message: String) = Unit

        override fun onFinished(success: Int, failed: Int, error: String?) {
            successCount = success
            failedCount = failed
        }

        override fun onItemFinished(itemId: String, success: Boolean) {
            itemSucceeded = success
        }
    }

    private fun rowExists(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), queryArgs, null)
            } else {
                @Suppress("DEPRECATION")
                val includePendingUri = MediaStore.setIncludePending(uri)
                resolver.query(includePendingUri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            }
            cursor?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
