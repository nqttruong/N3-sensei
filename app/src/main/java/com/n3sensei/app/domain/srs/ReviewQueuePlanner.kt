package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef

/** Today's answers so far, by kind. */
data class TodayCounts(val newAnswered: Int, val reviewsAnswered: Int)

/**
 * Builds a review session: due items first (oldest due first, all content types mixed), then
 * new items in course order, each within what is left of today's limits. The queue is fixed
 * when the session starts; items that fall due during the session are picked up by the next
 * session.
 */
object ReviewQueuePlanner {
    fun remainingReviews(limits: DailyLimits, today: TodayCounts): Int =
        (limits.reviewsPerDay - today.reviewsAnswered).coerceAtLeast(0)

    fun remainingNew(limits: DailyLimits, today: TodayCounts): Int =
        (limits.newPerDay - today.newAnswered).coerceAtLeast(0)

    fun plan(due: List<ItemRef>, newItems: List<ItemRef>, limits: DailyLimits, today: TodayCounts): List<ItemRef> {
        val dueTaken = due.distinct().take(remainingReviews(limits, today))
        val newTaken = newItems.distinct().filterNot { it in dueTaken }.take(remainingNew(limits, today))
        return dueTaken + newTaken
    }
}
