package com.n3sensei.app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReviewTimeTest {

    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String, zone: ZoneId = vietnam): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test
    fun newItem_isNotScheduled() {
        assertEquals(DueState.NOT_SCHEDULED, ReviewTime.dueState(null, at("2026-10-11T08:00"), vietnam))
    }

    @Test
    fun dueAtExactlyNow_isDue_oneMillisecondLater_isNotDueBefore() {
        val now = at("2026-10-11T08:00")
        assertEquals(DueState.DUE, ReviewTime.dueState(now, now, vietnam))
        assertEquals(DueState.NOT_DUE, ReviewTime.dueState(now.plusMillis(1), now, vietnam))
    }

    @Test
    fun nearMidnight_dueYesterdayIsOverdue_dueTodayIsDue() {
        val justAfterMidnight = at("2026-10-12T00:01")
        assertEquals(DueState.OVERDUE, ReviewTime.dueState(at("2026-10-11T23:59"), justAfterMidnight, vietnam))
        assertEquals(DueState.DUE, ReviewTime.dueState(at("2026-10-12T00:00"), justAfterMidnight, vietnam))

        val justBeforeMidnight = at("2026-10-11T23:59")
        assertEquals(DueState.DUE, ReviewTime.dueState(at("2026-10-11T00:00"), justBeforeMidnight, vietnam))
        assertEquals(DueState.NOT_DUE, ReviewTime.dueState(at("2026-10-12T00:00"), justBeforeMidnight, vietnam))
    }

    @Test
    fun dayBoundaries_followTheCurrentZone() {
        val instant = Instant.parse("2026-10-11T18:30:00Z") // 01:30 on 12 Oct in Vietnam, 14:30 on 11 Oct in New York
        assertEquals(at("2026-10-12T00:00"), ReviewTime.startOfDay(instant, vietnam))
        assertEquals(at("2026-10-13T00:00"), ReviewTime.startOfNextDay(instant, vietnam))
        val newYork = ZoneId.of("America/New_York")
        assertEquals(at("2026-10-11T00:00", newYork), ReviewTime.startOfDay(instant, newYork))

        // An item due 23:00 Vietnam time on 11 Oct: overdue in Vietnam (a new day began),
        // merely due in New York where it is still 11 Oct.
        val due = at("2026-10-11T23:00")
        assertEquals(DueState.OVERDUE, ReviewTime.dueState(due, instant, vietnam))
        assertEquals(DueState.DUE, ReviewTime.dueState(due, instant, newYork))
    }

    @Test
    fun timeZoneChange_movesDayBoundaries_notDueTimes() {
        val due = Instant.parse("2026-10-12T02:00:00Z")
        val now = Instant.parse("2026-10-12T03:00:00Z")
        // Due time is the same instant whatever the zone; only "today" differs.
        listOf(vietnam, ZoneId.of("Europe/London"), ZoneId.of("Pacific/Auckland")).forEach { zone ->
            assertEquals(true, ReviewTime.dueState(due, now, zone) in setOf(DueState.DUE, DueState.OVERDUE))
        }
    }

    @Test
    fun dstDay_startOfDayIsLocalMidnight() {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = at("2026-10-25T12:00", berlin) // 25-hour day
        assertEquals(25L * 60, java.time.Duration.between(ReviewTime.startOfDay(now, berlin), ReviewTime.startOfNextDay(now, berlin)).toMinutes())
    }
}
