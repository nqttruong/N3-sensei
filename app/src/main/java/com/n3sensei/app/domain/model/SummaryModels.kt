package com.n3sensei.app.domain.model

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
    val dueNow: Int,
    val totalReviews: Int,
)
