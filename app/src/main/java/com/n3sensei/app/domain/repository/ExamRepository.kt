package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AttemptResult
import com.n3sensei.app.domain.exam.Confidence
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorReview
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ExamAnswer
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamSetFilter
import com.n3sensei.app.domain.exam.ExamSetSummary
import com.n3sensei.app.domain.exam.ExamSkill
import kotlinx.coroutines.flow.Flow

/**
 * JLPT exams: exam sets (content), attempts and answers, and the learner's error notebook with
 * its review state. Everything is in Room; nothing needs a network. Attempts, answers and
 * errors are kept when an exam set is saved again or deleted.
 */
interface ExamRepository {
    /** Exam sets matching [filter] (level, skill, full or section exam), by level then age. */
    fun observeExamSets(filter: ExamSetFilter = ExamSetFilter()): Flow<List<ExamSetSummary>>

    suspend fun getExamSet(id: String): ExamSet?

    /** The set's questions in exam order, only those training [skill] when given; empty if no such set. */
    suspend fun getQuestions(examSetId: String, skill: ExamSkill? = null): List<ExamQuestion>

    /** Saves (or replaces) a whole exam set. Invalid questions are refused: the result lists them. */
    suspend fun saveExamSet(set: ExamSet): SaveExamResult

    /** Deletes the set's content; its attempts, answers and errors stay. */
    suspend fun deleteExamSet(id: String): Boolean

    /** Starts a new attempt (several per set are kept), on the first question; null if the set does not exist. */
    suspend fun startAttempt(examSetId: String): ExamAttempt?

    /**
     * Saves or changes the answer to one question of an attempt in progress, graded at once.
     * Null when the attempt is not in progress, its time is up (even before it was submitted)
     * or the question is not in its set.
     */
    suspend fun answer(attemptId: String, questionId: String, input: AnswerInput, timeSpentMs: Long, confidence: Confidence? = null): ExamAnswer?

    /**
     * Ends an attempt: unanswered questions count as wrong, the score is stored, and every
     * wrong answer goes to the error notebook (created, or counted again) with a review due now.
     * Once its time is up the attempt is EXPIRED, submitted as of its deadline. Checked and
     * written in one transaction, so of two submits at once only one does it. Null when the
     * attempt is not in progress (already submitted, abandoned or unknown).
     */
    suspend fun submit(attemptId: String): AttemptResult?

    /** Flags (or unflags) a question to look at again; false when the attempt takes no more changes. */
    suspend fun setFlag(attemptId: String, questionId: String, flagged: Boolean): Boolean

    /** Flagged questions of an attempt, in the order they were flagged. */
    suspend fun getFlags(attemptId: String): List<String>

    /** Remembers the question the learner is on; false when the attempt is not in progress. */
    suspend fun setCurrentQuestion(attemptId: String, questionId: String): Boolean

    /** Attempts not submitted yet (some may have run out of time while the app was closed), latest first. */
    suspend fun getInProgressAttempts(): List<ExamAttempt>

    /** The attempt and its answers; null if unknown. */
    suspend fun getResult(attemptId: String): AttemptResult?

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
