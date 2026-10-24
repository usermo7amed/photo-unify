package com.photounifier

/**
 * Restores the export UI after an unexpected throwable prevents the repository's
 * normal completion callback from running.
 *
 * Successful image exports have already been published to MediaStore and must be
 * removed from the retry selection even when a later item fails fatally. PDF exports
 * are treated as a single all-or-nothing item and retain their selection on failure.
 */
internal fun recoverExportFailure(
    state: PhotoUiState,
    format: ExportFormat,
    successfulImageIds: Set<String>,
    message: String,
    failureStatus: String
): PhotoUiState {
    val failedCount = when (format) {
        ExportFormat.IMAGES -> (state.exportProgressMax - successfulImageIds.size).coerceAtLeast(1)
        ExportFormat.PDF -> 1
    }
    return state.copy(
        exportBusy = false,
        exportedCount = if (format == ExportFormat.IMAGES) successfulImageIds.size else 0,
        selectedExportIds = remainingExportIdsAfterExport(
            format = format,
            selectedIds = state.selectedExportIds,
            successfulImageIds = successfulImageIds,
            failedCount = failedCount
        ),
        exportStatus = failureStatus,
        exportErrors = state.exportErrors + message
    )
}
