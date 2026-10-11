package com.n3sensei.app.domain.model

import java.time.Instant

/**
 * Stored scheduling state, derived by the scheduler from the ladder step after each answer.
 * Names are persisted; never rename an entry.
 */
enum class ProgressState {
    /** Short intervals (below one day). */
    LEARNING,

    /** Long-term review (intervals of a day or more). */
    REVIEW,

    /** At the last step of the ladder. Still reviewed when due. */
    MASTERED,
}

/** Learner's self-assessment after a review: Quên, Khó, Tốt, Dễ. Names are persisted. */
enum class ReviewRating { AGAIN, HARD, GOOD, EASY }

/** Personal progress for one item. Never stored on shared content records. */
data class LearningProgress(
    val item: ItemRef,
    val state: ProgressState,
    /** Position in the configured interval ladder. */
    val stepIndex: Int,
    val intervalMinutes: Long,
    val dueAt: Instant?,
    val lastReviewedAt: Instant?,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ReviewRecord(
    val id: Long = 0,
    val item: ItemRef,
    val rating: ReviewRating,
    val reviewedAt: Instant,
    val intervalBeforeMinutes: Long,
    val intervalAfterMinutes: Long,
    val dueAtAfter: Instant,
    /** True for the first answer ever given for the item (counts against the new-items limit). */
    val wasNew: Boolean = false,
    /** Identifies one answer of one card; a repeated submit with the same id is ignored. */
    val requestId: String? = null,
)
