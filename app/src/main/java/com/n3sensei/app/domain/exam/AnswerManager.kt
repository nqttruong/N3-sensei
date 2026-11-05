package com.n3sensei.app.domain.exam

import com.n3sensei.app.domain.repository.ExamRepository
import java.time.Clock

/**
 * Answers and flags of an attempt in progress. Every change is saved at once (nothing is kept
 * only in memory), so leaving the app loses nothing. Refused once the attempt is submitted,
 * abandoned or out of time.
 */
class AnswerManager(private val exams: ExamRepository, private val clock: Clock) {

    /**
     * Saves or changes the answer to [question]; the time since [shownAtMillis] (when the
     * question came on screen) is added to the time spent on it. Null when refused: the attempt
     * takes no more answers, or the input does not fit the question (an option it does not have,
     * an empty text, a text for a choice question or the reverse).
     */
    suspend fun answer(
        attemptId: String,
        question: ExamQuestion,
        input: AnswerInput,
        shownAtMillis: Long,
        confidence: Confidence? = null,
    ): ExamAnswer? {
        if (!fits(question, input)) return null
        val spent = (clock.millis() - shownAtMillis).coerceAtLeast(0)
        return exams.answer(attemptId, question.id, input, spent, confidence)
    }

    suspend fun setFlag(attemptId: String, questionId: String, flagged: Boolean): Boolean =
        exams.setFlag(attemptId, questionId, flagged)

    private fun fits(question: ExamQuestion, input: AnswerInput): Boolean = when (input) {
        is AnswerInput.Choice -> question.format == AnswerFormat.CHOICE && input.index in question.options.indices
        is AnswerInput.Text -> question.format == AnswerFormat.TEXT && input.value.isNotBlank()
    }
}
