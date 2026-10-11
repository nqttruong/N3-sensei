package com.n3sensei.app.domain.forecast

import com.n3sensei.app.domain.analytics.TimeBucket
import com.n3sensei.app.domain.srs.DailyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ProgressAndTrendTest {

    // --- Progress estimate ---

    @Test
    fun nothingLeft_isAllStarted() {
        assertEquals(ProgressEstimate.AllStarted, ProgressEstimator.estimate(0, List(28) { 5 }, 28, 20))
    }

    @Test
    fun newUserOrLittleData_isNotEnoughData_butKeepsTheSettingsCalculation() {
        val newUser = ProgressEstimator.estimate(100, List(28) { 0 }, 0, 20)
        assertEquals(ProgressEstimate.NotEnoughData(remaining = 100, studyDays = 0, newItems = 0, settingsDays = 5), newUser)
        // 6 study days is below the minimum of 7 even with many items.
        val fewDays = ProgressEstimator.estimate(100, List(22) { 0 } + List(6) { 10 }, 6, 20)
        assertTrue(fewDays is ProgressEstimate.NotEnoughData)
        // 10 days but only 15 new items (< 20).
        val fewItems = ProgressEstimator.estimate(100, List(18) { 0 } + List(10) { if (it < 5) 3 else 0 }, 10, 0)
        assertEquals(ProgressEstimate.NotEnoughData(100, 10, 15, settingsDays = null), fewItems)
    }

    @Test
    fun steadyPace_givesTightRange_highConfidence() {
        // 2 new items every day for 28 days → 2.0/day; 100 left → 50 days.
        val e = ProgressEstimator.estimate(100, List(28) { 2 }, 28, 20) as ProgressEstimate.Estimate
        assertEquals(2.0, e.itemsPerDay, 1e-9)
        assertEquals(50, e.expectedDays)
        assertEquals(50, e.fastestDays)
        assertEquals(50, e.slowestDays)
        assertEquals(Confidence.HIGH, e.confidence)
        assertEquals(5, e.settingsDays)
    }

    @Test
    fun unevenPace_givesWideRange_lowerConfidence_andOpenEndWhenAWeekWasIdle() {
        // Weeks: 0, 14, 28, 14 items → weekly rates 0, 2, 4, 2/day; mean 56/28 = 2/day.
        val days = List(7) { 0 } + List(7) { 2 } + List(7) { 4 } + List(7) { 2 }
        val e = ProgressEstimator.estimate(60, days, 21, 10) as ProgressEstimate.Estimate
        assertEquals(30, e.expectedDays)
        assertEquals(15, e.fastestDays)
        assertNull(e.slowestDays)
        assertFalse(e.confidence == Confidence.HIGH)
    }

    // --- Trend ---

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private val today = LocalDate.parse("2026-10-11") // a Sunday

    @Test
    fun weeks_startOnMonday_andAreZeroFilled() {
        val buckets = listOf(
            TimeBucket(at("2026-10-05T08:00"), 3, 1), // Monday of the current week
            TimeBucket(at("2026-10-11T22:00"), 2, 0), // Sunday, same week
            TimeBucket(at("2026-10-04T23:45"), 4, 4), // Sunday of the previous week
        )
        val weeks = TrendAggregator.aggregate(buckets, zone, today, TrendUnit.WEEK)
        assertEquals(8, weeks.size)
        assertEquals(LocalDate.parse("2026-08-17"), weeks.first().start)
        assertEquals(LocalDate.parse("2026-10-05"), weeks.last().start)
        assertEquals(listOf(4, 5), weeks.takeLast(2).map { it.answers })
        assertEquals(listOf(4, 1), weeks.takeLast(2).map { it.newItems })
    }

    @Test
    fun months_andDays() {
        val buckets = listOf(TimeBucket(at("2026-09-30T23:45"), 1, 0), TimeBucket(at("2026-10-01T00:00"), 2, 0))
        val months = TrendAggregator.aggregate(buckets, zone, today, TrendUnit.MONTH)
        assertEquals(LocalDate.parse("2026-05-01"), months.first().start)
        assertEquals(listOf(1, 2), months.takeLast(2).map { it.answers })
        val days = TrendAggregator.aggregate(buckets, zone, today, TrendUnit.DAY)
        assertEquals(14, days.size)
        assertEquals(LocalDate.parse("2026-09-28"), days.first().start)
    }

    // --- Daily goal ---

    @Test
    fun dailyGoal_isCappedByLimitsAndAvailableWork() {
        val limits = DailyLimits(newPerDay = 20, reviewsPerDay = 50)
        val goal = DailyGoalCalculator.goal(limits, newDoneToday = 5, newAvailable = 3, reviewsDoneToday = 10, dueNow = 60)
        assertEquals(GoalPart(5, 8), goal.newItems) // only 3 new items left
        assertEquals(GoalPart(10, 50), goal.reviews) // capped by the daily limit
        assertFalse(goal.reached)
        val done = DailyGoalCalculator.goal(limits, 0, 0, 4, 0)
        assertTrue(done.reached)
        assertEquals(1f, done.newItems.fraction)
    }
}
