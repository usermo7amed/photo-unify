package com.photounifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SelectionBitmapOwnershipTest {
    @Test
    fun resourceIsReleasedWhenOwnerRegistrationFails() {
        val resource = Any()
        val expected = AssertionError("synthetic registration failure")
        var cleanupCount = 0
        var actual: AssertionError? = null

        try {
            retainResourceOrCleanup(
                resource = resource,
                cleanup = { cleaned ->
                    assertSame(resource, cleaned)
                    cleanupCount++
                },
                retain = { throw expected }
            )
        } catch (error: AssertionError) {
            actual = error
        }

        assertSame("The original failure must be preserved", expected, actual)
        assertEquals("Unretained resource must be cleaned once", 1, cleanupCount)
    }

    @Test
    fun resourceIsNotReleasedAfterOwnershipTransferSucceeds() {
        val resource = Any()
        var cleanupCount = 0
        var retained: Any? = null

        retainResourceOrCleanup(
            resource = resource,
            cleanup = { cleanupCount++ },
            retain = { retained = it }
        )

        assertSame(resource, retained)
        assertEquals("Ownership now belongs to the receiver", 0, cleanupCount)
    }

    @Test
    fun cleanupFailureDoesNotMaskRegistrationFailure() {
        val expected = AssertionError("registration failed")
        var actual: AssertionError? = null

        try {
            retainResourceOrCleanup(
                resource = Any(),
                cleanup = { throw IllegalStateException("cleanup failed") },
                retain = { throw expected }
            )
        } catch (error: AssertionError) {
            actual = error
        }

        assertSame(expected, actual)
    }
}
