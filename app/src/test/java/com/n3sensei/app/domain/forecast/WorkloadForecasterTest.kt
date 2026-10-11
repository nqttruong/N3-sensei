package com.n3sensei.app.domain.forecast

import com.n3sensei.app.domain.forecast.ForecastHorizon.NEXT_14_DAYS
import com.n3sensei.app.domain.forecast.ForecastHorizon.NEXT_30_DAYS
import com.n3sensei.app.domain.forecast.ForecastHorizon.NEXT_7_DAYS
import com.n3sensei.app.domain.forecast.ForecastHorizon.TOMORROW
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Known schedule, default ladder (20m, 1h, 1d, 2d, 1w, 2w, 1mo), Asia/Ho_Chi_Minh,
 * now = 11/10/2026 10:00. Horizons start tomorrow 00:00.
 *
 *   a: step 2 (1d), due 12/10 09:00   b: step 4 (1w), due 15/10 20:00
 *   c: step 5 (2w), due 20/10 08:00   d: step 6 (1mo), due 05/11 08:00
 *   e: step 0, due 11/10 09:00 (already due → assumed reviewed now)
 *
 * Scheduled (real): tomorrow a = 1; 7 days a b = 2; 14 days a b c = 3; 30 days a b c d = 4.
 * Simulated reviews, every answer "Tốt" on the due day:
 *   a: 12/10, 14/10, 21/10, 04/11      b: 15/10, 29/10      c: 20/10      d: 05/11
 *   e: now → 11:00 today → 12/10 11:00, 14/10, 21/10, 04/11
 * → tomorrow 2; 7 days 5; 14 days 8; 30 days 12.
 */
class WorkloadForecasterTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String, z: ZoneId = zone): Instant = LocalDateTime.parse(local).atZone(z).toInstant()
    private val now = at("2026-10-11T10:00")
    private val forecaster = WorkloadForecaster(ReviewLadder.DEFAULT)

    private val schedule = listOf(
        ScheduledItem(2, at("2026-10-12T09:00")),
        ScheduledItem(4, at("2026-10-15T20:00")),
        ScheduledItem(5, at("2026-10-20T08:00")),
        ScheduledItem(6, at("2026-11-05T08:00")),
        ScheduledItem(0, at("2026-10-11T09:00")),
    )
    private val noNew = ForecastAssumptions(newPerDay = 0, newAvailable = 0)

    private fun List<HorizonForecast>.of(h: ForecastHorizon) = single { it.horizon == h }

    @Test
    fun scheduledAndEstimatedReviews_matchHandCount() {
        val f = forecaster.forecast(schedule, noNew, now, zone)
        assertEquals(listOf(1, 2, 3, 4), f.map { it.scheduledItems })
        assertEquals(listOf(2, 5, 8, 12), f.map { it.estimatedReviews })
        assertEquals(listOf(0, 0, 0, 0), f.map { it.estimatedNewItems })
        assertEquals(listOf(TOMORROW, NEXT_7_DAYS, NEXT_14_DAYS, NEXT_30_DAYS), f.map { it.horizon })
    }

    @Test
    fun newItems_followDailyLimit_untilContentRunsOut() {
        // 3 per day from today, 10 available: today 3, then 3, 3, 1.
        val f = forecaster.forecast(emptyList(), ForecastAssumptions(newPerDay = 3, newAvailable = 10), now, zone)
        assertEquals(listOf(3, 7, 7, 7), f.map { it.estimatedNewItems })
    }

    @Test
    fun newItems_generateEstimatedReviews_onTheLadder() {
        // One item started now: first review 11:00 today, then 12/10, 14/10, 21/10, 04/11.
        val f = forecaster.forecast(emptyList(), ForecastAssumptions(newPerDay = 1, newAvailable = 1), now, zone)
        assertEquals(listOf(1, 2, 3, 4), f.map { it.estimatedReviews })
        assertEquals(listOf(0, 0, 0, 0), f.map { it.scheduledItems })
    }

    @Test
    fun emptyDatabase_isAllZeros() {
        val f = forecaster.forecast(emptyList(), noNew, now, zone)
        assertEquals(4, f.size)
        assertTrue(f.all { it.scheduledItems == 0 && it.estimatedReviews == 0 && it.estimatedNewItems == 0 })
    }

    @Test
    fun changedSchedule_changesTheForecast() {
        // Item a answered "Quên" today: now due in 20 minutes at step 0 instead of tomorrow at step 2.
        val changed = schedule.toMutableList().apply { set(0, ScheduledItem(0, now.plus(Duration.ofMinutes(20)))) }
        val f = forecaster.forecast(changed, noNew, now, zone)
        // a: 10:20 today → 11:20 → 12/10 11:20, 14/10, 21/10, 04/11: same review dates, other times.
        assertEquals(listOf(0, 1, 2, 3), f.map { it.scheduledItems })
        assertEquals(listOf(2, 5, 8, 12), f.map { it.estimatedReviews })
    }

    @Test
    fun changedLadder_changesEstimates_notScheduledCounts() {
        val custom = WorkloadForecaster(ReviewLadder.of(listOf("1d", "3d", "10d").map { ReviewInterval.parse(it)!! }))
        val f = custom.forecast(schedule, noNew, now, zone)
        assertEquals(listOf(1, 2, 3, 4), f.map { it.scheduledItems })
        // Stored steps beyond the ladder are clamped to its last step (10d).
        // a: 12/10, 22/10, 01/11 · b: 15/10, 25/10, 04/11 · c: 20/10, 30/10, 09/11 · d: 05/11
        // e (step 0): now (today), then step 1 = 3d → 14/10, step 2 = 10d → 24/10, 03/11
        // tomorrow: a = 1 · 7 days: a, b, e = 3 · 14 days: 2 + 2 + 1 + 2 = 7 · 30 days: 3 + 3 + 3 + 1 + 3 = 13
        assertEquals(listOf(1, 3, 7, 13), f.map { it.estimatedReviews })
    }

    @Test
    fun windows_startAtTomorrowMidnight_evenOneMinuteBeforeMidnight() {
        val late = at("2026-10-11T23:59")
        val (from, to) = forecaster.window(TOMORROW, late, zone)
        assertEquals(at("2026-10-12T00:00"), from)
        assertEquals(at("2026-10-13T00:00"), to)
        val dueAtMidnight = listOf(ScheduledItem(3, at("2026-10-12T00:00")), ScheduledItem(3, at("2026-10-11T23:59:59")))
        assertEquals(1, forecaster.forecast(dueAtMidnight, noNew, late, zone).of(TOMORROW).scheduledItems)
    }

    @Test
    fun monthEnd_andDaylightSaving_areCalendarCorrect() {
        // Mastered item due 31/01: next monthly review 28/02 (calendar month, clamped).
        val janNow = at("2027-01-30T12:00")
        val f = forecaster.forecast(listOf(ScheduledItem(6, at("2027-01-31T08:00"))), noNew, janNow, zone)
        assertEquals(1, f.of(TOMORROW).scheduledItems)
        assertEquals(2, f.of(NEXT_30_DAYS).estimatedReviews) // 31/01 and 28/02 (window ends 02/03 00:00)

        val berlin = ZoneId.of("Europe/Berlin")
        val (from, to) = forecaster.window(NEXT_7_DAYS, at("2026-10-23T12:00", berlin), berlin)
        assertEquals(7 * 24L + 1, Duration.between(from, to).toHours()) // includes the 25-hour day
    }
}
