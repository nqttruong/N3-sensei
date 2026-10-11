package com.n3sensei.app.core.ui.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

/** Default refresh period for "now": due counts change as time passes. */
const val CLOCK_TICK_MILLIS = 60_000L

/**
 * Emits the current time now and then every [periodMillis]. Re-reading the clock on each tick
 * also picks up system time and time-zone changes while the screen is open.
 */
fun Clock.ticks(periodMillis: Long = CLOCK_TICK_MILLIS): Flow<Instant> = flow {
    while (true) {
        emit(instant())
        delay(periodMillis)
    }
}
