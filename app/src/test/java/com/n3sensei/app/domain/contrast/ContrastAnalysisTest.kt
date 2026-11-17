package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Mastery, delayed reviews and error analysis. */
class ContrastAnalysisTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day0 = Instant.parse("2026-11-01T02:00:00Z")
    private var n = 0

    private fun attempt(
        session: String,
        day: Long,
        outcome: AnswerOutcome,
        question: String = "q${n}",
        type: ContrastQuestionType = ContrastQuestionType.FILL_IN_THE_BLANK,
        expected: String = "A",
        chosen: String? = if (outcome == AnswerOutcome.INCORRECT) "B" else if (outcome == AnswerOutcome.CORRECT) "A" else null,
        group: String = "g",
    ): ContrastAttempt {
        n++
        return ContrastAttempt(
            "r$n", session, n, group, 1, question, 1, type, "p", 1, outcome,
            if (outcome == AnswerOutcome.SKIPPED) null else "o", "o", expected, chosen, if (outcome == AnswerOutcome.SKIPPED) null else "x", "y",
            day0.plus(Duration.ofDays(day)).plusSeconds(n.toLong()),
        )
    }

    private fun review(session: String, day: Long, rating: ReviewRating) =
        ContrastReview(0, "g", session, day0.plus(Duration.ofDays(day)).plusSeconds(500), 3, 3, 0, 0, rating, 0, 1, day0, 1)

    private fun progress(step: Int) = ContrastProgress("g", step, day0, day0, 1, 0, day0, day0)

    @Test
    fun delayed_needsTheGapSinceTheGroupsPreviousSession_fresh_needsANewQuestion() {
        val s1 = listOf(attempt("s1", 0, AnswerOutcome.CORRECT, "q1"), attempt("s1", 0, AnswerOutcome.CORRECT, "q2"))
        val s2 = listOf(attempt("s2", 6, AnswerOutcome.CORRECT, "q3")) // 6 days: not delayed
        val s3 = listOf(attempt("s3", 13, AnswerOutcome.CORRECT, "q1"), attempt("s3", 13, AnswerOutcome.INCORRECT, "q4")) // 7 days after s2
        val c = DelayedAnswers.classify(s1 + s2 + s3, Duration.ofDays(7))
        assertEquals(setOf("s3"), c.delayedSessions)
        assertEquals(s3.map { it.requestId }.toSet(), c.delayed)
        assertEquals(setOf(s3[1].requestId), c.fresh) // q1 was answered before
    }

    @Test
    fun readingOrOneCorrectAnswer_isNeverMastered() {
        val evaluator = ContrastMasteryEvaluator()
        assertEquals(ContrastMastery.NEW, evaluator.evaluate("g", null, emptyList(), emptyList(), zone).mastery)
        val one = evaluator.evaluate("g", progress(5), listOf(attempt("s1", 0, AnswerOutcome.CORRECT)), listOf(review("s1", 0, ReviewRating.GOOD)), zone)
        assertEquals(ContrastMastery.REVIEWING, one.mastery)
    }

    private fun masteredHistory(lastDelayedRating: ReviewRating = ReviewRating.GOOD): Pair<List<ContrastAttempt>, List<ContrastReview>> {
        val attempts = buildList {
            repeat(4) { add(attempt("s1", 0, AnswerOutcome.CORRECT)) }
            repeat(4) { add(attempt("s2", 3, AnswerOutcome.CORRECT, type = ContrastQuestionType.MULTIPLE_CHOICE)) }
            repeat(2) { add(attempt("s3", 10, AnswerOutcome.CORRECT, type = ContrastQuestionType.CONTEXT_SELECTION)) }
            repeat(2) { add(attempt("s3", 10, AnswerOutcome.CORRECT, type = ContrastQuestionType.ERROR_DETECTION)) }
        }
        return attempts to listOf(review("s1", 0, ReviewRating.GOOD), review("s2", 3, ReviewRating.GOOD), review("s3", 10, lastDelayedRating))
    }

    @Test
    fun mastered_whenEveryExperimentalThresholdHolds() {
        val (attempts, reviews) = masteredHistory()
        val m = ContrastMasteryEvaluator().evaluate("g", progress(3), attempts, reviews, zone)
        assertEquals(ContrastMastery.MASTERED, m.mastery)
        assertEquals(12, m.accuracy.answered)
        assertEquals(3, m.sessionDays)
        assertEquals(4, m.delayedAccuracy.answered)
        assertEquals(2, m.delayedQuestionTypes)
        assertEquals(4, m.freshDelayedAccuracy.answered)
    }

    @Test
    fun mastery_isLostAfterAnAgainDelayedBlock_andThresholdsAreConfigurable() {
        val (attempts, reviews) = masteredHistory(ReviewRating.AGAIN)
        assertEquals(ContrastMastery.REVIEWING, ContrastMasteryEvaluator().evaluate("g", progress(2), attempts, reviews, zone).mastery)
        val (ok, okReviews) = masteredHistory()
        val stricter = ContrastMasteryEvaluator(ContrastConfig(mastery = MasteryPolicy(minAnswered = 13)))
        assertEquals(ContrastMastery.LEARNING, stricter.evaluate("g", progress(1), ok, okReviews, zone).mastery)
    }

    @Test
    fun skippedAnswers_doNotCountAsAnsweredNorAsWrong() {
        val attempts = listOf(attempt("s1", 0, AnswerOutcome.CORRECT), attempt("s1", 0, AnswerOutcome.SKIPPED), attempt("s1", 0, AnswerOutcome.INCORRECT))
        val m = ContrastMasteryEvaluator().evaluate("g", progress(0), attempts, emptyList(), zone)
        assertEquals(Accuracy(1, 2), m.accuracy)
        assertEquals(1, m.skipped)
        assertEquals(ContrastMastery.LEARNING, m.mastery)
    }

    @Test
    fun evaluate_refusesDataOfAnotherGroup() {
        assertThrows(IllegalArgumentException::class.java) {
            ContrastMasteryEvaluator().evaluate("g", null, listOf(attempt("s", 0, AnswerOutcome.CORRECT, group = "other")), emptyList(), zone)
        }
    }

    @Test
    fun mistakeReport_directedConfusions_types_repeats_andDelayed() {
        val attempts = listOf(
            attempt("s1", 0, AnswerOutcome.INCORRECT, "q1", expected = "A", chosen = "B"),
            attempt("s1", 0, AnswerOutcome.INCORRECT, "q2", expected = "B", chosen = "A", type = ContrastQuestionType.MULTIPLE_CHOICE),
            attempt("s1", 0, AnswerOutcome.INCORRECT, "q3", expected = "A", chosen = "C"),
            attempt("s1", 0, AnswerOutcome.INCORRECT, "q4", expected = "A", chosen = null), // distractor
            attempt("s1", 0, AnswerOutcome.SKIPPED, "q5"),
            attempt("s2", 8, AnswerOutcome.INCORRECT, "q1", expected = "A", chosen = "B"),
            attempt("s2", 8, AnswerOutcome.CORRECT, "q6"),
        )
        val report = ContrastMistakeAnalyzer().analyze(attempts)
        assertEquals(7, report.total)
        assertEquals(Triple(1, 5, 1), Triple(report.correct, report.incorrect, report.skipped))
        assertEquals(Accuracy(1, 6), report.accuracy)
        val pairs = report.confusions.associate { (it.expectedItemId to it.chosenItemId) to it.count }
        assertEquals(mapOf(("A" to "B") to 2, ("B" to "A") to 1, ("A" to "C") to 1), pairs)
        assertEquals(listOf("A" to "B"), report.repeatedConfusions.map { it.expectedItemId to it.chosenItemId })
        assertEquals(listOf("q1"), report.repeatedQuestions.map { it.questionId })
        assertEquals(1, report.otherIncorrect)
        assertEquals(ContrastQuestionType.FILL_IN_THE_BLANK, report.byQuestionType.first().type)
        assertEquals(attempts[5].answeredAt, report.lastWrongAt)
        // s2 is 8 days after s1: delayed. q1 was seen before (not fresh), q6 is new (fresh).
        assertEquals(Accuracy(1, 2), report.delayedAccuracy)
        assertEquals(Accuracy(1, 1), report.freshDelayedAccuracy)
    }

    @Test
    fun emptyHistory() {
        val report = ContrastMistakeAnalyzer().analyze(emptyList())
        assertEquals(0, report.total)
        assertNull(report.accuracy.value)
        assertNull(report.lastWrongAt)
    }
}
