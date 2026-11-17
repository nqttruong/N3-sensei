package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ReviewRating
import java.time.Instant

// Learner data of Contrast Lab (DOMAIN_CONTRACT §1.2). Written by the app, never deleted when a
// pack is updated, and never mixed with learning_progress / review_records.

/**
 * Format and meaning of stored Contrast learner data (`progressVersion`). Distinct from a pack's
 * `contentVersion`. Raised only when the meaning of stored data changes, with a tested conversion.
 */
const val CONTRAST_PROGRESS_VERSION = 1

/** Outcome of an answer that was given. A question not answered has no attempt at all (PENDING). Names are stored. */
enum class AnswerOutcome { CORRECT, INCORRECT, SKIPPED }

/**
 * State of one session position, derived: an attempt gives its outcome; no attempt is [PENDING]
 * (not done yet, never counted as wrong); a question gone from the pack is [UNAVAILABLE].
 */
enum class PositionState { CORRECT, INCORRECT, SKIPPED, PENDING, UNAVAILABLE }

/**
 * One answer, immutable. Carries the content version and text snapshots so history stays readable
 * after the question changes or disappears. No answer duration is stored (rule R6).
 */
data class ContrastAttempt(
    /** Idempotency key (UUID): a repeated submit with the same id writes nothing. */
    val requestId: String,
    val sessionId: String,
    val position: Int,
    val groupId: String,
    val groupRevision: Int,
    val questionId: String,
    val questionRevision: Int,
    val questionType: ContrastQuestionType,
    val contentPackId: String,
    val contentVersion: Int,
    val outcome: AnswerOutcome,
    /** Null only when [outcome] is SKIPPED. */
    val chosenOptionId: String?,
    val correctOptionId: String,
    /** The question's target item at answer time. */
    val expectedItemId: String?,
    /** Item of the chosen option; for a missed ERROR_DETECTION, the item of the wrong segment that went unnoticed. */
    val chosenItemId: String?,
    val chosenText: String?,
    val correctText: String,
    val answeredAt: Instant,
) {
    init {
        require((outcome == AnswerOutcome.SKIPPED) == (chosenOptionId == null)) { "Only a skipped answer has no chosen option" }
    }

    /** The directed confusion this answer shows, if it chose another item of the group. */
    val confusion: Confusion?
        get() = if (outcome == AnswerOutcome.INCORRECT && expectedItemId != null && chosenItemId != null && chosenItemId != expectedItemId) {
            Confusion(expectedItemId, chosenItemId)
        } else {
            null
        }
}

/**
 * Progress and review schedule of one group (one row, like learning_progress). Exists only after
 * the group's first completed block: reading the theory creates nothing (rule R1).
 */
data class ContrastProgress(
    val groupId: String,
    /** Step on the Contrast ladder; clamped when the ladder gets shorter. */
    val stepIndex: Int,
    val dueAt: Instant,
    val lastReviewedAt: Instant,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Mastery of a group, computed from attempts and reviews (never stored). */
enum class ContrastMastery { NEW, LEARNING, REVIEWING, MASTERED }

/** One scheduling event (a completed block), immutable. The derived [rating] is kept so later rule changes do not reinterpret history. */
data class ContrastReview(
    val id: Long = 0,
    val groupId: String,
    val sessionId: String,
    val reviewedAt: Instant,
    val answered: Int,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    /** AGAIN, HARD or GOOD (never EASY: learners do not rate themselves). */
    val rating: ReviewRating,
    /** Null for a new group. */
    val stepBefore: Int?,
    val stepAfter: Int,
    val dueAtAfter: Instant,
    val contentVersion: Int,
)

/** A review and the progress it produces, written atomically with the attempt that completed the block. */
data class PendingReview(val review: ContrastReview, val progress: ContrastProgress)

enum class SessionStatus { ACTIVE, COMPLETED, ABANDONED }

/** What a session practises. */
sealed interface PracticeScope {
    /** Due groups first, then new ones within the daily limit. */
    data object Due : PracticeScope

    /**
     * Review only (M7 "Ôn tập ngay"): the groups due now on Contrast's own schedule, no new group,
     * so it never uses the daily new-group allowance.
     */
    data object Review : PracticeScope

    /** One group chosen by the learner (allowed even when not due). */
    data class Group(val groupId: String) : PracticeScope

    /** Due then new groups of one type. */
    data class Type(val contentType: ContrastContentType) : PracticeScope
}

/**
 * One planned question. [blockIndex] identifies the group's block in this session; it is null for
 * a re-ask of a missed question at the end, which never counts toward the block.
 */
data class SessionItem(
    val position: Int,
    val groupId: String,
    val questionId: String,
    val questionRevision: Int,
    val blockIndex: Int?,
)

/** A practice session; at most one is ACTIVE at a time. */
data class ContrastSession(
    val id: String,
    val scope: PracticeScope,
    val status: SessionStatus,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val contentVersion: Int,
    val items: List<SessionItem>,
) {
    init {
        require(items.map { it.position } == items.indices.toList()) { "Positions must be 0..n-1 in order" }
    }
}
