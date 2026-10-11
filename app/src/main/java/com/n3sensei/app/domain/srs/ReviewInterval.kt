package com.n3sensei.app.domain.srs

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Units of a review interval. [code] is the persisted suffix; never change it. */
enum class IntervalUnit(val code: String, val nominalMinutes: Long) {
    MINUTES("m", 1),
    HOURS("h", 60),
    DAYS("d", 24 * 60),
    WEEKS("w", 7 * 24 * 60),

    /** Calendar month; 30 days is used only for ordering and limits. */
    MONTHS("mo", 30 * 24 * 60),
    ;

    /** Days, weeks and months follow the calendar in the learner's time zone. */
    val isCalendarBased: Boolean get() = this >= DAYS
}

/**
 * One step of the review ladder, e.g. 20 minutes or 1 month.
 *
 * Time rule (shown in Settings):
 * - minutes and hours are exact elapsed time;
 * - days, weeks and months are calendar arithmetic in the device time zone, so "1 day" lands at
 *   the same wall-clock time tomorrow even across a daylight-saving change, and "1 month" is
 *   the same day next month, clamped to the month's last day (31 Jan + 1 month = 28/29 Feb).
 */
data class ReviewInterval(val amount: Int, val unit: IntervalUnit) {

    init {
        require(amount > 0) { "Interval amount must be positive" }
    }

    fun addTo(from: Instant, zone: ZoneId): Instant = when (unit) {
        IntervalUnit.MINUTES -> from.plus(Duration.ofMinutes(amount.toLong()))
        IntervalUnit.HOURS -> from.plus(Duration.ofHours(amount.toLong()))
        IntervalUnit.DAYS -> from.atZone(zone).plusDays(amount.toLong()).toInstant()
        IntervalUnit.WEEKS -> from.atZone(zone).plusWeeks(amount.toLong()).toInstant()
        IntervalUnit.MONTHS -> from.atZone(zone).plusMonths(amount.toLong()).toInstant()
    }

    /** Approximate length, for ordering and validation only. */
    val nominalMinutes: Long get() = amount * unit.nominalMinutes

    val isCalendarBased: Boolean get() = unit.isCalendarBased

    /** Persisted form, e.g. "20m", "1mo". */
    fun encode(): String = "$amount${unit.code}"

    companion object {
        private val PATTERN = Regex("""^(\d{1,4})(mo|m|h|d|w)$""")

        fun parse(text: String): ReviewInterval? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val amount = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val unit = IntervalUnit.entries.first { it.code == match.groupValues[2] }
            return ReviewInterval(amount, unit)
        }

        /** Parses "20m,1h,1d"; null if any element is malformed. */
        fun parseList(text: String): List<ReviewInterval>? {
            if (text.isBlank()) return null
            return text.split(',').map { parse(it) ?: return null }
        }

        fun encodeList(intervals: List<ReviewInterval>): String = intervals.joinToString(",") { it.encode() }
    }
}
