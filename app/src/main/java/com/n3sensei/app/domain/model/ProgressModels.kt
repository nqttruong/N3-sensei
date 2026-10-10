package com.n3sensei.app.domain.model

import java.time.Instant

/** Names are persisted; never rename an entry. */
enum class ProgressState { LEARNING, REVIEW }

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
)
