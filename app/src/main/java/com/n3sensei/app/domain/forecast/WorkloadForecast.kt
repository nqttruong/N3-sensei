package com.n3sensei.app.domain.forecast

import com.n3sensei.app.domain.srs.ReviewLadder
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One item on the stored schedule (seed/test and removed content excluded). */
data class ScheduledItem(val stepIndex: Int, val dueAt: Instant)

/** A forecast horizon: the next [days] local days, starting tomorrow at 00:00. */
enum class ForecastHorizon(val days: Int) {
    TOMORROW(1),
    NEXT_7_DAYS(7),
    NEXT_14_DAYS(14),
    NEXT_30_DAYS(30),
}

/**
 * Workload for one horizon.
 *
 * @property scheduledItems REAL: items whose stored due date falls in the horizon (each item once).
 * @property estimatedReviews ESTIMATE: review answers expected in the horizon under [ForecastAssumptions].
 * @property estimatedNewItems ESTIMATE: new items expected to be started in the horizon.
 */
data class HorizonForecast(
    val horizon: ForecastHorizon,
    val scheduledItems: Int,
    val estimatedReviews: Int,
    val estimatedNewItems: Int,
)

/**
 * Stated assumptions of the estimate (shown in the app):
 * - every due item is reviewed on its due day (overdue items today) and answered "Tốt", so it
 *   moves up one ladder step each time;
 * - [newPerDay] new items are started every day (today included) until [newAvailable] run out,
 *   each also answered "Tốt";
 * - daily review limits and time of day are ignored.
 */
data class ForecastAssumptions(val newPerDay: Int, val newAvailable: Int)

/**
 * Workload forecast from the stored schedule. "Scheduled" numbers are facts read from the
 * database; "estimated" numbers simulate the stated assumptions with the user's own ladder.
 * Kept separate from any adaptive model so one can be plugged in later.
 */
class WorkloadForecaster(private val ladder: ReviewLadder) {

    /** [from, to) of [horizon]: from tomorrow 00:00 to the end of the last day, local time. */
    fun window(horizon: ForecastHorizon, now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        return tomorrow.atStartOfDay(zone).toInstant() to tomorrow.plusDays(horizon.days.toLong()).atStartOfDay(zone).toInstant()
    }

    fun forecast(
        schedule: List<ScheduledItem>,
        assumptions: ForecastAssumptions,
        now: Instant,
        zone: ZoneId,
        horizons: List<ForecastHorizon> = ForecastHorizon.entries,
    ): List<HorizonForecast> {
        val longest = horizons.maxOf { it.days }
        val (_, simulationEnd) = window(horizons.first { it.days == longest }, now, zone)
        val reviewTimes = simulateReviews(schedule, assumptions, now, zone, simulationEnd)
        val newPerDayList = newItemsPerDay(assumptions, longest + 1)

        return horizons.map { horizon ->
            val (from, to) = window(horizon, now, zone)
            HorizonForecast(
                horizon = horizon,
                scheduledItems = schedule.count { !it.dueAt.isBefore(from) && it.dueAt.isBefore(to) },
                estimatedReviews = reviewTimes.count { !it.isBefore(from) && it.isBefore(to) },
                // Day 0 of newPerDayList is today; the horizon covers days 1..days.
                estimatedNewItems = newPerDayList.drop(1).take(horizon.days).sum(),
            )
        }
    }

    /** All simulated review times before [end]: each answer is "Tốt" on the due day. */
    private fun simulateReviews(
        schedule: List<ScheduledItem>,
        assumptions: ForecastAssumptions,
        now: Instant,
        zone: ZoneId,
        end: Instant,
    ): List<Instant> {
        val times = mutableListOf<Instant>()
        fun follow(startStep: Int, firstDue: Instant) {
            var step = ladder.clampStep(startStep)
            // Overdue items are assumed to be reviewed now.
            var due = if (firstDue.isBefore(now)) now else firstDue
            while (due.isBefore(end)) {
                times += due
                step = ladder.clampStep(step + 1)
                due = ladder[step].addTo(due, zone)
            }
        }
        schedule.forEach { follow(it.stepIndex, it.dueAt) }
        // New items started each day at the same time as now (or at local midnight for future days).
        newItemsPerDay(assumptions, daysUntil(now, end, zone)).forEachIndexed { dayIndex, count ->
            val startedAt = if (dayIndex == 0) now else now.atZone(zone).toLocalDate().plusDays(dayIndex.toLong()).atStartOfDay(zone).toInstant()
            // First answer "Tốt" on a new item → step 1; its first review is ladder[1] later.
            val firstReview = ladder[1].addTo(startedAt, zone)
            repeat(count) { follow(1, firstReview) }
        }
        return times
    }

    private fun daysUntil(now: Instant, end: Instant, zone: ZoneId): Int {
        val today = now.atZone(zone).toLocalDate()
        val last = end.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, last).toInt() + 1
    }

    /** New items started on each local day from today, capped by what is left. */
    fun newItemsPerDay(assumptions: ForecastAssumptions, days: Int): List<Int> {
        var left = assumptions.newAvailable
        return List(days) {
            val n = minOf(assumptions.newPerDay, left).coerceAtLeast(0)
            left -= n
            n
        }
    }
}
