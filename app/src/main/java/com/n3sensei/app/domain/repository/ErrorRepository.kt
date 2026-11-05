package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.mistakes.ErrorReviewEntry
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.ReviewQuestionKind
import kotlinx.coroutines.flow.Flow

/**
 * The error notebook: one entry per question missed in a submitted exam (never two for the
 * same question; questions on the same study item stay separate entries, linked by the analysis).
 */
interface ErrorRepository {
    fun observeErrors(): Flow<List<NotebookError>>

    fun observeError(id: Long): Flow<NotebookError?>

    fun observeReviewLog(id: Long): Flow<List<ErrorReviewEntry>>

    /** The entry of a question, if it was ever missed. */
    suspend fun findByQuestion(questionId: String): Long?

    suspend fun setImportant(id: Long, important: Boolean): Boolean

    /** Saves the learner's note (blank removes it). */
    suspend fun setNote(id: Long, note: String?): Boolean

    /** The learner says they understood it. Not mastery: only passing the reviews masters an error. */
    suspend fun setUnderstood(id: Long, understood: Boolean): Boolean

    /** Open errors due for review at [nowMillis], overdue ones included. */
    fun observeDueCount(nowMillis: Long): Flow<Int>

    /**
     * Grades [answer] against [question] (the error's own or another one on the same study item,
     * see ReviewQuestionPicker), records the review and reschedules (ErrorReviewSchedule).
     * Null if there is no such error.
     */
    suspend fun review(id: Long, question: ExamQuestion, kind: ReviewQuestionKind, answer: AnswerInput): ReviewResult?
}

data class ReviewResult(
    val correct: Boolean,
    val counted: Boolean,
    val mastered: Boolean,
    val nextReviewAt: Long,
    val stepBefore: Int,
    val stepAfter: Int,
    /** All levels passed on the original question only: another question is needed to master it. */
    val needsAnotherQuestion: Boolean = false,
)
