package com.n3sensei.app.domain.exam

import com.n3sensei.app.domain.repository.ExamRepository
import java.time.Clock

sealed interface SubmitOutcome {
    /** This call submitted the attempt (SUBMITTED, or EXPIRED when its time was up). */
    data class Submitted(val result: AttemptResult) : SubmitOutcome

    /** It was submitted before (by the timer, another tap…): the existing result, nothing recorded again. */
    data class AlreadySubmitted(val result: AttemptResult) : SubmitOutcome

    /** Abandoned or unknown: there is nothing to submit. */
    data object NotSubmittable : SubmitOutcome
}

/**
 * Submitting attempts, by the learner or when the time is up. Safe to call more than once:
 * an attempt gets one result and its mistakes are recorded once.
 */
class ExamSubmissionManager(private val exams: ExamRepository, private val clock: Clock) {

    suspend fun submit(attemptId: String): SubmitOutcome {
        exams.submit(attemptId)?.let { return SubmitOutcome.Submitted(it) }
        val existing = exams.getResult(attemptId)
        return if (existing != null && existing.attempt.status.isSubmitted) SubmitOutcome.AlreadySubmitted(existing) else SubmitOutcome.NotSubmittable
    }

    /** Submits [attempt] if its time is up; null when it still runs (or has no limit). */
    suspend fun submitIfExpired(attempt: ExamAttempt): SubmitOutcome? =
        if (attempt.status == AttemptStatus.IN_PROGRESS && ExamTimer.isExpired(attempt, clock.millis())) submit(attempt.id) else null

    /** Submits every attempt whose time ran out while the app was not running; returns their results. */
    suspend fun submitExpired(): List<AttemptResult> =
        exams.getInProgressAttempts().mapNotNull { attempt ->
            when (val outcome = submitIfExpired(attempt)) {
                is SubmitOutcome.Submitted -> outcome.result
                else -> null
            }
        }
}
