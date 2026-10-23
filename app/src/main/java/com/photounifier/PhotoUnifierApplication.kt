package com.photounifier

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Removes private processing outputs left behind by a previous app process.
 *
 * Pending exports are only referenced by in-memory ViewModel state. When a new
 * process starts, that state no longer exists, so these files are safe to
 * remove before the user creates a new processing session.
 */
class PhotoUnifierApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cleanup must be best-effort: a failed deletion must never prevent startup.
        runCatching { PendingExportCleanup.removeOrphanedFiles(this) }
    }
}

internal object PendingExportCleanup {
    private const val DIRECTORY_NAME = "pending_exports"

    /**
     * Removes regular files directly inside the app-owned pending-export directory.
     * Does not recurse into directories or touch MediaStore/public user files.
     */
    fun removeOrphanedFiles(context: Context): Int {
        val directory = File(context.filesDir, DIRECTORY_NAME)
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
