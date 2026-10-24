package com.photounifier

/**
 * Transfers a newly created resource to its owner only when registration succeeds.
 * If delivery fails before ownership is accepted, release the resource and preserve
 * the original failure so allocation errors cannot leak an untracked bitmap.
 */
internal fun <T> retainResourceOrCleanup(
    resource: T,
    cleanup: (T) -> Unit,
    retain: (T) -> Unit
) {
    try {
        retain(resource)
    } catch (error: Throwable) {
        runCatching { cleanup(resource) }
        throw error
    }
}
