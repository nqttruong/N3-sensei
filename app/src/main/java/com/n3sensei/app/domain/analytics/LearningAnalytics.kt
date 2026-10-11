package com.n3sensei.app.domain.analytics

import com.n3sensei.app.domain.model.ReviewRating
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Time range of the Statistics screen; days are local calendar days. */
enum class StatsPeriod(val days: Int?) {
    TODAY(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    ALL_TIME(null),
    ;

    /** [from, to): from local midnight [days]-1 days ago (null = no lower bound) to tomorrow's midnight. */
    fun range(now: Instant, zone: ZoneId): Pair<Instant?, Instant> {
        val today = now.atZone(zone).toLocalDate()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        val from = days?.let { today.minusDays((it - 1).toLong()).atStartOfDay(zone).toInstant() }
        return from to to
    }

    /** Days shown in the daily chart: the period itself, or 30 days for "today" / "all". */
    val chartDays: Int get() = when (this) {
        TODAY -> 7
        ALL_TIME -> 30
        else -> days!!
    }
}

data class DayActivity(val date: LocalDate, val answers: Int, val newItems: Int)

data class Streak(val current: Int, val longest: Int, val studiedToday: Boolean)

object LearningAnalytics {

    /** Size of the time blocks the database groups answers into. Every real UTC offset is a multiple. */
    const val BUCKET_MILLIS = 15 * 60 * 1000L

    /**
     * Recall rate: review answers rated Khó, Tốt or Dễ divided by all review answers (first
     * answers to new items are not recall). Null when there were no review answers.
     */
    fun recallRate(reviewRatings: Map<ReviewRating, Int>): Double? {
        val total = reviewRatings.values.sum()
        if (total == 0) return null
        val remembered = total - (reviewRatings[ReviewRating.AGAIN] ?: 0)
        return remembered.toDouble() / total
    }

    /** Daily totals for [days] local days ending today, zero-filled, oldest first. */
    fun dailySeries(buckets: List<TimeBucket>, zone: ZoneId, today: LocalDate, days: Int): List<DayActivity> {
        val byDate = buckets.groupBy { it.start.atZone(zone).toLocalDate() }
        return (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val list = byDate[date].orEmpty()
            DayActivity(date, list.sumOf { it.answers }, list.sumOf { it.newItems })
        }
    }

    /**
     * Items falling due on each of the next [days] local days, today first. Today also gets
     * everything already due ([dueNow]). An estimate: it assumes no reviews before then, and
     * answers will reschedule items.
     */
    fun forecast(dueBuckets: List<TimeBucket>, dueNow: Int, zone: ZoneId, today: LocalDate, days: Int): List<DayActivity> {
        val byDate = dueBuckets.groupBy { it.start.atZone(zone).toLocalDate() }
        return (0 until days).map { ahead ->
            val date = today.plusDays(ahead.toLong())
            val scheduled = byDate[date].orEmpty().sumOf { it.answers }
            DayActivity(date, scheduled + if (ahead == 0) dueNow else 0, 0)
        }
    }

    /** Local days with at least one answer. */
    fun studyDays(buckets: List<Instant>, zone: ZoneId): Set<LocalDate> =
        buckets.mapTo(mutableSetOf()) { it.atZone(zone).toLocalDate() }

    /**
     * A study day is a local calendar day with at least one answer. The current streak counts
     * consecutive study days ending today, or ending yesterday when today has no answer yet
     * (the streak is not lost until a whole day is missed).
     */
    fun streak(studyDays: Set<LocalDate>, today: LocalDate): Streak {
        val studiedToday = today in studyDays
        var cursor = if (studiedToday) today else today.minusDays(1)
        var current = 0
        while (cursor in studyDays) {
            current++
            cursor = cursor.minusDays(1)
        }
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in studyDays.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        return Streak(current, maxOf(longest, current), studiedToday)
    }
}
