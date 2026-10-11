package com.n3sensei.app.domain.forecast

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.sqrt

enum class Confidence { LOW, MEDIUM, HIGH }

/**
 * ESTIMATE of when every not-yet-started item will have been started, from the recent pace.
 * "Started" means answered once; it is not mastery and says nothing about passing JLPT N3.
 */
sealed interface ProgressEstimate {
    /** Nothing left to start. */
    data object AllStarted : ProgressEstimate

    /** Not enough recent history to estimate a pace; [settingsDays] uses the daily new limit instead. */
    data class NotEnoughData(val remaining: Int, val studyDays: Int, val newItems: Int, val settingsDays: Int?) : ProgressEstimate

    /**
     * @param fastestDays / slowestDays range from the best and worst recent week; slowestDays
     *   null when some week had no new items (no upper bound).
     */
    data class Estimate(
        val remaining: Int,
        val itemsPerDay: Double,
        val expectedDays: Int,
        val fastestDays: Int,
        val slowestDays: Int?,
        val confidence: Confidence,
        val settingsDays: Int?,
    ) : ProgressEstimate
}

/**
 * Pace = new items started per calendar day over the last [WINDOW_DAYS] days (idle days
 * count, so the pace is realistic). Requires at least [MIN_STUDY_DAYS] study days and
 * [MIN_NEW_ITEMS] new items in the window. Confidence grows with study days and falls with
 * week-to-week variation.
 */
object ProgressEstimator {
    const val WINDOW_DAYS = 28
    const val MIN_STUDY_DAYS = 7
    const val MIN_NEW_ITEMS = 20

    /**
     * @param newItemsByDay new items started on each of the last [WINDOW_DAYS] local days, oldest first.
     * @param studyDays local days in the window with any answer.
     */
    fun estimate(remaining: Int, newItemsByDay: List<Int>, studyDays: Int, newPerDaySetting: Int): ProgressEstimate {
        if (remaining <= 0) return ProgressEstimate.AllStarted
        val settingsDays = if (newPerDaySetting > 0) ceil(remaining.toDouble() / newPerDaySetting).toInt() else null
        val total = newItemsByDay.sum()
        if (studyDays < MIN_STUDY_DAYS || total < MIN_NEW_ITEMS) {
            return ProgressEstimate.NotEnoughData(remaining, studyDays, total, settingsDays)
        }
        val perDay = total.toDouble() / newItemsByDay.size
        val weekly = newItemsByDay.chunked(7).filter { it.size == 7 }.map { it.sum() / 7.0 }
        val fastest = weekly.maxOrNull() ?: perDay
        val slowest = weekly.minOrNull() ?: perDay
        return ProgressEstimate.Estimate(
            remaining = remaining,
            itemsPerDay = perDay,
            expectedDays = days(remaining, perDay)!!,
            fastestDays = days(remaining, fastest)!!,
            slowestDays = days(remaining, slowest),
            confidence = confidence(studyDays, weekly),
            settingsDays = settingsDays,
        )
    }

    private fun days(remaining: Int, perDay: Double): Int? = if (perDay <= 0.0) null else ceil(remaining / perDay).toInt()

    private fun confidence(studyDays: Int, weekly: List<Double>): Confidence {
        val mean = weekly.average()
        val cv = if (weekly.size < 2 || mean == 0.0) 1.0 else sqrt(weekly.sumOf { (it - mean) * (it - mean) } / weekly.size) / mean
        return when {
            studyDays >= 20 && cv <= 0.25 -> Confidence.HIGH
            studyDays >= 12 && cv <= 0.6 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    /** Calendar date [days] after [today] (the estimate itself is shown as a range of days). */
    fun dateAfter(today: LocalDate, days: Int): LocalDate = today.plusDays(days.toLong())
}
