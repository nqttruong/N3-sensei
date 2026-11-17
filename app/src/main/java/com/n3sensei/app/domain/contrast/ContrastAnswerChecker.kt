package com.n3sensei.app.domain.contrast

/** What the learner did on a question. Leaving the session is not an input: it leaves the position PENDING. */
sealed interface AnswerInput {
    data class Chosen(val optionId: String) : AnswerInput

    /** Pressed "Bỏ qua": SKIPPED, never INCORRECT. */
    data object Skipped : AnswerInput
}

/** A graded answer, before it becomes a [ContrastAttempt]. */
data class GradedAnswer(
    val outcome: AnswerOutcome,
    val chosenOptionId: String?,
    val expectedItemId: String,
    val chosenItemId: String?,
    val chosenText: String?,
    val correctText: String,
)

/**
 * Grades an answer against the edited pack answer only (no AI, no free-text matching: answers are
 * option ids). The four types share the rule "correct ⇔ chosen option = correctOptionId"; they differ
 * in what the options are and in which item a wrong answer points to:
 *
 * | type | options | chosenItemId when wrong |
 * |------|---------|-------------------------|
 * | MULTIPLE_CHOICE, FILL_IN_THE_BLANK, CONTEXT_SELECTION | answers | item of the chosen option (null for a distractor) |
 * | ERROR_DETECTION | segments of the sentence; the correct one is the wrong segment | item of the wrong segment the learner did not spot |
 *
 * For ERROR_DETECTION, [GradedAnswer.correctText] is "segment → correction" so history shows the fix.
 */
object ContrastAnswerChecker {

    fun grade(question: ContrastQuestion, input: AnswerInput): GradedAnswer {
        val correct = question.correctOption
        val correctText = when (question.type) {
            ContrastQuestionType.ERROR_DETECTION -> "${correct.text} → ${requireNotNull(question.correction) { "ERROR_DETECTION needs a correction" }.text}"
            else -> correct.text
        }
        return when (input) {
            AnswerInput.Skipped -> GradedAnswer(AnswerOutcome.SKIPPED, null, question.targetItemId, null, null, correctText)
            is AnswerInput.Chosen -> {
                val chosen = requireNotNull(question.option(input.optionId)) { "Option ${input.optionId} is not an option of ${question.id}" }
                val isCorrect = chosen.id == question.correctOptionId
                val chosenItem = when {
                    isCorrect -> chosen.itemId
                    question.type == ContrastQuestionType.ERROR_DETECTION -> correct.itemId
                    else -> chosen.itemId
                }
                GradedAnswer(
                    outcome = if (isCorrect) AnswerOutcome.CORRECT else AnswerOutcome.INCORRECT,
                    chosenOptionId = chosen.id,
                    expectedItemId = question.targetItemId,
                    chosenItemId = chosenItem,
                    chosenText = chosen.text,
                    correctText = correctText,
                )
            }
        }
    }
}
