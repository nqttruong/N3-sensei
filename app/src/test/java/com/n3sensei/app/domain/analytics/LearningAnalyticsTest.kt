package com.n3sensei.app.domain.analytics

import com.n3sensei.app.domain.model.ReviewRating.AGAIN
import com.n3sensei.app.domain.model.ReviewRating.EASY
import com.n3sensei.app.domain.model.ReviewRating.GOOD
import com.n3sensei.app.domain.model.ReviewRating.HARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LearningAnalyticsTest {

    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String, zone: ZoneId = vietnam): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private val today = LocalDate.parse("2026-10-11")

    // --- Recall rate ---

    @Test
    fun recallRate_isNotForgottenOverAllReviewAnswers() {
        assertNull(LearningAnalytics.recallRate(emptyMap()))
        assertEquals(0.0, LearningAnalytics.recallRate(mapOf(AGAIN to 4))!!, 1e-9)
        // 1 Quên, 1 Khó, 2 Tốt, 0 Dễ → 3 of 4 remembered.
        assertEquals(0.75, LearningAnalytics.recallRate(mapOf(AGAIN to 1, HARD to 1, GOOD to 2, EASY to 0))!!, 1e-9)
    }

    // --- Periods and day boundaries ---

    @Test
    fun periods_coverWholeLocalDays_endingAtTomorrowsMidnight() {
        val now = at("2026-10-11T00:05")
        assertEquals(at("2026-10-11T00:00") to at("2026-10-12T00:00"), StatsPeriod.TODAY.range(now, vietnam))
        assertEquals(at("2026-10-05T00:00") to at("2026-10-12T00:00"), StatsPeriod.LAST_7_DAYS.range(now, vietnam))
        assertEquals(at("2026-09-12T00:00") to at("2026-10-12T00:00"), StatsPeriod.LAST_30_DAYS.range(now, vietnam))
        assertEquals(null to at("2026-10-12T00:00"), StatsPeriod.ALL_TIME.range(now, vietnam))
    }

    @Test
    fun period_acrossDaylightSavingChange_isStillWholeDays() {
        val berlin = ZoneId.of("Europe/Berlin")
        val (from, to) = StatsPeriod.LAST_7_DAYS.range(at("2026-10-27T10:00", berlin), berlin)
        assertEquals(at("2026-10-21T00:00", berlin), from)
        assertEquals(at("2026-10-28T00:00", berlin), to)
        assertEquals(7 * 24L + 1, Duration.between(from, to!!).toHours()) // one 25-hour day
    }

    @Test
    fun dailySeries_mapsBucketsToLocalDays_andFillsGaps() {
        val buckets = listOf(
            TimeBucket(at("2026-10-11T23:45"), answers = 2, newItems = 1), // last block of the day
            TimeBucket(at("2026-10-11T00:00"), answers = 1, newItems = 0), // first block of the day
            TimeBucket(at("2026-10-09T12:00"), answers = 3, newItems = 3),
        )
        val series = LearningAnalytics.dailySeries(buckets, vietnam, today, 4)
        assertEquals(listOf("2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"), series.map { it.date.toString() })
        assertEquals(listOf(0, 3, 0, 3), series.map { it.answers })
        assertEquals(listOf(0, 3, 0, 1), series.map { it.newItems })
    }

    @Test
    fun fifteenMinuteBlocks_mapCorrectlyInHalfAndQuarterHourZones() {
        // UTC-aligned blocks never straddle local midnight because every offset is a multiple of 15 min.
        val india = ZoneId.of("Asia/Kolkata") // +05:30
        val nepal = ZoneId.of("Asia/Kathmandu") // +05:45
        val block = Instant.parse("2026-10-10T18:30:00Z") // 00:00 on 11 Oct in India
        assertEquals(LocalDate.parse("2026-10-11"), block.atZone(india).toLocalDate())
        assertEquals(LocalDate.parse("2026-10-10"), block.minusMillis(LearningAnalytics.BUCKET_MILLIS).atZone(india).toLocalDate())
        val nepalMidnight = Instant.parse("2026-10-10T18:15:00Z")
        assertEquals(0L, nepalMidnight.toEpochMilli() % LearningAnalytics.BUCKET_MILLIS)
        assertEquals(LocalDate.parse("2026-10-11"), nepalMidnight.atZone(nepal).toLocalDate())
    }

    // --- Streak ---

    private fun days(vararg d: String) = d.map(LocalDate::parse).toSet()

    @Test
    fun streak_countsConsecutiveDaysEndingToday() {
        assertEquals(Streak(3, 3, true), LearningAnalytics.streak(days("2026-10-09", "2026-10-10", "2026-10-11"), today))
    }

    @Test
    fun streak_isKeptUntilAWholeDayIsMissed() {
        assertEquals(Streak(2, 2, false), LearningAnalytics.streak(days("2026-10-09", "2026-10-10"), today))
        assertEquals(Streak(0, 2, false), LearningAnalytics.streak(days("2026-10-08", "2026-10-09"), today))
    }

    @Test
    fun streak_longestRun_andEmpty() {
        val history = days("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04", "2026-10-10", "2026-10-11")
        assertEquals(Streak(2, 4, true), LearningAnalytics.streak(history, today))
        assertEquals(Streak(0, 0, false), LearningAnalytics.streak(emptySet(), today))
    }

    @Test
    fun studyDays_followTheZone() {
        val blocks = listOf(Instant.parse("2026-10-10T17:00:00Z")) // 00:00 on 11 Oct in Vietnam, 10 Oct in UTC
        assertEquals(days("2026-10-11"), LearningAnalytics.studyDays(blocks, vietnam))
        assertEquals(days("2026-10-10"), LearningAnalytics.studyDays(blocks, ZoneId.of("UTC")))
    }

    // --- Forecast ---

    @Test
    fun forecast_addsDueNowToToday_andGroupsByDay() {
        val buckets = listOf(
            TimeBucket(at("2026-10-11T20:00"), 2, 0),
            TimeBucket(at("2026-10-13T08:00"), 1, 0),
            TimeBucket(at("2026-10-13T21:00"), 4, 0),
        )
        val f = LearningAnalytics.forecast(buckets, dueNow = 5, zone = vietnam, today = today, days = 4)
        assertEquals(listOf(7, 0, 5, 0), f.map { it.answers })
    }
}
