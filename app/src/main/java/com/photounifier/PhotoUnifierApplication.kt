package com.photounifier

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Removes abandoned processing files and unpublished MediaStore exports left
 * behind by a previous app process. Completed public exports are preserved.
 */
class PhotoUnifierApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cleanup must be best-effort: a failed deletion must never prevent startup.
        runCatching { PendingExportCleanup.removeOrphanedFiles(this) }
        runCatching { PendingMediaStoreExportCleanup.removeOrphanedRows(this) }
    }
}

internal object PendingExportCleanup {
    private const val DIRECTORY_NAME = "pending_exports"

    /**
     * Removes regular files directly inside the app-owned pending-export directory.
     * Does not recurse into directories or touch MediaStore/public user files.
     */
    fun removeOrphanedFiles(context: Context): Int =
        removeOrphanedFiles(File(context.filesDir, DIRECTORY_NAME))

    /**
     * File-based overload keeps the deletion boundary independently testable.
     * Only direct regular-file children are eligible for removal.
     */
    internal fun removeOrphanedFiles(directory: File): Int {
        val files = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return 0

        var removed = 0
        files.forEach { file ->
            if (!file.isFile) return@forEach
            val deleted = try {
                file.delete()
            } catch (_: SecurityException) {
                false
            }
            if (deleted) removed++
        }
        return removed
    }
}
