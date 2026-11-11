package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

/** Today's answers so far, by kind. */
data class TodayCounts(val newAnswered: Int, val reviewsAnswered: Int)

/**
 * Builds a review session: due items first, then new items, each within what is left of
 * today's limits. The queue is fixed when the session starts; items that fall due during the
 * session are picked up by the next session.
 *
 * Mixed sessions take the types in turn ([interleave]): vocabulary, kanji, grammar, vocabulary…
 * each in its own order (oldest due first, new items in course order), so no type fills the
 * whole session while the others wait. A type with nothing left simply gives its turns away.
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

    /** One item of each type in turn (in [ItemType] order), keeping each type's own order. */
    fun interleave(byType: Map<ItemType, List<ItemRef>>): List<ItemRef> {
        val queues = ItemType.entries.mapNotNull { type -> byType[type]?.takeIf { it.isNotEmpty() }?.iterator() }
        val result = mutableListOf<ItemRef>()
        var active = queues
        while (active.isNotEmpty()) {
            active.forEach { result += it.next() }
            active = active.filter { it.hasNext() }
        }
        return result
    }
}
