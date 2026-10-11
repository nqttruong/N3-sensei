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
}

/**
 * Review counts. Every item counts once whatever its type; [dueByType] breaks the total down.
 * [overdue] is the part of the due items that fell due before today's local midnight.
 */
data class ReviewOverview(
    val dueByType: Map<ItemType, Int>,
    val overdue: Int,
    val today: TodayCounts,
    /** Items never answered, before applying the daily new-item limit. */
    val newAvailable: Int,
) {
    val due: Int get() = dueByType.values.sum()
}
