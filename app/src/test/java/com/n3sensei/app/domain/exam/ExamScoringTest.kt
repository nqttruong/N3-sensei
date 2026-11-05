package com.n3sensei.app.domain.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamScoringTest {

    private fun question(id: String, type: QuestionType = QuestionType.OTHER) = ExamQuestion(
        id, "s", type, AnswerFormat.CHOICE, "?", null, listOf("a", "b"), 0, emptyList(), null, null, null, null, 0,
    )

    private fun item(position: Int, skill: ExamSkill, outcome: ItemOutcome, type: QuestionType = QuestionType.OTHER, timeMs: Long = 10_000) =
        ResultItem(
            position, question("q$position", type), ExamSectionKind.VOCABULARY, skill, outcome,
            if (outcome == ItemOutcome.UNANSWERED) null else ExamAnswer("a", "q$position", 0, null, outcome == ItemOutcome.CORRECT, timeMs, null, 0, 0),
        )

    private fun attempt(id: String = "a", status: AttemptStatus = AttemptStatus.SUBMITTED, duration: Long? = 900) =
        ExamAttempt(id, "set", "Đề", 0, 1, 1_800, duration, null, 0, status)

    private fun record(vararg items: ResultItem, attempt: ExamAttempt = attempt()) = AttemptRecord(attempt, items.toList())

    @Test
    fun theScore_countsRightWrongAndBlank_overallAndPerSkill() {
        val score = ExamScoring.score(
            record(
                item(0, ExamSkill.KANJI, ItemOutcome.CORRECT),
                item(1, ExamSkill.KANJI, ItemOutcome.WRONG),
                item(2, ExamSkill.GRAMMAR, ItemOutcome.UNANSWERED),
                item(3, ExamSkill.READING, ItemOutcome.CORRECT, timeMs = 30_000),
                item(4, ExamSkill.READING, ItemOutcome.CORRECT),
            ),
        )
        assertEquals(5, score.total)
        assertEquals(3, score.correct)
        assertEquals(1, score.wrong)
        assertEquals(1, score.unanswered)
        assertEquals(0.6, score.accuracy, 1e-9)
        assertEquals(900L, score.durationSeconds)
        // Only the skills in the attempt, in skill order.
        assertEquals(listOf(ExamSkill.KANJI, ExamSkill.GRAMMAR, ExamSkill.READING), score.skills.map { it.skill })
        val kanji = score.skills[0]
        assertEquals(listOf(2, 1, 1, 0), listOf(kanji.total, kanji.correct, kanji.wrong, kanji.unanswered))
        assertEquals(0.5, kanji.accuracy, 1e-9)
        assertEquals(0.0, score.skills[1].accuracy, 1e-9)
        assertEquals(40_000L, score.skills[2].timeSpentMs)
    }

    @Test
    fun anEmptyAttempt_scoresZero_withoutDividingByZero() {
        val score = ExamScoring.score(record())
        assertEquals(0.0, score.accuracy, 0.0)
        assertTrue(score.skills.isEmpty())
        assertTrue(ExamScoring.improvements(record()).isEmpty())
    }

    @Test
    fun improvements_listWeakSkillsWeakestFirst_thenTasksMissedTwice_thenBlanks() {
        val r = record(
            item(0, ExamSkill.KANJI, ItemOutcome.WRONG, QuestionType.KANJI_READING),
            item(1, ExamSkill.KANJI, ItemOutcome.UNANSWERED, QuestionType.KANJI_READING),
            item(2, ExamSkill.KANJI, ItemOutcome.CORRECT, QuestionType.KANJI_READING),
            item(3, ExamSkill.GRAMMAR, ItemOutcome.WRONG, QuestionType.GRAMMAR_FORM),
            item(4, ExamSkill.GRAMMAR, ItemOutcome.CORRECT, QuestionType.GRAMMAR_FORM),
            item(5, ExamSkill.READING, ItemOutcome.CORRECT, QuestionType.READING_SHORT),
            item(6, ExamSkill.READING, ItemOutcome.CORRECT, QuestionType.READING_SHORT),
            attempt = attempt(status = AttemptStatus.EXPIRED),
        )
        assertEquals(
            listOf(
                Improvement.WeakSkill(ExamSkill.KANJI, 1, 3), // 33 %
                Improvement.WeakSkill(ExamSkill.GRAMMAR, 1, 2), // 50 %; reading (100 %) is not weak
                Improvement.FrequentMistake(QuestionType.KANJI_READING, 2, 3),
                Improvement.Unanswered(1, timeRanOut = true),
            ),
            ExamScoring.improvements(r),
        )
    }

    @Test
    fun exactlySixtyPercent_isNotWeak_andBlanksWithoutTheTimeUpAreSaidSo() {
        val r = record(
            *(0 until 3).map { item(it, ExamSkill.LISTENING, ItemOutcome.CORRECT) }.toTypedArray(),
            item(3, ExamSkill.LISTENING, ItemOutcome.WRONG),
            item(4, ExamSkill.LISTENING, ItemOutcome.UNANSWERED),
        )
        assertEquals(listOf(Improvement.Unanswered(1, timeRanOut = false)), ExamScoring.improvements(r))
    }

    @Test
    fun comparison_givesScoreAccuracySkillAndTimeChanges() {
        val first = ExamScoring.report(
            record(
                item(0, ExamSkill.KANJI, ItemOutcome.WRONG), item(1, ExamSkill.KANJI, ItemOutcome.CORRECT),
                item(2, ExamSkill.GRAMMAR, ItemOutcome.CORRECT), item(3, ExamSkill.GRAMMAR, ItemOutcome.CORRECT),
                attempt = attempt("first", duration = 1_200),
            ),
        )
        val second = ExamScoring.report(
            record(
                item(0, ExamSkill.KANJI, ItemOutcome.CORRECT), item(1, ExamSkill.KANJI, ItemOutcome.CORRECT),
                item(2, ExamSkill.GRAMMAR, ItemOutcome.UNANSWERED), item(3, ExamSkill.GRAMMAR, ItemOutcome.CORRECT),
                item(4, ExamSkill.LISTENING, ItemOutcome.CORRECT),
                attempt = attempt("second", duration = 1_000),
            ),
        )
        val c = ExamScoring.compare(first, second)
        assertEquals("first", c.previous.id)
        assertEquals(1, c.correctDelta) // 3 → 4
        assertEquals(0.8 - 0.75, c.accuracyDelta, 1e-9)
        assertEquals(-200L, c.durationDeltaSeconds)
        assertEquals(listOf(ExamSkill.KANJI), c.improved.map { it.skill })
        assertEquals(listOf(ExamSkill.GRAMMAR), c.declined.map { it.skill })
        // Listening was not in the first attempt: shown, but neither better nor worse.
        assertNull(c.skills.single { it.skill == ExamSkill.LISTENING }.accuracyDelta)
    }
}
