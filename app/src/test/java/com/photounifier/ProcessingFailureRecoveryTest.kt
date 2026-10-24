package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProcessingFailureRecoveryTest {
    @Test
    fun unexpectedFailureClearsBusyAndPreservesPartialResults() {
        val original = PhotoUiState(
            selected = emptyList(),
            busy = true,
            progress = 2,
            progressMax = 5,
            savedCount = 2,
            status = "processing",
            errors = listOf("first image failed")
        )

        val recovered = recoverProcessingFailure(
            state = original,
            message = "unexpected failure",
            failureStatus = "processing interrupted"
        )

        assertFalse(recovered.busy)
        assertEquals(2, recovered.progress)
        assertEquals(5, recovered.progressMax)
        assertEquals(2, recovered.savedCount)
        assertEquals("processing interrupted", recovered.status)
        assertEquals(
            listOf("first image failed", "unexpected failure"),
            recovered.errors
        )
    }
}
