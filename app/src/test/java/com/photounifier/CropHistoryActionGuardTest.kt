package com.photounifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropHistoryActionGuardTest {
    @Test
    fun allowsHistoryActionsWhenCropEditorIsIdle() {
        assertTrue(canEditCropHistory(PhotoUiState(cropOpen = true)))
    }

    @Test
    fun blocksHistoryActionsWhileCropOperationIsApplying() {
        assertFalse(
            canEditCropHistory(
                PhotoUiState(cropOpen = true, cropApplying = true)
            )
        )
    }

    @Test
    fun blocksHistoryActionsWhenEditorIsClosed() {
        assertFalse(canEditCropHistory(PhotoUiState(cropOpen = false)))
    }

    @Test
    fun blocksHistoryActionsWhileImageBatchIsProcessing() {
        assertFalse(
            canEditCropHistory(
                PhotoUiState(cropOpen = true, busy = true)
            )
        )
    }
}
