package com.n3sensei.app.app.di

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * System clock whose zone is read on every call. `Clock.systemDefaultZone()` would freeze the
 * zone at creation, so a time-zone change while the app runs would keep the old day
 * boundaries.
 */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
