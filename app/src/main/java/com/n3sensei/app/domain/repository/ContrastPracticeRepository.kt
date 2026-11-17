package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.PendingReview
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SessionStatus
import java.time.Instant

/** Result of [ContrastPracticeRepository.recordAnswer]. */
enum class RecordAnswerResult {
    /** The attempt (and the review/progress, if any) were written. */
    RECORDED,

    /** An attempt with the same request id exists: nothing written (a repeated submit). */
    DUPLICATE,

    /** The position already has an attempt with another request id: nothing written. */
    ALREADY_ANSWERED,
}

/**
 * Practice sessions and answers (Contrast's own tables; never error_records / review_items of
 * the exam module). Attempts are immutable: there is no delete.
 */
interface ContrastPracticeRepository {
    suspend fun activeSession(): ContrastSession?

    suspend fun session(sessionId: String): ContrastSession?

    /** Stores [session] (ACTIVE); an ACTIVE session already there becomes ABANDONED in the same transaction. */
    suspend fun createSession(session: ContrastSession)

    /** Adds re-asks at the end of an ACTIVE session (positions continue the sequence). */
    suspend fun appendItems(sessionId: String, items: List<SessionItem>, now: Instant)

    /**
     * Writes [attempt]; when [review] is given (the attempt completed a block), writes the review
     * and the group's progress in the SAME transaction, so an answer and its scheduling are never
     * stored apart. Updates the session's last activity.
     */
    suspend fun recordAnswer(attempt: ContrastAttempt, review: PendingReview?): RecordAnswerResult

    suspend fun finishSession(sessionId: String, status: SessionStatus, now: Instant)

    suspend fun attempts(sessionId: String): List<ContrastAttempt>

    /** Last answer time of each question of [groupId] the learner answered (for the planner). */
    suspend fun lastAnsweredAt(groupId: String): Map<String, Instant>

    /** Attempts answered in [from, to) (both null: all), oldest first; for error analysis. */
    suspend fun attemptsBetween(from: Instant?, to: Instant?): List<ContrastAttempt>

    /** Every attempt of [groupId], oldest first; for mastery and delayed-review classification. */
    suspend fun attemptsOfGroup(groupId: String): List<ContrastAttempt>
}
