package com.photounifier

import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingTaskSubmissionTest {
    @Test
    fun rejectedSubmissionRecoversBusyProcessingState() {
        var taskRan = false
        var recovered = false
        submitOrRecoverRejectedTask(
            submit = { throw RejectedExecutionException("executor already shut down") },
            task = { taskRan = true },
            onRejected = { error ->
                val state = recoverProcessingFailure(
                    state = PhotoUiState(busy = true, progress = 2, progressMax = 4),
                    message = error.message ?: "unknown",
                    failureStatus = "processing interrupted"
                )
                assertFalse(state.busy)
                assertEquals(2, state.progress)
                assertEquals(4, state.progressMax)
                assertEquals("processing interrupted", state.status)
                assertEquals(listOf("executor already shut down"), state.errors)
                recovered = true
            }
        )

        assertFalse(taskRan)
        assertTrue(recovered)
    }

    @Test
    fun acceptedSubmissionRunsWithoutRecovery() {
        var taskRan = false
        var recoveryRan = false
        submitOrRecoverRejectedTask(
            submit = { task -> task() },
            task = { taskRan = true },
            onRejected = { recoveryRan = true }
        )
        assertTrue(taskRan)
        assertFalse(recoveryRan)
    }
}
