package com.n3sensei.app.domain.srs

/**
 * Ordered review intervals. An item sits on one step; answers move it up or down (see
 * [SpacedRepetitionScheduler]). Always valid: build with [of] after [validate].
 */
class ReviewLadder private constructor(val intervals: List<ReviewInterval>) {

    val size: Int get() = intervals.size
    val lastIndex: Int get() = intervals.lastIndex

    operator fun get(step: Int): ReviewInterval = intervals[step.coerceIn(0, lastIndex)]

    /** First step of a day or longer; items at or above it are in long-term review. */
    val reviewStartIndex: Int = intervals.indexOfFirst { it.isCalendarBased }.let { if (it < 0) intervals.size else it }

    /** Clamps a stored step to this ladder (the ladder may have been shortened since). */
    fun clampStep(step: Int): Int = step.coerceIn(0, lastIndex)

    fun encode(): String = ReviewInterval.encodeList(intervals)

    override fun equals(other: Any?): Boolean = other is ReviewLadder && other.intervals == intervals
    override fun hashCode(): Int = intervals.hashCode()
    override fun toString(): String = "ReviewLadder(${encode()})"

    enum class Problem { EMPTY, TOO_MANY_STEPS, NOT_INCREASING, TOO_LONG }

    data class Validation(val problem: Problem, val index: Int? = null)

    companion object {
        const val MAX_STEPS = 12

        /** One year: longer steps would hide items for too long to be useful. */
        const val MAX_NOMINAL_MINUTES = 366L * 24 * 60

        /** 20 phút, 1 giờ, 1 ngày, 2 ngày, 1 tuần, 2 tuần, 1 tháng. */
        val DEFAULT = ReviewLadder(
            listOf(
                ReviewInterval(20, IntervalUnit.MINUTES),
                ReviewInterval(1, IntervalUnit.HOURS),
                ReviewInterval(1, IntervalUnit.DAYS),
                ReviewInterval(2, IntervalUnit.DAYS),
                ReviewInterval(1, IntervalUnit.WEEKS),
                ReviewInterval(2, IntervalUnit.WEEKS),
                ReviewInterval(1, IntervalUnit.MONTHS),
            ),
        )

        /** Problems with [intervals]; empty when they form a valid ladder. */
        fun validate(intervals: List<ReviewInterval>): List<Validation> {
            if (intervals.isEmpty()) return listOf(Validation(Problem.EMPTY))
            val problems = mutableListOf<Validation>()
            if (intervals.size > MAX_STEPS) problems += Validation(Problem.TOO_MANY_STEPS)
            intervals.forEachIndexed { i, interval ->
                if (interval.nominalMinutes > MAX_NOMINAL_MINUTES) problems += Validation(Problem.TOO_LONG, i)
                if (i > 0 && interval.nominalMinutes <= intervals[i - 1].nominalMinutes) {
                    problems += Validation(Problem.NOT_INCREASING, i)
                }
            }
            return problems
        }

        fun of(intervals: List<ReviewInterval>): ReviewLadder {
            val problems = validate(intervals)
            require(problems.isEmpty()) { "Invalid ladder: $problems" }
            return ReviewLadder(intervals.toList())
        }

        /** Null when [text] does not parse or is not a valid ladder. */
        fun decodeOrNull(text: String): ReviewLadder? {
            val intervals = ReviewInterval.parseList(text) ?: return null
            return if (validate(intervals).isEmpty()) ReviewLadder(intervals) else null
        }

        /** Stored settings that fail to parse or validate fall back to [DEFAULT]. */
        fun decodeOrDefault(text: String?): ReviewLadder {
            val intervals = text?.let { ReviewInterval.parseList(it) } ?: return DEFAULT
            return if (validate(intervals).isEmpty()) ReviewLadder(intervals) else DEFAULT
        }
    }
}
