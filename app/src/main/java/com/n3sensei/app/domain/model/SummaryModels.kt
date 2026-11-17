package com.n3sensei.app.domain.model

import com.n3sensei.app.domain.srs.TodayCounts

/** Amount of content currently installed. All zero when no content pack is loaded. */
data class ContentTotals(
    val lessons: Int,
    val vocabulary: Int,
    val kanji: Int,
    val grammar: Int,
) {
    val items: Int get() = vocabulary + kanji + grammar
    val isEmpty: Boolean get() = lessons == 0 && items == 0
}

/** Counts computed from stored progress; items whose content was removed are excluded. */
data class ProgressSummary(
    val trackedItems: Int,
    val learning: Int,
    val inReview: Int,
    val mastered: Int,
    val dueNow: Int,
    val totalReviews: Int,
)

enum class SubmitOutcome {
    APPLIED,

    /** The same answer (request id) was already saved; nothing changed. */
    DUPLICATE,

    /**
     * A first answer to a new item whose type already introduced its daily number of new items
     * (checked inside the write, so two sessions at once cannot go over); nothing was saved.
     */
    NEW_LIMIT_REACHED,
}

/**
 * Today's cap for a new item's type and the local day it is counted in: a first answer is
 * saved only while fewer than [limit] items of that type were introduced in [dayStart, dayEnd).
 */
data class NewItemLimit(val limit: Int, val dayStart: java.time.Instant, val dayEnd: java.time.Instant)

/**
 * Review counts. Every item counts once whatever its type; the `…ByType` maps break the totals
 * down (a missing type counts 0). [overdue] is the part of the due items that fell due before
 * today's local midnight.
 */
data class ReviewOverview(
    val dueByType: Map<ItemType, Int>,
    val overdue: Int,
    val today: TodayCounts,
    /** Items never answered (kanji: only those of the lessons chosen for review), before applying the daily new-item limits. */
    val newAvailable: Int,
    val overdueByType: Map<ItemType, Int> = emptyMap(),
    /** Distinct items answered today (local day), new or review. */
    val doneTodayByType: Map<ItemType, Int> = emptyMap(),
    val newByType: Map<ItemType, Int> = emptyMap(),
    /** Items whose last answer was "Quên" (due or not yet). */
    val forgottenByType: Map<ItemType, Int> = emptyMap(),
) {
    val due: Int get() = dueByType.values.sum()
    val forgotten: Int get() = forgottenByType.values.sum()
}
