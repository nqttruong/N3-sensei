package com.n3sensei.app.feature.exam

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamLabelsTest {

    @Test
    fun countdown_showsMinutesAndSeconds_hoursWhenNeeded_roundedUp() {
        assertEquals("30:00", formatCountdown(1_800_000))
        assertEquals("29:59", formatCountdown(1_799_000))
        // 0.4 s left is still "00:01": "00:00" only once the time is up.
        assertEquals("00:01", formatCountdown(400))
        assertEquals("00:00", formatCountdown(0))
        assertEquals("00:00", formatCountdown(-5_000))
        assertEquals("1:45:00", formatCountdown(6_300_000))
    }
}
