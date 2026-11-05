package com.n3sensei.app.domain.dashboard

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.exam.ReviewItem
import com.n3sensei.app.domain.exam.ReviewStatus
import com.n3sensei.app.domain.mistakes.ErrorCategory
import com.n3sensei.app.domain.mistakes.GradedOccurrence
import com.n3sensei.app.domain.mistakes.NotebookError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExamStatisticsTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    /** Wednesday 2026-11-04 10:00 local. */
    private val now = LocalDate.of(2026, 11, 4).atTime(10, 0).atZone(zone).toInstant()
    private val day = 24 * 60 * 60_000L

    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun occ(
        id: String,
        skill: ExamSkill,
        outcome: ItemOutcome,
        whenMillis: Long = now.toEpochMilli(),
        attempt: String = "a",
        type: QuestionType = QuestionType.OTHER,
    ) = GradedOccurrence(
        attempt, "set", whenMillis, false, 0,
        ExamQuestion(id, "s", type, AnswerFormat.CHOICE, "?", null, listOf("a", "b"), 0, emptyList(), null, null, null, null, 0),
        ExamSectionKind.VOCABULARY, skill, outcome, null, null, null,
    )

    private fun many(n: Int, right: Int, skill: ExamSkill, whenMillis: Long, attempt: String = "a", prefix: String = "q") =
        (0 until n).map { occ("$prefix$it", skill, if (it < right) ItemOutcome.CORRECT else ItemOutcome.WRONG, whenMillis, attempt) }

    private fun error(id: Long, count: Int = 1, status: ErrorStatus = ErrorStatus.ACTIVE, due: Long = 0, lastReviewed: Long? = null, lastWrong: Long = 0) =
        NotebookError(
            ErrorRecord(id, "q$id", "s", ErrorType.INCORRECT, count, 0, lastWrong, 0, status, null, null),
            ReviewItem(id, id, due, 0, 0, 0, 0, lastReviewed, if (status == ErrorStatus.MASTERED) ReviewStatus.MASTERED else ReviewStatus.NEW),
            false, null, null, null,
        )

    @Test
    fun noData_givesZerosAndNoRates() {
        val o = ExamStatistics.overview(emptyList(), emptyList(), now.toEpochMilli())
        assertEquals(ExamOverview(0, Tally.EMPTY, 0, 0, 0), o)
        assertFalse(o.questions.reliable)
        assertEquals(0.0, o.questions.accuracy, 0.0)
        assertTrue(ExamStatistics.skills(emptyList(), now, zone).isEmpty())
        assertTrue(ExamStatistics.categories(emptyList()).isEmpty())
        assertNull(ExamStatistics.compare(emptyList(), emptyList(), ExamStatistics.week(now, zone), ExamStatistics.week(now, zone, 1)).accuracyDelta)
    }

    @Test
    fun theOverview_countsExamsAnswersAccuracyAndErrors_exactly() {
        val history = many(8, 6, ExamSkill.KANJI, now.toEpochMilli(), "a1") +
            many(4, 1, ExamSkill.GRAMMAR, now.toEpochMilli(), "a2", "g") +
            occ("blank", ExamSkill.GRAMMAR, ItemOutcome.UNANSWERED, attempt = "a2")
        val errors = listOf(
            error(1, due = now.toEpochMilli() - 1), error(2, due = now.toEpochMilli() + day),
            error(3, status = ErrorStatus.MASTERED), error(4, status = ErrorStatus.ARCHIVED, due = 0),
        )
        val o = ExamStatistics.overview(history, errors, now.toEpochMilli())
        assertEquals(2, o.examsTaken)
        assertEquals(Tally(total = 13, answered = 12, correct = 7), o.questions)
        assertTrue(o.questions.reliable)
        assertEquals(7.0 / 13, o.questions.accuracy, 1e-9) // a blank counts as not right
        assertEquals(2, o.openErrors)
        assertEquals(1, o.masteredErrors)
        assertEquals(1, o.dueToday)
    }

    @Test
    fun weeksStartOnMonday_andMonthsOnTheFirst_inTheLearnersTimeZone() {
        val w = ExamStatistics.week(now, zone)
        assertEquals(LocalDate.of(2026, 11, 2).atStartOfDay(zone).toInstant().toEpochMilli(), w.start)
        assertEquals(LocalDate.of(2026, 11, 9).atStartOfDay(zone).toInstant().toEpochMilli(), w.end)
        val last = ExamStatistics.week(now, zone, 1)
        assertEquals(w.start, last.end)
        val m = ExamStatistics.month(now, zone, 1)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli(), m.start)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(), m.end)
        // Sunday 23:59 local is still last week.
        val sundayNight = LocalDate.of(2026, 11, 1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        assertTrue(sundayNight in last)
        assertFalse(sundayNight in w)
    }

    @Test
    fun skills_comparePeriods_onlyWithEnoughQuestions() {
        val history = many(10, 8, ExamSkill.READING, at(LocalDate.of(2026, 11, 3)), "w0") +
            many(10, 5, ExamSkill.READING, at(LocalDate.of(2026, 10, 28)), "w1", "r") +
            many(3, 3, ExamSkill.LISTENING, at(LocalDate.of(2026, 11, 3)), "w0", "l")
        val skills = ExamStatistics.skills(history, now, zone).associateBy { it.skill }
        val reading = skills.getValue(ExamSkill.READING)
        assertEquals(Tally(20, 20, 13), reading.overall)
        assertEquals(0.8, reading.thisWeek.accuracy, 1e-9)
        assertEquals(0.5, reading.lastWeek.accuracy, 1e-9)
        assertEquals(Tally(10, 10, 8), reading.thisMonth)
        assertEquals(Tally(10, 10, 5), reading.lastMonth)
        val listening = skills.getValue(ExamSkill.LISTENING)
        assertFalse(listening.overall.reliable) // 3 questions: no rate

        val week = ExamStatistics.compare(history.filter { it.skill == ExamSkill.READING }, emptyList(), ExamStatistics.week(now, zone), ExamStatistics.week(now, zone, 1))
        assertEquals(0.3, week.accuracyDelta!!, 1e-9)
        val thin = ExamStatistics.compare(history.filter { it.skill == ExamSkill.LISTENING }, emptyList(), ExamStatistics.week(now, zone), ExamStatistics.week(now, zone, 1))
        assertNull(thin.accuracyDelta)
    }

    @Test
    fun fixedAndRecurringErrors_areCountedInThePeriodTheyHappened() {
        val thisWeek = at(LocalDate.of(2026, 11, 3))
        val lastWeek = at(LocalDate.of(2026, 10, 27))
        val errors = listOf(
            error(1, status = ErrorStatus.MASTERED, lastReviewed = thisWeek),
            error(2, status = ErrorStatus.MASTERED, lastReviewed = lastWeek),
            error(3, status = ErrorStatus.MASTERED, lastReviewed = lastWeek),
            error(4, count = 2, lastWrong = thisWeek),
            error(5, count = 1, lastWrong = thisWeek), // once only: not recurring
            error(6, count = 3, lastWrong = lastWeek),
        )
        val c = ExamStatistics.compare(emptyList(), errors, ExamStatistics.week(now, zone), ExamStatistics.week(now, zone, 1))
        assertEquals(listOf(1, 2, 1, 1), listOf(c.fixedCurrent, c.fixedPrevious, c.recurringCurrent, c.recurringPrevious))
    }

    @Test
    fun categories_countEachMissedQuestionOnce_byItsLatestMiss() {
        val history = listOf(
            occ("k1", ExamSkill.KANJI, ItemOutcome.WRONG, type = QuestionType.KANJI_READING),
            occ("k1", ExamSkill.KANJI, ItemOutcome.WRONG, now.toEpochMilli() + 1, attempt = "b", type = QuestionType.KANJI_READING),
            occ("k2", ExamSkill.KANJI, ItemOutcome.WRONG, type = QuestionType.ORTHOGRAPHY),
            occ("g1", ExamSkill.GRAMMAR, ItemOutcome.WRONG, type = QuestionType.GRAMMAR_FORM),
            occ("blank", ExamSkill.GRAMMAR, ItemOutcome.UNANSWERED, type = QuestionType.GRAMMAR_FORM),
            occ("right", ExamSkill.GRAMMAR, ItemOutcome.CORRECT, type = QuestionType.GRAMMAR_FORM),
        )
        assertEquals(
            listOf(CategoryCount(ErrorCategory.KANJI_CONFUSION, 2), CategoryCount(ErrorCategory.GRAMMAR_CONFUSION, 1), CategoryCount(ErrorCategory.UNKNOWN, 1)),
            ExamStatistics.categories(history),
        )
    }

    @Test
    fun theWeeklySeries_hasEightWeeks_emptyOnesIncluded_andTypesAreSplit() {
        val history = many(4, 2, ExamSkill.KANJI, at(LocalDate.of(2026, 11, 3))) +
            many(6, 6, ExamSkill.KANJI, at(LocalDate.of(2026, 9, 15)), "old", "o")
        val weekly = ExamStatistics.weekly(history, ExamSkill.KANJI, now, zone)
        assertEquals(8, weekly.size)
        assertEquals(LocalDate.of(2026, 11, 2), weekly.last().weekStart)
        assertEquals(LocalDate.of(2026, 9, 14), weekly.first().weekStart)
        assertEquals(Tally(4, 4, 2), weekly.last().tally)
        assertEquals(Tally(6, 6, 6), weekly.first().tally)
        assertEquals(6, weekly.count { it.tally.total == 0 })
        assertEquals(listOf(QuestionType.OTHER), ExamStatistics.byType(history, ExamSkill.KANJI).map { it.type })
    }

    /** Regression (QA): counting categories in one pass must match the full analyzer, miss by miss. */
    @Test
    fun categories_matchTheFullAnalyzer_onRandomHistories() {
        val random = kotlin.random.Random(11)
        repeat(20) {
            val history = (0 until 200).map { n ->
                occ(
                    "q${random.nextInt(40)}", ExamSkill.entries.random(random), ItemOutcome.entries.random(random),
                    whenMillis = random.nextLong(0, 30) * day, attempt = "a${random.nextInt(15)}",
                    type = QuestionType.entries.random(random),
                )
            }.distinctBy { it.attemptId to it.question.id }
            val analyzer = com.n3sensei.app.domain.mistakes.RuleBasedErrorAnalyzer()
            val expected = history.filter { it.missed }.groupBy { it.question.id }
                .map { (_, misses) -> analyzer.analyze(com.n3sensei.app.domain.mistakes.MistakeContext(misses.maxBy { it.submittedAt }, history, emptyList())).category }
                .groupingBy { it }.eachCount()
            assertEquals(expected, ExamStatistics.categories(history).associate { it.category to it.count })
        }
    }
}
