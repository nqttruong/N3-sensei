package com.n3sensei.app.domain.dashboard

import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.mistakes.ErrorCategory
import com.n3sensei.app.domain.mistakes.ErrorState
import com.n3sensei.app.domain.mistakes.GradedOccurrence
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.RuleBasedErrorAnalyzer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** [start, end) in epoch millis. */
data class Period(val start: Long, val end: Long) {
    operator fun contains(millis: Long) = millis in start until end
}

/** Questions graded in submitted exams: how many, how many answered, how many right. */
data class Tally(val total: Int, val answered: Int, val correct: Int) {
    /** Right answers over all graded questions (a blank counts as not right). */
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total

    /** Enough questions for a rate to be shown as a figure ([ExamStatistics.MIN_QUESTIONS]). */
    val reliable: Boolean get() = total >= ExamStatistics.MIN_QUESTIONS

    companion object {
        val EMPTY = Tally(0, 0, 0)

        fun of(items: List<GradedOccurrence>) =
            Tally(items.size, items.count { it.outcome != ItemOutcome.UNANSWERED }, items.count { it.outcome == ItemOutcome.CORRECT })
    }
}

data class ExamOverview(
    val examsTaken: Int,
    val questions: Tally,
    val openErrors: Int,
    val masteredErrors: Int,
    /** Open errors due for review now. */
    val dueToday: Int,
)

/** One skill: all time, this and last week, this and last month. */
data class SkillStats(
    val skill: ExamSkill,
    val overall: Tally,
    val thisWeek: Tally,
    val lastWeek: Tally,
    val thisMonth: Tally,
    val lastMonth: Tally,
)

/** Two periods side by side, with the errors fixed and the ones made again in each. */
data class PeriodComparison(
    val current: Tally,
    val previous: Tally,
    val fixedCurrent: Int,
    val fixedPrevious: Int,
    val recurringCurrent: Int,
    val recurringPrevious: Int,
) {
    /** Change in accuracy, only when both periods have enough questions; null otherwise. */
    val accuracyDelta: Double? get() = if (current.reliable && previous.reliable) current.accuracy - previous.accuracy else null
}

data class CategoryCount(val category: ErrorCategory, val count: Int)

data class TypeStats(val type: QuestionType, val tally: Tally)

/** One week of a skill, for the weekly chart. */
data class WeekStats(val weekStart: LocalDate, val tally: Tally)

/**
 * Exam statistics, all counted from the graded questions of submitted exams (what was stored at
 * each submit) and the error notebook. Rates are only shown with [MIN_QUESTIONS] questions or
 * more, and nothing here is a JLPT level or score.
 */
object ExamStatistics {

    const val MIN_QUESTIONS = 10

    fun week(now: Instant, zone: ZoneId, weeksAgo: Long = 0): Period {
        val monday = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo)
        return Period(monday.atStartOfDay(zone).toInstant().toEpochMilli(), monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun month(now: Instant, zone: ZoneId, monthsAgo: Long = 0): Period {
        val first = now.atZone(zone).toLocalDate().withDayOfMonth(1).minusMonths(monthsAgo)
        return Period(first.atStartOfDay(zone).toInstant().toEpochMilli(), first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    private fun List<GradedOccurrence>.within(period: Period) = filter { it.submittedAt in period }

    fun overview(history: List<GradedOccurrence>, errors: List<NotebookError>, now: Long) = ExamOverview(
        examsTaken = history.map { it.attemptId }.distinct().size,
        questions = Tally.of(history),
        openErrors = errors.count { it.isOpen },
        masteredErrors = errors.count { it.state == ErrorState.MASTERED },
        dueToday = errors.count { it.isOpen && it.review != null && it.review.nextReviewAt <= now },
    )

    /** Every skill met in the history, in skill order. */
    fun skills(history: List<GradedOccurrence>, now: Instant, zone: ZoneId): List<SkillStats> {
        val thisWeek = week(now, zone)
        val lastWeek = week(now, zone, 1)
        val thisMonth = month(now, zone)
        val lastMonth = month(now, zone, 1)
        return ExamSkill.entries.mapNotNull { skill ->
            val mine = history.filter { it.skill == skill }
            if (mine.isEmpty()) return@mapNotNull null
            SkillStats(
                skill, Tally.of(mine), Tally.of(mine.within(thisWeek)), Tally.of(mine.within(lastWeek)),
                Tally.of(mine.within(thisMonth)), Tally.of(mine.within(lastMonth)),
            )
        }
    }

    /**
     * [current] against [previous]. "Fixed": errors mastered with their last review in the
     * period. "Recurring": errors missed again (more than once in all) with the last miss in it.
     */
    fun compare(history: List<GradedOccurrence>, errors: List<NotebookError>, current: Period, previous: Period): PeriodComparison {
        fun fixed(p: Period) = errors.count { e -> e.state == ErrorState.MASTERED && e.review?.lastReviewedAt?.let { it in p } == true }
        fun recurring(p: Period) = errors.count { it.isRecurring && it.record.lastWrongAt in p }
        return PeriodComparison(
            Tally.of(history.within(current)), Tally.of(history.within(previous)),
            fixed(current), fixed(previous), recurring(current), recurring(previous),
        )
    }

    /**
     * The category of each question's latest miss (rules of [RuleBasedErrorAnalyzer]), most
     * common first. One pass per question: only its own earlier right answers matter here.
     */
    fun categories(history: List<GradedOccurrence>): List<CategoryCount> {
        val analyzer = RuleBasedErrorAnalyzer()
        return history.groupBy { it.question.id }
            .mapNotNull { (_, occurrences) ->
                val latestMiss = occurrences.filter { it.missed }.maxByOrNull { it.submittedAt } ?: return@mapNotNull null
                val rightBefore = occurrences.count {
                    it.outcome == ItemOutcome.CORRECT && it.attemptId != latestMiss.attemptId && it.submittedAt <= latestMiss.submittedAt
                }
                analyzer.categorize(latestMiss, rightBefore).first
            }
            .groupingBy { it }.eachCount()
            .map { (category, count) -> CategoryCount(category, count) }
            .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.category.ordinal })
    }

    fun byType(history: List<GradedOccurrence>, skill: ExamSkill): List<TypeStats> =
        history.filter { it.skill == skill }.groupBy { it.question.type }
            .map { (type, items) -> TypeStats(type, Tally.of(items)) }
            .sortedByDescending { it.tally.total }

    /** The last [weeks] weeks of a skill, oldest first (empty weeks included). */
    fun weekly(history: List<GradedOccurrence>, skill: ExamSkill, now: Instant, zone: ZoneId, weeks: Int = 8): List<WeekStats> {
        val mine = history.filter { it.skill == skill }
        return (weeks - 1 downTo 0).map { ago ->
            val p = week(now, zone, ago.toLong())
            WeekStats(Instant.ofEpochMilli(p.start).atZone(zone).toLocalDate(), Tally.of(mine.within(p)))
        }
    }
}
