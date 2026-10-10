package com.geogroup.shared.platform

/** Wall-clock access, injected so timestamps are controllable from a test. */
fun interface TimeProvider {
    fun nowEpochMillis(): Long
}

/** A clock that only moves when a test tells it to. */
class FixedTimeProvider(private var now: Long = 0L) : TimeProvider {
    override fun nowEpochMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }

    fun set(millis: Long) {
        now = millis
    }
}

expect fun systemTimeProvider(): TimeProvider
