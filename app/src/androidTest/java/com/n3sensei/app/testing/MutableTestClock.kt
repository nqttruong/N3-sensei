package com.n3sensei.app.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand (milliseconds). */
class MutableTestClock(private var nowMillis: Long) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this

    fun advance(millis: Long) {
        nowMillis += millis
    }
}
