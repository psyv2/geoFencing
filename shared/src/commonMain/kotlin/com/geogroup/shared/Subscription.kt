package com.geogroup.shared

import kotlinx.coroutines.Job

/**
 * Handle for an observation started from Swift. `Flow` does not cross the Objective-C
 * boundary usefully, so the public API hands out a callback plus this cancel token.
 */
class Subscription internal constructor(private val job: Job) {

    val isActive: Boolean
        get() = job.isActive

    fun cancel() {
        job.cancel()
    }
}
