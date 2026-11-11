package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

/**
 * Today's answers so far. [newByType]: items of each type introduced today (their first answer);
 * [newAnswered] is their total. A type missing from the map introduced none.
 */
data class TodayCounts(
    val newAnswered: Int,
    val reviewsAnswered: Int,
    val newByType: Map<ItemType, Int> = emptyMap(),
)

/**
 * Builds a review session: due items first (in the order given: oldest due first), then new
 * items, each within what is left of today's limits. New items have one allowance per type,
 * computed from that type's own cap and that type's own count today: one type never uses or
 * frees another type's allowance, and there is no limit on their sum. The queue is fixed when
 * the session starts; items that fall due during the session are picked up by the next one.
 */
object ReviewQueuePlanner {
    fun remainingReviews(limits: DailyLimits, today: TodayCounts): Int =
        (limits.reviewsPerDay - today.reviewsAnswered).coerceAtLeast(0)

    /** New items of [type] that may still be introduced today. */
    fun remainingNew(limits: DailyLimits, today: TodayCounts, type: ItemType): Int =
        (limits.newPerDay(type) - (today.newByType[type] ?: 0)).coerceAtLeast(0)

    /**
     * [due] within today's review allowance, then [newItems] (in their given order) within each
     * type's own new allowance.
     */
    fun plan(due: List<ItemRef>, newItems: List<ItemRef>, limits: DailyLimits, today: TodayCounts): List<ItemRef> {
        val dueTaken = due.distinct().take(remainingReviews(limits, today))
        val left = ItemType.entries.associateWith { remainingNew(limits, today, it) }.toMutableMap()
        val newTaken = newItems.distinct().filterNot { it in dueTaken }.filter { ref ->
            val n = left.getValue(ref.type)
            if (n > 0) left[ref.type] = n - 1
            n > 0
        }
        return dueTaken + newTaken
    }
}
