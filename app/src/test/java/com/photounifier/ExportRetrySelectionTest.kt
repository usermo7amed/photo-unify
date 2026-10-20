package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportRetrySelectionTest {
    @Test
    fun successfulImageExportsAreUnselectedButFailuresRemainSelected() {
        val remaining = remainingExportIdsAfterExport(
            format = ExportFormat.IMAGES,
            selectedIds = setOf("success.jpg", "failed.jpg"),
            successfulImageIds = setOf("success.jpg"),
            failedCount = 1
        )

        assertEquals(setOf("failed.jpg"), remaining)
    }

    @Test
    fun successfulImageExportClearsItsSelection() {
        val remaining = remainingExportIdsAfterExport(
            format = ExportFormat.IMAGES,
            selectedIds = setOf("one.jpg", "two.jpg"),
            successfulImageIds = setOf("one.jpg", "two.jpg"),
            failedCount = 0
        )

        assertEquals(emptySet<String>(), remaining)
    }

    @Test
    fun failedPdfExportKeepsSelectionForRetry() {
        val selected = setOf("one.jpg", "two.jpg")

        val remaining = remainingExportIdsAfterExport(
            format = ExportFormat.PDF,
            selectedIds = selected,
            successfulImageIds = emptySet(),
            failedCount = 2
        )

        assertEquals(selected, remaining)
    }

    @Test
    fun successfulPdfExportClearsSelectionToPreventAccidentalDuplicate() {
        val remaining = remainingExportIdsAfterExport(
            format = ExportFormat.PDF,
            selectedIds = setOf("one.jpg", "two.jpg"),
            successfulImageIds = emptySet(),
            failedCount = 0
        )

        assertEquals(emptySet<String>(), remaining)
    }
}
