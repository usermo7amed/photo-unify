package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExportFatalFailureRecoveryTest {
    @Test
    fun fatalImageExportKeepsOnlyItemsThatStillNeedRetry() {
        val original = PhotoUiState(
            exportBusy = true,
            exportProgress = 1,
            exportProgressMax = 3,
            selectedExportIds = setOf("one.jpg", "two.jpg", "three.jpg"),
            exportErrors = listOf("earlier warning")
        )

        val recovered = recoverExportFailure(
            state = original,
            format = ExportFormat.IMAGES,
            successfulImageIds = setOf("one.jpg"),
            message = "unexpected failure",
            failureStatus = "Export stopped"
        )

        assertFalse(recovered.exportBusy)
        assertEquals(setOf("two.jpg", "three.jpg"), recovered.selectedExportIds)
        assertEquals(1, recovered.exportedCount)
        assertEquals(1, recovered.exportProgress)
        assertEquals(3, recovered.exportProgressMax)
        assertEquals("Export stopped", recovered.exportStatus)
        assertEquals(listOf("earlier warning", "unexpected failure"), recovered.exportErrors)
    }

    @Test
    fun failedPdfExportKeepsAllSelectedItemsForRetry() {
        val selected = setOf("one.jpg", "two.jpg")
        val recovered = recoverExportFailure(
            state = PhotoUiState(
                exportBusy = true,
                exportProgressMax = 2,
                selectedExportIds = selected
            ),
            format = ExportFormat.PDF,
            successfulImageIds = emptySet(),
            message = "unexpected failure",
            failureStatus = "Export stopped"
        )

        assertFalse(recovered.exportBusy)
        assertEquals(selected, recovered.selectedExportIds)
        assertEquals(0, recovered.exportedCount)
    }
}
