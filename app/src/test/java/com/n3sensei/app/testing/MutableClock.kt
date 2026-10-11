package com.n3sensei.app.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Clock that only moves when the test advances it; [currentZone] can change like a device setting. */
class MutableClock(
    var now: Instant = Instant.parse("2026-10-11T08:00:00Z"),
    var currentZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = currentZone
    override fun withZone(zone: ZoneId?): Clock = this

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
