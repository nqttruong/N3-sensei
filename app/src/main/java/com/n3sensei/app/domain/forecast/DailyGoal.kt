package com.n3sensei.app.domain.forecast

import com.n3sensei.app.domain.srs.DailyLimits

/** Target and progress for one part of today's goal (REAL counts). */
data class GoalPart(val done: Int, val target: Int) {
    val fraction: Float get() = if (target == 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
    val reached: Boolean get() = done >= target
    val remaining: Int get() = (target - done).coerceAtLeast(0)
}

data class DailyGoal(val newItems: GoalPart, val reviews: GoalPart) {
    val reached: Boolean get() = newItems.reached && reviews.reached

    /** Cards a review session started now would hold (same rule as the session queue). */
    val sessionSize: Int get() = newItems.remaining + reviews.remaining
}

/**
 * Today's goal from the daily limits and real data:
 * - new items: the daily new limit, but never more than what has been started today plus what
 *   is still available;
 * - reviews: reviews answered today plus everything due now, capped by the daily review limit.
 *   The target can grow during the day when items fall due (e.g. after "Quên").
 */
object DailyGoalCalculator {
    fun goal(limits: DailyLimits, newDoneToday: Int, newAvailable: Int, reviewsDoneToday: Int, dueNow: Int): DailyGoal =
        DailyGoal(
            newItems = GoalPart(newDoneToday, minOf(limits.newPerDay, newDoneToday + newAvailable)),
            reviews = GoalPart(reviewsDoneToday, minOf(limits.reviewsPerDay, reviewsDoneToday + dueNow)),
        )
}
