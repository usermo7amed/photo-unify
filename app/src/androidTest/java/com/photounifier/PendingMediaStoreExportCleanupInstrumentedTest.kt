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
