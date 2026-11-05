package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AttemptResult
import com.n3sensei.app.domain.exam.Confidence
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorReview
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ExamAnswer
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamSetSummary
import kotlinx.coroutines.flow.Flow

/**
 * JLPT exams: exam sets (content), attempts and answers, and the learner's error notebook with
 * its review state. Everything is in Room; nothing needs a network. Attempts, answers and
 * errors are kept when an exam set is saved again or deleted.
 */
interface ExamRepository {
    fun observeExamSets(): Flow<List<ExamSetSummary>>

    suspend fun getExamSet(id: String): ExamSet?

    /** Saves (or replaces) a whole exam set. Invalid questions are refused: the result lists them. */
    suspend fun saveExamSet(set: ExamSet): SaveExamResult

    /** Deletes the set's content; its attempts, answers and errors stay. */
    suspend fun deleteExamSet(id: String): Boolean

    /** Starts a new attempt (several per set are kept); null if the set does not exist. */
    suspend fun startAttempt(examSetId: String): ExamAttempt?

    /**
     * Saves or changes the answer to one question of an attempt in progress, graded at once.
     * Null when the attempt is not in progress or the question is not in its set.
     */
    suspend fun answer(attemptId: String, questionId: String, input: AnswerInput, timeSpentMs: Long, confidence: Confidence? = null): ExamAnswer?

    /**
     * Ends an attempt: unanswered questions count as wrong, the score is stored, and every
     * wrong answer goes to the error notebook (created, or counted again) with a review due now.
     * Null when the attempt is not in progress.
     */
    suspend fun submit(attemptId: String): AttemptResult?

    /** Leaves an attempt without a score; its answers stay, nothing goes to the notebook. */
    suspend fun abandon(attemptId: String): Boolean

    suspend fun getAttempt(id: String): ExamAttempt?

    suspend fun getAnswers(attemptId: String): List<ExamAnswer>

    /** Attempts of one set (or of all sets when null), latest first. */
    fun observeAttempts(examSetId: String? = null): Flow<List<ExamAttempt>>

    /** Error notebook, most recent mistake first; [status] null for all. */
    fun observeErrors(status: ErrorStatus? = ErrorStatus.ACTIVE): Flow<List<ErrorRecord>>

    suspend fun getErrorForQuestion(questionId: String): ErrorRecord?

    suspend fun setErrorNotes(errorId: Long, notes: String?): Boolean

    suspend fun setErrorStatus(errorId: Long, status: ErrorStatus): Boolean

    /** Removes an error and its review state (the attempts that recorded it stay). */
    suspend fun deleteError(errorId: Long): Boolean

    /** Active errors whose review is due at [nowMillis], earliest first. */
    fun observeDueReviews(nowMillis: Long): Flow<List<ErrorReview>>
}

sealed interface SaveExamResult {
    data object Saved : SaveExamResult

    /** Question id → problems (see ExamGrading.problems); nothing was written. */
    data class Invalid(val problems: Map<String, List<String>>) : SaveExamResult
}
