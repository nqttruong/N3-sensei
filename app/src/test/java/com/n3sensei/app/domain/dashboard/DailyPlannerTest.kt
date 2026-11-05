package com.n3sensei.app.domain.dashboard

import com.n3sensei.app.domain.course.ItemLocation
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamSetSummary
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.mistakes.Knowledge
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.ReviewPriority
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.practice.PracticeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DailyPlannerTest {

    private fun error(id: Long, skill: ExamSkill?, priority: ReviewPriority = ReviewPriority.NORMAL) = NotebookError(
        ErrorRecord(id, "q$id", "s", ErrorType.INCORRECT, 1, 0, 0, 0, ErrorStatus.ACTIVE, null, null), null, false, null, "N3", skill,
    ) to priority

    private fun word(id: String) = Knowledge(
        ItemRef(ItemType.VOCABULARY, id), PracticeItem.VocabularyItem(Vocabulary(id, "l", id, id, null, "", null, "N3", null)), ItemLocation.NONE,
    )

    private fun grammar(id: String) = Knowledge(
        ItemRef(ItemType.GRAMMAR, id), PracticeItem.GrammarItem(GrammarPoint(id, "l", id, "", "", "", null)), ItemLocation.NONE,
    )

    private fun exam(id: String, skill: ExamSkill, minutes: Int, type: ExamType = ExamType.SECTION) =
        ExamSetSummary(id, "Đề $id", "N3", type, minutes * 60, 10, 0, null, setOf(skill))

    private val empty = PlanInput(emptyList(), emptyList(), emptyList(), emptyList(), 0)

    @Test
    fun nothingToDo_isAnEmptyPlan() {
        val plan = DailyPlanner.plan(empty, 20)
        assertTrue(plan.tasks.isEmpty())
        assertEquals(0, plan.seconds)
        assertEquals(0, plan.errorsLeft)
    }

    @Test
    fun dueErrorsComeFirst_byPriority_groupedBySkill_andAreCutToTheTime() {
        val errors = listOf(
            error(1, ExamSkill.GRAMMAR), error(2, ExamSkill.KANJI, ReviewPriority.HIGH), error(3, ExamSkill.GRAMMAR, ReviewPriority.RAISED),
            error(4, ExamSkill.GRAMMAR), error(5, ExamSkill.KANJI),
        )
        // 5 minutes = 3 errors of 90 s; the 3 most urgent: 2 (kanji), 3 (grammar), then 1 or 4 (grammar).
        val plan = DailyPlanner.plan(empty.copy(dueErrors = errors), 5)
        assertEquals(
            listOf(PlanTask.ReviewErrors(ExamSkill.KANJI, 1, 90), PlanTask.ReviewErrors(ExamSkill.GRAMMAR, 2, 180)),
            plan.tasks,
        )
        assertEquals(2, plan.errorsLeft)
        assertTrue(plan.seconds <= 5 * 60)
    }

    @Test
    fun weakItems_flashcardsAndAnExam_fillTheRest_inThatOrder() {
        val input = PlanInput(
            dueErrors = listOf(error(1, ExamSkill.GRAMMAR)),
            weakItems = listOf(word("w1"), grammar("g1"), word("w2")),
            weakSkills = listOf(ExamSkill.KANJI),
            exams = listOf(exam("long", ExamSkill.KANJI, 25), exam("short", ExamSkill.KANJI, 8), exam("full", ExamSkill.KANJI, 5, ExamType.FULL)),
            dueFlashcards = 100,
        )
        val plan = DailyPlanner.plan(input, 15)
        assertEquals(PlanTask.ReviewErrors(ExamSkill.GRAMMAR, 1, 90), plan.tasks[0])
        assertEquals(PlanTask.StudyWeakItems(ItemType.VOCABULARY, listOf(ItemRef(ItemType.VOCABULARY, "w1"), ItemRef(ItemType.VOCABULARY, "w2")), 60), plan.tasks[1])
        assertEquals(PlanTask.StudyWeakItems(ItemType.GRAMMAR, listOf(ItemRef(ItemType.GRAMMAR, "g1")), 30), plan.tasks[2])
        // The 8-minute section exam fits (the 25-minute one and the full exam do not count).
        assertEquals("short", (plan.tasks[3] as PlanTask.TakeSectionExam).examSetId)
        // 15 min − 90 s − 90 s − 8 min = 4 min = 12 cards of 20 s.
        assertEquals(PlanTask.ReviewFlashcards(12, 240), plan.tasks[4])
        assertEquals(15 * 60, plan.seconds)
    }

    @Test
    fun aListeningWeakness_getsNoListeningExam_andIsSaidSo() {
        val input = empty.copy(weakSkills = listOf(ExamSkill.LISTENING), exams = listOf(exam("l", ExamSkill.LISTENING, 5)))
        val plan = DailyPlanner.plan(input, 30)
        assertTrue(plan.tasks.none { it is PlanTask.TakeSectionExam })
        assertTrue(plan.listeningWeakWithoutAudio)
        assertFalse(DailyPlanner.plan(empty, 30).listeningWeakWithoutAudio)
    }

    @Test
    fun aTinyBudget_leavesOutWhatDoesNotFit_andZeroPlansNothing() {
        val input = empty.copy(dueErrors = listOf(error(1, ExamSkill.KANJI)), dueFlashcards = 5)
        assertTrue(DailyPlanner.plan(input, 0).tasks.isEmpty())
        val one = DailyPlanner.plan(input, 1) // 60 s: no error (90 s), three cards
        assertEquals(listOf(PlanTask.ReviewFlashcards(3, 60)), one.tasks)
        assertEquals(1, one.errorsLeft)
    }

    @Test
    fun weakItems_areAtMostTen_andItemsGoneFromTheAppAreSkipped() {
        val gone = Knowledge(ItemRef(ItemType.VOCABULARY, "gone"), null, ItemLocation.NONE)
        val input = empty.copy(weakItems = listOf(gone) + (1..20).map { word("w$it") })
        val plan = DailyPlanner.plan(input, 60)
        val items = plan.tasks.filterIsInstance<PlanTask.StudyWeakItems>().flatMap { it.refs }
        assertEquals(DailyPlanner.MAX_WEAK_ITEMS, items.size)
        assertFalse(ItemRef(ItemType.VOCABULARY, "gone") in items)
    }

    @Test
    fun thePlannedTime_neverGoesOverTheBudget_whateverTheData() {
        val random = Random(42)
        repeat(500) {
            val input = PlanInput(
                dueErrors = (1..random.nextInt(0, 40)).map { error(it.toLong(), ExamSkill.entries.random(random), ReviewPriority.entries.random(random)) },
                weakItems = (1..random.nextInt(0, 15)).map { word("w$it") },
                weakSkills = ExamSkill.entries.shuffled(random).take(random.nextInt(0, 3)),
                exams = (1..random.nextInt(0, 4)).map { exam("e$it", ExamSkill.entries.random(random), random.nextInt(1, 40)) },
                dueFlashcards = random.nextInt(0, 300),
            )
            DailyPlanner.BUDGETS.forEach { budget ->
                val plan = DailyPlanner.plan(input, budget)
                assertTrue("${plan.seconds} > $budget min", plan.seconds <= budget * 60)
                assertTrue(plan.tasks.all { it.seconds > 0 })
            }
        }
    }
}
