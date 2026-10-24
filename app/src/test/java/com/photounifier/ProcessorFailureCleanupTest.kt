package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessorFailureCleanupTest {
    @Test
    fun cleanupRunsAndOriginalErrorIsRethrownUnchanged() {
        var cleanupCalls = 0
        val expected = AssertionError("synthetic fatal export failure")
        var actual: AssertionError? = null

        try {
            Processor.withCleanupOnFailure(
                cleanup = { cleanupCalls++ }
            ) {
                throw expected
            }
        } catch (error: AssertionError) {
            actual = error
        }

        assertSame("The original fatal error must be rethrown", expected, actual)
        assertEquals("Cleanup must run exactly once", 1, cleanupCalls)
    }

    @Test
    fun successfulOperationDoesNotRunFailureCleanup() {
        var cleanupCalls = 0

        val result = Processor.withCleanupOnFailure(
            cleanup = { cleanupCalls++ }
        ) {
            "published"
        }

        assertEquals("published", result)
        assertEquals("Successful exports must not be cleaned up", 0, cleanupCalls)
    }

    @Test
    fun cleanupFailureDoesNotMaskOriginalError() {
        val expected = AssertionError("original export failure")
        var actual: AssertionError? = null

        try {
            Processor.withCleanupOnFailure(
                cleanup = { throw IllegalStateException("synthetic cleanup failure") }
            ) {
                throw expected
            }
        } catch (error: AssertionError) {
            actual = error
        }

        assertSame("Cleanup failure must not replace the original error", expected, actual)
        assertTrue(actual?.message == "original export failure")
    }
}
