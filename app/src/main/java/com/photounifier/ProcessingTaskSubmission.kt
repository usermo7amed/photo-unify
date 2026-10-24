package com.photounifier

import java.util.concurrent.RejectedExecutionException

/**
 * Runs a task submission and reports executor rejection to the caller.
 * The callback runs only when submission itself is rejected; task failures stay
 * on the executor thread and are handled by the task's own error boundary.
 */
internal fun submitOrRecoverRejectedTask(
    submit: (() -> Unit) -> Unit,
    task: () -> Unit,
    onRejected: (RejectedExecutionException) -> Unit
) {
    try {
        submit(task)
    } catch (error: RejectedExecutionException) {
        onRejected(error)
    }
}
