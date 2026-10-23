package com.photounifier

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private fun queryPendingRows(
        context: Context,
        collection: Uri,
        projection: Array<String>
    ): Cursor? {
        val resolver = context.contentResolver
        val pendingSelection = "${MediaStore.MediaColumns.IS_PENDING} = ?"
        val pendingArgs = arrayOf("1")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // MediaStore filters pending rows out by default, even when the selection
            // explicitly requests IS_PENDING=1. Opt in to seeing pending rows first.
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, pendingSelection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, pendingArgs)
            }
            resolver.query(collection, projection, queryArgs, null)
        } else {
            // API 29 has no query-argument equivalent; use the legacy include-pending URI.
            @Suppress("DEPRECATION")
            val includePendingUri = MediaStore.setIncludePending(collection)
            resolver.query(includePendingUri, projection, pendingSelection, pendingArgs, null)
        }
    }

    fun removeOrphanedRows(context: Context): Int {
        val resolver = context.contentResolver
        var removed = 0

        targets.forEach { target ->
            val pendingUris = mutableListOf<Uri>()
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.IS_PENDING
            )
            val cursor = try {
                queryPendingRows(context, target.collection, projection)
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
