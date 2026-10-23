package com.photounifier

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * Removes private-to-this-app MediaStore exports left pending when a process died
 * before publishing them. Published exports and rows outside our export folders
 * are intentionally left untouched.
 */
internal object PendingMediaStoreExportCleanup {
    private data class Target(
        val collection: Uri,
        val relativePath: String,
        val fileNamePrefix: String
    )

    private val targets = listOf(
        Target(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            relativePath = "${Environment.DIRECTORY_PICTURES}/PhotoUnifier",
            fileNamePrefix = "PU_"
        ),
        Target(
            collection = MediaStore.Files.getContentUri("external"),
            relativePath = "${Environment.DIRECTORY_DOCUMENTS}/PhotoUnifier",
            fileNamePrefix = "PhotoUnifier_"
        )
    )

    /**
     * Queries and collects matching rows before deleting them. MediaStore exposes
     * this app's pending rows to the owner; entries are additionally scoped by
     * both the app's export folder and the output naming convention.
     */
    fun removeOrphanedRows(context: Context): Int {
        val resolver = context.contentResolver
        var removed = 0

        targets.forEach { target ->
            val pendingUris = mutableListOf<Uri>()
            val cursor = try {
                resolver.query(
                    target.collection,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        MediaStore.MediaColumns.IS_PENDING
                    ),
                    // Filter pending rows in the provider, then compare normalized
                    // path/name values in Kotlin. MediaStore may normalize RELATIVE_PATH
                    // with a trailing slash and provider-side path selection is not
                    // consistent across image and generic-file collections.
                    "${MediaStore.MediaColumns.IS_PENDING} = ?",
                    arrayOf("1"),
                    null
                )
            } catch (_: Exception) {
                null
            } ?: return@forEach

            try {
                cursor.use { rows ->
                    val idColumn = rows.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameColumn = rows.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathColumn = rows.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val pendingColumn = rows.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                    if (idColumn < 0 || nameColumn < 0 || pathColumn < 0 || pendingColumn < 0) {
                        return@use
                    }

                    while (rows.moveToNext()) {
                        if (rows.getInt(pendingColumn) != 1) continue
                        val name = rows.getString(nameColumn) ?: continue
                        val path = rows.getString(pathColumn)?.trimEnd('/') ?: continue
                        if (path != target.relativePath || !name.startsWith(target.fileNamePrefix)) continue

                        val id = rows.getLong(idColumn)
                        pendingUris += ContentUris.withAppendedId(target.collection, id)
                    }
                }
            } catch (_: Exception) {
                // One provider/query failure must not prevent cleanup of the other collection.
                return@forEach
            }

            pendingUris.forEach { uri ->
                val deleted = try {
                    resolver.delete(uri, null, null) > 0
                } catch (_: Exception) {
                    false
                }
                if (deleted) removed++
            }
        }

        return removed
    }
}
