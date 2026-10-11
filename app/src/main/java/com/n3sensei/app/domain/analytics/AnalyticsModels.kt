package com.n3sensei.app.domain.analytics

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import java.time.Instant

/*
 * Counting rules (also shown in the app and in docs/ANALYTICS.md):
 * - Source: review_records (scheduled reviews). Practice mode is not recorded and not counted.
 * - Excluded: records of seed/test packs and of items no longer in the content.
 * - "Lượt" (answers) counts every answer; "mục" (items) counts distinct items.
 */

/** Activity in a period. */
data class ActivityTotals(
    /** Answers of any kind (lượt). */
    val answers: Int,
    /** Distinct items answered (mục). */
    val distinctItems: Int,
    /** Items answered for the first time ever in the period (mục; each item has one first answer). */
    val newItems: Int,
    /** Answers to items that already had progress (lượt). */
    val reviewAnswers: Int,
    /** Distinct items that already had progress and were answered again (mục). */
    val distinctReviewedItems: Int,
    /** Ratings of review answers only (first answers excluded). */
    val reviewRatings: Map<ReviewRating, Int>,
) {
    companion object {
        val EMPTY = ActivityTotals(0, 0, 0, 0, 0, emptyMap())
    }
}

/** Items due now; [overdue] is the part that fell due before today's local midnight. */
data class DueCounts(val due: Int, val overdue: Int)

/** Answers that fell into one 15-minute block (UTC-aligned); converted to local days in Kotlin. */
data class TimeBucket(val start: Instant, val answers: Int, val newItems: Int)

/** Items of one type by learning state; seed/test content excluded. */
data class StatusCounts(val new: Int, val learning: Int, val review: Int, val mastered: Int) {
    val total: Int get() = new + learning + review + mastered

    companion object {
        val EMPTY = StatusCounts(0, 0, 0, 0)
    }
}

/** An item often forgotten or found hard in the period. */
data class TroubleItem(
    val ref: ItemRef,
    /** Expression, kanji character or grammar pattern. */
    val label: String,
    val again: Int,
    val hard: Int,
    val lastAnsweredAt: Instant,
) {
    val total: Int get() = again + hard
}

/** All status counts by type. */
typealias StatusByType = Map<ItemType, StatusCounts>
