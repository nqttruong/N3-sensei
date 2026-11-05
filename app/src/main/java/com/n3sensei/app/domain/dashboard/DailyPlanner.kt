package com.n3sensei.app.domain.dashboard

import com.n3sensei.app.domain.exam.ExamSetSummary
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.mistakes.Knowledge
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.ReviewPriority
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

/** One task of the day, with the time it is planned to take. */
sealed interface PlanTask {
    val seconds: Int

    /** Review [count] due errors of [skill] (null: errors with no known skill). */
    data class ReviewErrors(val skill: ExamSkill?, val count: Int, override val seconds: Int) : PlanTask

    /** Study weak study items of one kind as flashcards. */
    data class StudyWeakItems(val type: ItemType, val refs: List<ItemRef>, override val seconds: Int) : PlanTask

    /** Take a section exam of a skill with a low share of right answers. */
    data class TakeSectionExam(val skill: ExamSkill, val examSetId: String, val title: String, val questions: Int, override val seconds: Int) : PlanTask

    /** Review [count] flashcards due in Ôn tập. */
    data class ReviewFlashcards(val count: Int, override val seconds: Int) : PlanTask
}

data class DailyPlan(
    val budgetMinutes: Int,
    val tasks: List<PlanTask>,
    /** Due errors left for another day because the time was used up. */
    val errorsLeft: Int,
    /** A weak skill was found that the app cannot practise yet (listening needs audio). */
    val listeningWeakWithoutAudio: Boolean,
) {
    val seconds: Int get() = tasks.sumOf { it.seconds }
}

/** What the plan is made from: everything comes from the learner's data, nothing is invented. */
data class PlanInput(
    /** Open errors due now, with their priority. */
    val dueErrors: List<Pair<NotebookError, ReviewPriority>>,
    /** Study items still missed (latest question on them wrong), found in the content. */
    val weakItems: List<Knowledge>,
    /** Skills with enough questions to judge, by share missed (highest first). */
    val weakSkills: List<ExamSkill>,
    /** Exam sets in the app. */
    val exams: List<ExamSetSummary>,
    /** Flashcards due in Ôn tập. */
    val dueFlashcards: Int,
)

/**
 * The day's tasks, in order: due errors (high priority first, grouped by skill), weak study
 * items, one section exam of the weakest skill, then due flashcards. The planned time never
 * goes over the learner's budget: a task is shortened to fit (fewer errors, items or cards), or
 * left out when even one does not fit; an exam is only planned whole.
 */
object DailyPlanner {
    const val SECONDS_PER_ERROR = 90
    const val SECONDS_PER_ITEM = 30
    const val SECONDS_PER_FLASHCARD = 20
    const val MAX_WEAK_ITEMS = 10

    /** The budgets the learner can pick, in minutes. */
    val BUDGETS = listOf(10, 15, 20, 30, 45, 60)

    fun plan(input: PlanInput, budgetMinutes: Int): DailyPlan {
        var left = budgetMinutes.coerceAtLeast(0) * 60
        val tasks = mutableListOf<PlanTask>()

        // 1. Due errors, the most urgent first, grouped by skill in that order.
        val errors = input.dueErrors.sortedByDescending { it.second.ordinal }.map { it.first }
        val takeErrors = (left / SECONDS_PER_ERROR).coerceAtMost(errors.size)
        errors.take(takeErrors).groupBy { it.skill }.forEach { (skill, list) ->
            tasks += PlanTask.ReviewErrors(skill, list.size, list.size * SECONDS_PER_ERROR)
        }
        left -= takeErrors * SECONDS_PER_ERROR

        // 2. Weak study items as flashcards, by kind, at most MAX_WEAK_ITEMS in all.
        val weak = input.weakItems.filter { it.item != null }.take(MAX_WEAK_ITEMS)
        val takeItems = (left / SECONDS_PER_ITEM).coerceAtMost(weak.size)
        weak.take(takeItems).groupBy { it.ref.type }.toSortedMap().forEach { (type, list) ->
            tasks += PlanTask.StudyWeakItems(type, list.map { it.ref }, list.size * SECONDS_PER_ITEM)
        }
        left -= takeItems * SECONDS_PER_ITEM

        // 3. A section exam of the weakest skill that has one fitting the time left.
        // Listening exams need audio, which the app cannot play yet.
        val practicable = input.weakSkills.filter { it != ExamSkill.LISTENING }
        val exam = practicable.firstNotNullOfOrNull { skill ->
            input.exams
                .filter { it.type == ExamType.SECTION && it.skills == setOf(skill) && it.durationSeconds in 1..left && it.totalQuestions > 0 }
                .minByOrNull { it.durationSeconds }
                ?.let { PlanTask.TakeSectionExam(skill, it.id, it.title, it.totalQuestions, it.durationSeconds) }
        }
        exam?.let {
            tasks += it
            left -= it.seconds
        }

        // 4. Flashcards due in Ôn tập with what time is left.
        val cards = (left / SECONDS_PER_FLASHCARD).coerceAtMost(input.dueFlashcards)
        if (cards > 0) {
            tasks += PlanTask.ReviewFlashcards(cards, cards * SECONDS_PER_FLASHCARD)
            left -= cards * SECONDS_PER_FLASHCARD
        }

        return DailyPlan(
            budgetMinutes = budgetMinutes,
            tasks = tasks,
            errorsLeft = errors.size - takeErrors,
            listeningWeakWithoutAudio = ExamSkill.LISTENING in input.weakSkills,
        )
    }
}
