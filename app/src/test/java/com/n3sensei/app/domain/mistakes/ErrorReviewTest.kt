package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.exam.ReviewItem
import com.n3sensei.app.domain.exam.ReviewStatus
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReviewTest {

    private val day = 24 * 60 * 60_000L
    private val tame = ItemRef(ItemType.GRAMMAR, "tame")
    private val noni = ItemRef(ItemType.GRAMMAR, "noni")
    private val kei = ItemRef(ItemType.KANJI, "kei")

    private fun q(id: String, type: QuestionType = QuestionType.GRAMMAR_FORM, related: List<ItemRef> = listOf(tame), options: List<String> = listOf("ため", "のに")) =
        ExamQuestion(id, "s", type, AnswerFormat.CHOICE, "Câu $id", null, options, 0, emptyList(), null, null, null, null, 0, related)

    /** Runs reviews from the exam's first due date: each pair is (days later, right?). */
    private fun run(vararg reviews: Pair<Long, Boolean>, otherQuestionAt: Set<Int> = emptySet()): List<ReviewOutcome> {
        var step = 0
        var due = ErrorReviewSchedule.firstDue(0)
        var passedOther = false
        var now = 0L
        return reviews.mapIndexed { i, (days, right) ->
            now += days * day
            val other = i in otherQuestionAt
            ErrorReviewSchedule.review(step, due, right, now, other, passedOther).also {
                if (it.counted && right && other) passedOther = true
                if (!right) passedOther = false
                step = it.step
                due = it.nextReviewAt
            }
        }
    }

    @Test
    fun theFirstReview_isADayAfterTheExam_thenThreeSevenFourteenThirtyDays() {
        assertEquals(day, ErrorReviewSchedule.firstDue(0))
        val out = run(1L to true, 3L to true, 7L to true, 14L to true)
        assertEquals(listOf(1, 2, 3, 4), out.map { it.step })
        val gaps = out.map { it.intervalMinutes / (24 * 60) }
        assertEquals(listOf(3L, 7L, 14L, 30L), gaps)
        assertTrue(out.all { it.counted && !it.mastered })
    }

    @Test
    fun allFiveLevelsOnTheOldQuestionOnly_doNotMasterIt() {
        val out = run(1L to true, 3L to true, 7L to true, 14L to true, 30L to true)
        val last = out.last()
        assertFalse(last.mastered)
        assertTrue(last.needsAnotherQuestion)
        assertEquals(ErrorReviewSchedule.LEVELS, last.step)
        assertEquals(30L * 24 * 60, last.intervalMinutes)
        // Another question on the same item, answered right when due, then masters it.
        val confirmed = run(1L to true, 3L to true, 7L to true, 14L to true, 30L to true, 30L to true, otherQuestionAt = setOf(5))
        assertTrue(confirmed.last().mastered)
    }

    @Test
    fun aRightAnswerToAnotherQuestionEarlier_countsForMastery_untilAWrongAnswer() {
        val out = run(1L to true, 3L to true, 7L to true, 14L to true, 30L to true, otherQuestionAt = setOf(2))
        assertTrue(out.last().mastered)
        val reset = run(1L to true, 3L to true, 1L to false, 1L to true, 7L to true, 14L to true, 30L to true, 30L to true, otherQuestionAt = setOf(1))
        // The other question was before the wrong answer: not enough any more.
        assertFalse(reset.last().mastered)
    }

    @Test
    fun aWrongAnswer_dropsTwoLevels_bringsTheNextReviewToADay_andRepeatsKeepItLow() {
        val out = run(1L to true, 3L to true, 7L to true, 1L to false, 1L to false, 1L to false)
        assertEquals(listOf(1, 2, 3, 1, 0, 0), out.map { it.step })
        out.drop(3).forEach {
            assertFalse(it.mastered)
            assertTrue(it.counted)
            assertEquals(24L * 60, it.intervalMinutes)
        }
    }

    @Test
    fun aRightAnswerBeforeItIsDue_isPracticeOnly_butAnEarlyWrongOneCounts() {
        val early = ErrorReviewSchedule.review(step = 2, dueAt = 10 * day, correct = true, now = 9 * day, otherQuestion = true, passedAnotherQuestionBefore = false)
        assertFalse(early.counted)
        assertEquals(2, early.step)
        assertEquals(10 * day, early.nextReviewAt)
        val earlyWrong = ErrorReviewSchedule.review(2, 10 * day, false, 9 * day, false, false)
        assertTrue(earlyWrong.counted)
        assertEquals(0, earlyWrong.step)
        assertEquals(10 * day, earlyWrong.nextReviewAt)
    }

    @Test
    fun anOverdueReview_isGradedLikeADueOne_scheduledFromToday() {
        val late = ErrorReviewSchedule.review(1, dueAt = 3 * day, correct = true, now = 20 * day, otherQuestion = false, passedAnotherQuestionBefore = false)
        assertTrue(late.counted)
        assertEquals(2, late.step)
        assertEquals(27 * day, late.nextReviewAt)
    }

    @Test
    fun otherQuestions_countOnlyThroughASharedStudyItem() {
        val original = q("orig")
        assertEquals(ReviewQuestionKind.ORIGINAL, ReviewQuestionPicker.classify(original, original))
        assertEquals(ReviewQuestionKind.SIMILAR, ReviewQuestionPicker.classify(original, q("same-task")))
        assertEquals(ReviewQuestionKind.APPLICATION, ReviewQuestionPicker.classify(original, q("other-task", QuestionType.TEXT_GRAMMAR)))
        assertEquals(ReviewQuestionKind.CONTRAST, ReviewQuestionPicker.classify(original, q("two-points", related = listOf(tame, noni))))
        // Same task and same options, but no shared item: not related.
        assertNull(ReviewQuestionPicker.classify(original, q("unrelated", related = listOf(noni))))
        assertNull(ReviewQuestionPicker.classify(original, q("bare", related = emptyList())))
        // A question with no items has no other questions at all.
        assertNull(ReviewQuestionPicker.classify(q("no-items", related = emptyList()), q("bare2", related = emptyList())))
        // Two items of different kinds is not a contrast.
        assertEquals(ReviewQuestionKind.SIMILAR, ReviewQuestionPicker.classify(original, q("mixed", related = listOf(tame, kei))))
    }

    @Test
    fun candidates_putTheOriginalFirst_skipUngradableOnes_andTurnReviewAfterReview() {
        val original = q("orig")
        val pool = listOf(
            q("orig"), q("similar"), q("application", QuestionType.TEXT_GRAMMAR), q("contrast", related = listOf(tame, noni)),
            q("broken", options = listOf("only one")), q("unrelated", related = listOf(kei)),
        ).map { PooledQuestion(it, null) }
        val candidates = ReviewQuestionPicker.candidates(original, null, pool)
        assertEquals(listOf("orig", "contrast", "application", "similar"), candidates.map { it.question.id })
        assertEquals(
            listOf("orig", "contrast", "application", "similar", "orig"),
            (0..4).map { ReviewQuestionPicker.pick(candidates, it).question.id },
        )
        // Without others, always the original.
        val alone = ReviewQuestionPicker.candidates(q("x", related = emptyList()), null, pool)
        assertEquals(listOf("x"), alone.map { it.question.id })
        assertEquals("x", ReviewQuestionPicker.pick(alone, 7).question.id)
    }

    private fun error(count: Int = 1, reviews: Int = 0, successes: Int = 0, due: Long = 0, important: Boolean = false) = NotebookError(
        ErrorRecord(1, "q", "s", ErrorType.INCORRECT, count, 0, 0, 0, ErrorStatus.ACTIVE, null, null),
        ReviewItem(1, 1, due, 0, 0, reviews, successes, null, ReviewStatus.LEARNING),
        important, null, null, null,
    )

    @Test
    fun priority_risesWithWrongReviewsRepeatsTheMarkAndLongOverdue() {
        assertEquals(ReviewPriority.NORMAL, ErrorReviewPriority.of(error(), now = 0))
        assertEquals(ReviewPriority.RAISED, ErrorReviewPriority.of(error(reviews = 1), now = 0))
        assertEquals(ReviewPriority.HIGH, ErrorReviewPriority.of(error(reviews = 3, successes = 1), now = 0))
        assertEquals(ReviewPriority.RAISED, ErrorReviewPriority.of(error(count = 2), now = 0))
        assertEquals(ReviewPriority.HIGH, ErrorReviewPriority.of(error(count = 3), now = 0))
        assertEquals(ReviewPriority.HIGH, ErrorReviewPriority.of(error(important = true), now = 0))
        assertEquals(ReviewPriority.NORMAL, ErrorReviewPriority.of(error(due = 0), now = 2 * day))
        assertEquals(ReviewPriority.RAISED, ErrorReviewPriority.of(error(due = 0), now = 3 * day))
    }

    /** Regression (QA): the indexed pool must give exactly what checking every question gave. */
    @Test
    fun theIndexedPool_givesTheSameCandidatesAsCheckingEveryQuestion() {
        val random = kotlin.random.Random(3)
        val items = (0 until 12).map { i -> ItemRef(ItemType.entries[i % 3], "i$i") }
        val pool = (0 until 300).map { n ->
            PooledQuestion(
                q("p$n", QuestionType.entries[random.nextInt(QuestionType.entries.size)], items.shuffled(random).take(random.nextInt(0, 3)),
                    options = if (n % 17 == 0) listOf("one") else listOf("a", "b")),
                null,
            )
        }
        val index = ReviewQuestionPicker.PoolIndex(pool)
        pool.take(60).forEach { p ->
            val bruteForce = listOf(ReviewQuestion(p.question, ReviewQuestionKind.ORIGINAL)) + pool
                .filter { it.question.id != p.question.id && com.n3sensei.app.domain.exam.ExamGrading.problems(it.question).isEmpty() }
                .mapNotNull { o -> ReviewQuestionPicker.classify(p.question, o.question)?.let { ReviewQuestion(o.question, it) } }
                .sortedWith(compareBy({ listOf(ReviewQuestionKind.ORIGINAL, ReviewQuestionKind.CONTRAST, ReviewQuestionKind.APPLICATION, ReviewQuestionKind.SIMILAR).indexOf(it.kind) }, { it.question.id }))
            assertEquals(bruteForce, ReviewQuestionPicker.candidates(p.question, null, index))
        }
    }
}
