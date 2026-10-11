package com.n3sensei.app.domain.forecast

import com.n3sensei.app.domain.analytics.TimeBucket
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class TrendUnit(val periods: Int) {
    DAY(14),
    /** Weeks start on Monday (Vietnamese convention). */
    WEEK(8),
    MONTH(6),
}

/** Answers (REAL) in one day, week or month starting on [start]. */
data class TrendPoint(val start: LocalDate, val answers: Int, val newItems: Int)

object TrendAggregator {

    /** First day of the oldest period shown for [unit]. */
    fun firstDay(unit: TrendUnit, today: LocalDate): LocalDate = periodStart(unit, today).let {
        when (unit) {
            TrendUnit.DAY -> it.minusDays((unit.periods - 1).toLong())
            TrendUnit.WEEK -> it.minusWeeks((unit.periods - 1).toLong())
            TrendUnit.MONTH -> it.minusMonths((unit.periods - 1).toLong())
        }
    }

    fun periodStart(unit: TrendUnit, date: LocalDate): LocalDate = when (unit) {
        TrendUnit.DAY -> date
        TrendUnit.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TrendUnit.MONTH -> date.withDayOfMonth(1)
    }

    /** Zero-filled series of [TrendUnit.periods] periods ending with the current one, oldest first. */
    fun aggregate(buckets: List<TimeBucket>, zone: ZoneId, today: LocalDate, unit: TrendUnit): List<TrendPoint> {
        val grouped = buckets.groupBy { periodStart(unit, it.start.atZone(zone).toLocalDate()) }
        var start = firstDay(unit, today)
        return List(unit.periods) {
            val list = grouped[start].orEmpty()
            TrendPoint(start, list.sumOf { b -> b.answers }, list.sumOf { b -> b.newItems }).also {
                start = when (unit) {
                    TrendUnit.DAY -> start.plusDays(1)
                    TrendUnit.WEEK -> start.plusWeeks(1)
                    TrendUnit.MONTH -> start.plusMonths(1)
                }
            }
        }
    }
}
