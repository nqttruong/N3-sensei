package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ExamGrading
import com.n3sensei.app.domain.exam.ExamPassage
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.model.ItemRef

/** Which question a review asks, relative to the one that was missed. Stored by name. */
enum class ReviewQuestionKind {
    /** The question that was missed. */
    ORIGINAL,

    /** Another question on the same study item, of the same task. */
    SIMILAR,

    /** Another question on the same study item, of another task (the item used differently). */
    APPLICATION,

    /** Another question on the same study item that also tests another item of its kind: telling them apart. */
    CONTRAST,
}

/** A question to review an error with, and its reading text or audio if it has one. */
data class ReviewQuestion(val question: ExamQuestion, val kind: ReviewQuestionKind, val passage: ExamPassage? = null)

/** An exam question with its passage, as stored in the exam sets. */
data class PooledQuestion(val question: ExamQuestion, val passage: ExamPassage?)

/**
 * Picks review questions only from the exam sets in the app: never written for the occasion.
 * Another question counts only through a study item it shares with the missed one (the
 * vocabulary, kanji or grammar links of the sets); a shared task or answer alone does not.
 */
object ReviewQuestionPicker {

    fun classify(original: ExamQuestion, candidate: ExamQuestion): ReviewQuestionKind? {
        if (candidate.id == original.id) return ReviewQuestionKind.ORIGINAL
        val shared = candidate.relatedItems.toSet() intersect original.relatedItems.toSet()
        if (shared.isEmpty()) return null
        val sameKindItems = shared.any { s -> candidate.relatedItems.count { it.type == s.type } >= 2 }
        return when {
            sameKindItems -> ReviewQuestionKind.CONTRAST
            candidate.type != original.type -> ReviewQuestionKind.APPLICATION
            else -> ReviewQuestionKind.SIMILAR
        }
    }

    /**
     * The pool's gradable questions by the study items they test. Built once per session, so
     * each error only looks at questions that share an item with it, not at the whole pool.
     */
    class PoolIndex(pool: List<PooledQuestion>) {
        val byId: Map<String, PooledQuestion> = pool.associateBy { it.question.id }
        val byItem: Map<ItemRef, List<PooledQuestion>> = pool
            .filter { ExamGrading.problems(it.question).isEmpty() }
            .flatMap { p -> p.question.relatedItems.distinct().map { it to p } }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * The original first, then the other questions (contrast, application, similar), each
     * gradable. The original comes from the notebook, so it is there even if its set is gone.
     */
    fun candidates(original: ExamQuestion, originalPassage: ExamPassage?, pool: List<PooledQuestion>): List<ReviewQuestion> =
        candidates(original, originalPassage, PoolIndex(pool))

    fun candidates(original: ExamQuestion, originalPassage: ExamPassage?, index: PoolIndex): List<ReviewQuestion> {
        val others = original.relatedItems.distinct()
            .flatMap { index.byItem[it].orEmpty() }
            .distinctBy { it.question.id }
            .filter { it.question.id != original.id }
            .mapNotNull { p -> classify(original, p.question)?.let { ReviewQuestion(p.question, it, p.passage) } }
            .sortedWith(compareBy({ it.kind.order() }, { it.question.id }))
        return listOf(ReviewQuestion(original, ReviewQuestionKind.ORIGINAL, originalPassage)) + others
    }

    /** Turns through the candidates review after review, so a session mixes the old question with new ones. */
    fun pick(candidates: List<ReviewQuestion>, reviewCount: Int): ReviewQuestion = candidates[reviewCount.mod(candidates.size)]

    private fun ReviewQuestionKind.order() = when (this) {
        ReviewQuestionKind.ORIGINAL -> 0
        ReviewQuestionKind.CONTRAST -> 1
        ReviewQuestionKind.APPLICATION -> 2
        ReviewQuestionKind.SIMILAR -> 3
    }
}

/** What a review changes. */
data class ReviewOutcome(
    val step: Int,
    val nextReviewAt: Long,
    val intervalMinutes: Long,
    val mastered: Boolean,
    /** False for a right answer before it was due: practice only. */
    val counted: Boolean,
    /** Every step is passed but only on the original question: another question is needed to master it. */
    val needsAnotherQuestion: Boolean,
)

/**
 * The spaced reviews of an error, in real time (dates, not app sessions): first a day after the
 * exam, then 3, 7, 14 and 30 days after each right answer given when due. A wrong answer drops
 * two levels and brings the next review back to a day later. Mastery takes all five levels and
 * at least one right answer to another question on the same item, so remembering the old
 * answer is not enough.
 */
object ErrorReviewSchedule {
    const val DAY_MINUTES = 24 * 60L
    val LADDER_DAYS = listOf(1L, 3L, 7L, 14L, 30L)
    val LEVELS = LADDER_DAYS.size
    const val WRONG_DROP = 2

    /** When the first review of a mistake made at [madeAt] is due. */
    fun firstDue(madeAt: Long): Long = madeAt + LADDER_DAYS.first() * DAY_MINUTES * 60_000

    fun review(
        step: Int,
        dueAt: Long,
        correct: Boolean,
        now: Long,
        otherQuestion: Boolean,
        passedAnotherQuestionBefore: Boolean,
    ): ReviewOutcome {
        if (!correct) {
            val minutes = LADDER_DAYS.first() * DAY_MINUTES
            return ReviewOutcome((step - WRONG_DROP).coerceAtLeast(0), now + minutes * 60_000, minutes, false, true, false)
        }
        if (now < dueAt) {
            return ReviewOutcome(step, dueAt, LADDER_DAYS[step.coerceIn(0, LEVELS - 1)] * DAY_MINUTES, false, false, false)
        }
        val next = (step + 1).coerceAtMost(LEVELS)
        val confirmed = otherQuestion || passedAnotherQuestionBefore
        if (next >= LEVELS && confirmed) return ReviewOutcome(LEVELS, now, 0, true, true, false)
        val minutes = LADDER_DAYS[next.coerceAtMost(LEVELS - 1)] * DAY_MINUTES
        return ReviewOutcome(next, now + minutes * 60_000, minutes, false, true, needsAnotherQuestion = next >= LEVELS)
    }

    /** The right answer of [question] as text. */
    fun correctAnswer(question: ExamQuestion): String = when (question.format) {
        AnswerFormat.CHOICE -> question.answerIndex?.let { question.options.getOrNull(it) }.orEmpty()
        AnswerFormat.TEXT -> question.acceptedAnswers.joinToString(" / ")
    }

    fun correctAnswer(error: ErrorRecord): String? = error.question?.let(::correctAnswer)
}

/**
 * Review priority of an open error, from facts the learner can see: wrong reviews, how often it
 * was missed in exams, the "cần chú ý" mark and how long it has been overdue.
 */
object ErrorReviewPriority {
    fun of(error: NotebookError, now: Long): ReviewPriority {
        val review = error.review
        val lapses = review?.let { (it.reviewCount - it.successCount).coerceAtLeast(0) } ?: 0
        val overdueDays = review?.let { (now - it.nextReviewAt) / (ErrorReviewSchedule.DAY_MINUTES * 60_000) } ?: 0
        return when {
            lapses >= 2 || error.record.errorCount >= 3 || error.isImportant -> ReviewPriority.HIGH
            lapses == 1 || error.record.errorCount == 2 || overdueDays >= 3 -> ReviewPriority.RAISED
            else -> ReviewPriority.NORMAL
        }
    }
}
