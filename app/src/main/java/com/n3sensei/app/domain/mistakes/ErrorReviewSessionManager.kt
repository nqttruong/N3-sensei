package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.repository.ErrorRepository
import com.n3sensei.app.domain.repository.ExamRepository
import com.n3sensei.app.domain.repository.ReviewResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

/** One error to review in a session, with the question picked for it this time. */
data class ReviewCard(
    val error: NotebookError,
    val question: ReviewQuestion,
    val priority: ReviewPriority,
    /** How many questions the app has for this error (the original included). */
    val questionCount: Int,
)

/**
 * Review sessions of the error notebook. What is due comes from each error's nextReviewAt and
 * the clock, so errors fall due whether or not the app was opened in between.
 */
class ErrorReviewSessionManager(
    private val errors: ErrorRepository,
    private val exams: ExamRepository,
    private val clock: Clock,
) {

    /**
     * Errors due now (overdue included), highest priority first, then the longest overdue; only
     * those of [skill] when given, and at most [limit] (a task of the daily plan).
     */
    suspend fun dueSession(skill: ExamSkill? = null, limit: Int? = null): List<ReviewCard> {
        val now = clock.millis()
        val due = errors.observeErrors().first().filter { e ->
            e.isOpen && e.record.question != null && e.review != null && e.review.nextReviewAt <= now && (skill == null || e.skill == skill)
        }
        val sorted = cards(due, now).sortedWith(compareByDescending<ReviewCard> { it.priority.ordinal }.thenBy { it.error.review!!.nextReviewAt })
        return if (limit != null) sorted.take(limit) else sorted
    }

    /** A session of one error, due or not ("Ôn ngay"); empty if it is gone or has no question. */
    suspend fun singleSession(errorId: Long): List<ReviewCard> {
        val error = errors.observeError(errorId).first()?.takeIf { it.record.question != null } ?: return emptyList()
        return cards(listOf(error), clock.millis())
    }

    suspend fun answer(card: ReviewCard, answer: AnswerInput): ReviewResult? =
        errors.review(card.error.id, card.question.question, card.question.kind, answer)

    private suspend fun cards(list: List<NotebookError>, now: Long): List<ReviewCard> = withContext(Dispatchers.Default) { buildCards(list, now) }

    private suspend fun buildCards(list: List<NotebookError>, now: Long): List<ReviewCard> {
        if (list.isEmpty()) return emptyList()
        val index = ReviewQuestionPicker.PoolIndex(exams.getQuestionPool())
        return list.map { error ->
            val original = error.record.question!!
            val candidates = ReviewQuestionPicker.candidates(original, index.byId[original.id]?.passage, index)
            ReviewCard(
                error,
                ReviewQuestionPicker.pick(candidates, error.review?.reviewCount ?: 0),
                ErrorReviewPriority.of(error, now),
                candidates.size,
            )
        }
    }
}
