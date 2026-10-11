package com.n3sensei.app.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPolicyTest {

    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String, zone: ZoneId = vietnam): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private val on = ReminderSettings(enabled = true, startHour = 8, endHour = 21, frequency = ReminderFrequency.EVERY_3_HOURS)

    @Test
    fun window_startInclusive_endExclusive() {
        assertFalse(ReminderPolicy.isInWindow(at("2026-10-11T07:59"), vietnam, on))
        assertTrue(ReminderPolicy.isInWindow(at("2026-10-11T08:00"), vietnam, on))
        assertTrue(ReminderPolicy.isInWindow(at("2026-10-11T20:59"), vietnam, on))
        assertFalse(ReminderPolicy.isInWindow(at("2026-10-11T21:00"), vietnam, on))
    }

    @Test
    fun window_overMidnight_andWholeDay() {
        val night = on.copy(startHour = 20, endHour = 2)
        assertTrue(ReminderPolicy.isInWindow(at("2026-10-11T23:30"), vietnam, night))
        assertTrue(ReminderPolicy.isInWindow(at("2026-10-12T01:59"), vietnam, night))
        assertFalse(ReminderPolicy.isInWindow(at("2026-10-12T02:00"), vietnam, night))
        assertFalse(ReminderPolicy.isInWindow(at("2026-10-12T12:00"), vietnam, night))
        val allDay = on.copy(startHour = 6, endHour = 6)
        assertTrue(ReminderPolicy.isInWindow(at("2026-10-12T03:00"), vietnam, allDay))
    }

    @Test
    fun window_usesTheCurrentTimeZone() {
        val instant = Instant.parse("2026-10-11T23:30:00Z") // 06:30 in Vietnam, 19:30 in New York
        assertFalse(ReminderPolicy.isInWindow(instant, vietnam, on))
        assertTrue(ReminderPolicy.isInWindow(instant, ZoneId.of("America/New_York"), on))
    }

    @Test
    fun decide_disabledOutsideWindowAndSpacing() {
        val now = at("2026-10-11T12:00")
        assertEquals(ReminderDecision.DISABLED, ReminderPolicy.decide(now, vietnam, on.copy(enabled = false), null))
        assertEquals(ReminderDecision.OUTSIDE_WINDOW, ReminderPolicy.decide(at("2026-10-11T22:00"), vietnam, on, null))
        assertEquals(ReminderDecision.NOTIFY, ReminderPolicy.decide(now, vietnam, on, null))
        // Every 3 hours: at least 2 h 15 min (3/4 of the period) between reminders.
        assertEquals(ReminderDecision.TOO_SOON, ReminderPolicy.decide(now, vietnam, on, now.minus(Duration.ofMinutes(134))))
        assertEquals(ReminderDecision.NOTIFY, ReminderPolicy.decide(now, vietnam, on, now.minus(Duration.ofMinutes(135))))
    }

    @Test
    fun decide_onceADay_allowsJitter_butNotTwiceInAMorning() {
        val daily = on.copy(frequency = ReminderFrequency.ONCE_A_DAY)
        val yesterday = at("2026-10-10T09:00")
        assertEquals(ReminderDecision.NOTIFY, ReminderPolicy.decide(at("2026-10-11T08:30"), vietnam, daily, yesterday))
        assertEquals(ReminderDecision.TOO_SOON, ReminderPolicy.decide(at("2026-10-11T11:00"), vietnam, daily, at("2026-10-11T08:30")))
    }

    @Test
    fun decide_clockMovedBack_doesNotBlockForever() {
        val now = at("2026-10-11T12:00")
        assertEquals(ReminderDecision.NOTIFY, ReminderPolicy.decide(now, vietnam, on, now.plus(Duration.ofDays(2))))
    }
}
