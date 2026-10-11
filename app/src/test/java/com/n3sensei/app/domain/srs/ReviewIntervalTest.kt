package com.n3sensei.app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReviewIntervalTest {

    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh") // UTC+7, no daylight saving
    private val berlin = ZoneId.of("Europe/Berlin") // daylight saving

    private fun at(local: String, zone: ZoneId): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private fun local(instant: Instant, zone: ZoneId): String = instant.atZone(zone).toLocalDateTime().toString()

    @Test
    fun minutesAndHoursAreElapsedTime() {
        val start = at("2026-10-11T23:50", vietnam)
        assertEquals("2026-10-12T00:10", local(ReviewInterval(20, IntervalUnit.MINUTES).addTo(start, vietnam), vietnam))
        assertEquals("2026-10-12T00:50", local(ReviewInterval(1, IntervalUnit.HOURS).addTo(start, vietnam), vietnam))
    }

    @Test
    fun daysWeeksMonthsFollowTheCalendar() {
        val start = at("2026-10-11T08:00", vietnam)
        assertEquals("2026-10-12T08:00", local(ReviewInterval(1, IntervalUnit.DAYS).addTo(start, vietnam), vietnam))
        assertEquals("2026-10-25T08:00", local(ReviewInterval(2, IntervalUnit.WEEKS).addTo(start, vietnam), vietnam))
        assertEquals("2026-11-11T08:00", local(ReviewInterval(1, IntervalUnit.MONTHS).addTo(start, vietnam), vietnam))
    }

    @Test
    fun oneMonth_isACalendarMonth_clampedToMonthEnd() {
        val month = ReviewInterval(1, IntervalUnit.MONTHS)
        assertEquals("2027-02-28T10:00", local(month.addTo(at("2027-01-31T10:00", vietnam), vietnam), vietnam))
        assertEquals("2028-02-29T10:00", local(month.addTo(at("2028-01-31T10:00", vietnam), vietnam), vietnam)) // leap year
        assertEquals("2026-12-30T10:00", local(month.addTo(at("2026-11-30T10:00", vietnam), vietnam), vietnam))
    }

    @Test
    fun oneDay_keepsWallClockTimeAcrossDaylightSavingChange() {
        // 25 Oct 2026: Berlin clocks go back one hour; the day is 25 hours long.
        val start = at("2026-10-24T09:00", berlin)
        val due = ReviewInterval(1, IntervalUnit.DAYS).addTo(start, berlin)
        assertEquals("2026-10-25T09:00", local(due, berlin))
        assertEquals(25 * 60L, java.time.Duration.between(start, due).toMinutes())
        // While one hour stays exactly 60 minutes.
        assertEquals(60L, java.time.Duration.between(start, ReviewInterval(1, IntervalUnit.HOURS).addTo(start, berlin)).toMinutes())
    }

    @Test
    fun calendarStepsUseTheZoneOfTheAnswer() {
        // Same instant, answered in two zones: "next day" means the next local day in each.
        val instant = Instant.parse("2026-10-11T20:00:00Z") // 03:00 on 12 Oct in Vietnam
        assertEquals("2026-10-13T03:00", local(ReviewInterval(1, IntervalUnit.DAYS).addTo(instant, vietnam), vietnam))
        val newYork = ZoneId.of("America/New_York")
        assertEquals("2026-10-12T16:00", local(ReviewInterval(1, IntervalUnit.DAYS).addTo(instant, newYork), newYork))
    }

    @Test
    fun encodeAndParseRoundTrip() {
        val ladder = ReviewLadder.DEFAULT.intervals
        assertEquals("20m,1h,1d,2d,1w,2w,1mo", ReviewInterval.encodeList(ladder))
        assertEquals(ladder, ReviewInterval.parseList("20m, 1h,1d,2d,1w,2w,1mo"))
        assertNull(ReviewInterval.parse("0m"))
        assertNull(ReviewInterval.parse("5y"))
        assertNull(ReviewInterval.parseList("20m,,1h"))
        assertNull(ReviewInterval.parseList(""))
    }
}
