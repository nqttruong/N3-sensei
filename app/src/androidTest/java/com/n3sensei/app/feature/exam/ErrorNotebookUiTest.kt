package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.mistakes.ErrorState
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.SeedData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The error notebook in the real app, with the SEED TEST DATA content and a TEST DATA ONLY exam
 * linked to its items, taken twice through the repository (the kanji question missed both times).
 */
@RunWith(AndroidJUnit4::class)
class ErrorNotebookUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun q(id: String, section: String, type: QuestionType, related: List<ItemRef>) =
        ExamQuestion(id, section, type, AnswerFormat.CHOICE, "Câu hỏi $id", null, listOf("けいけん", "きょうけん", "けいげん"), 0, emptyList(), "Lời giải $id", null, null, null, 1, related)

    private val set = ExamSet(
        "ui-errors", "Đề sổ lỗi", null, "N3", ExamType.FULL, 1_800, null, 0,
        listOf(ExamSection("u1", ExamSectionKind.VOCABULARY, null, 1), ExamSection("u2", ExamSectionKind.GRAMMAR, null, 2)),
        emptyList(),
        listOf(
            q("qk", "u1", QuestionType.KANJI_READING, listOf(ItemRef(ItemType.KANJI, SeedData.K_KEI))),
            q("qv", "u1", QuestionType.CONTEXT, listOf(ItemRef(ItemType.VOCABULARY, SeedData.V_FUERU))).copy(order = 2),
            q("qg", "u2", QuestionType.GRAMMAR_FORM, listOf(ItemRef(ItemType.GRAMMAR, SeedData.G_TABAKARI))),
        ),
    )

    private fun launch(): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = true)
        val exams = AppTestState.appContainer.examRepository
        runBlocking {
            exams.saveExamSet(set)
            // 1st: kanji and grammar wrong, vocabulary right. 2nd: kanji wrong again.
            listOf(mapOf("qk" to 1, "qv" to 0, "qg" to 2), mapOf("qk" to 2, "qv" to 0, "qg" to 0)).forEach { picks ->
                val a = exams.startAttempt(set.id)!!
                picks.forEach { (id, i) -> exams.answer(a.id, id, AnswerInput.Choice(i), 2_000) }
                exams.submit(a.id)
            }
        }
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun clickable(text: String): SemanticsNodeInteraction = composeRule.onNode(hasText(text) and hasClickAction())

    /** A chip of one filter row (several rows have "Tất cả"). */
    private fun chip(row: String, text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag(row))
        composeRule.onNodeWithTag(row).performScrollTo().performScrollToNode(matcher)
        return composeRule.onNode(matcher)
    }

    private fun openNotebook() {
        composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        clickable(string(R.string.mistakes_title)).performScrollTo().performClick()
        waitFor(string(R.string.errors_overview_total))
    }

    private fun errorId(questionId: String) = runBlocking { AppTestState.appContainer.errorRepository.findByQuestion(questionId)!! }

    @Test
    fun searchAndFilters_narrowTheList() {
        launch().use {
            openNotebook()
            waitFor(string(R.string.errors_count_shown, 2))

            composeRule.onNode(hasSetTextAction()).performTextInput("qk")
            waitFor(string(R.string.errors_count_shown, 1))
            composeRule.onNodeWithTag(ErrorNotebookTags.row(errorId("qk"))).assertExists()
            composeRule.onNode(hasSetTextAction()).performTextClearance()
            waitFor(string(R.string.errors_count_shown, 2))

            chip(ErrorNotebookTags.SKILLS, string(R.string.exam_skill_grammar)).performClick()
            waitFor(string(R.string.errors_count_shown, 1))
            composeRule.onNodeWithTag(ErrorNotebookTags.row(errorId("qg"))).assertExists()
            chip(ErrorNotebookTags.SKILLS, string(R.string.common_all)).performClick()

            chip(ErrorNotebookTags.STATES, string(R.string.errors_filter_recurring)).performClick()
            waitFor(string(R.string.errors_count_shown, 1))
            composeRule.onNodeWithTag(ErrorNotebookTags.row(errorId("qk"))).assertExists()

            chip(ErrorNotebookTags.STATES, string(R.string.errors_filter_mastered)).performClick()
            waitFor(string(R.string.errors_none_match))
            chip(ErrorNotebookTags.STATES, string(R.string.common_all)).performClick()
            chip(ErrorNotebookTags.LEVELS, "N2").performClick()
            waitFor(string(R.string.errors_none_match))
        }
    }

    @Test
    fun theDetail_showsTheError_savesFlagsAndNotes_opensTheLesson_andReviewsUpdateTheState() {
        launch().use {
            openNotebook()
            val id = errorId("qk")
            composeRule.onNodeWithTag(ErrorNotebookTags.row(id)).performClick()
            waitFor(string(R.string.mistake_correct_answer, "けいけん"))
            val today = formatDate(System.currentTimeMillis())
            waitFor(string(R.string.error_times, 2, today, today))
            waitFor(string(R.string.error_history))
            waitFor(string(R.string.error_reviews_none))

            // Needs attention.
            composeRule.onNode(hasContentDescription(string(R.string.error_mark_important))).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasContentDescription(string(R.string.error_unmark_important))).fetchSemanticsNodes().isNotEmpty()
            }

            // A note.
            composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("nhầm âm on")
            clickable(string(R.string.error_note_save)).performScrollTo().performClick()
            waitFor(string(R.string.error_note_saved))

            // Understood: noted, still not mastered.
            clickable(string(R.string.error_understood)).performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                runBlocking { AppTestState.appContainer.errorRepository.observeError(id).first()!!.understoodAt } != null
            }
            val saved = runBlocking { AppTestState.appContainer.errorRepository.observeError(id).first()!! }
            assertEquals("nhầm âm on", saved.record.notes)
            assertEquals(true, saved.isImportant)
            assertNotNull(saved.understoodAt)
            assertEquals(ErrorState.NEW, saved.state)

            // The lesson the kanji is studied in, and the kanji itself.
            clickable(string(R.string.mistake_lesson, 1)).performScrollTo().performClick()
            waitFor(string(R.string.lesson_detail_title))
            Espresso.pressBack()
            waitFor(string(R.string.error_detail_title))

            // Review now: right answer, the state becomes "Đang ôn" and the review is listed.
            clickable(string(R.string.error_review_now)).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(ErrorReviewTags.option(0))).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag(ErrorReviewTags.option(0)).performClick()
            clickable(string(R.string.error_review_check)).performClick()
            waitFor(string(R.string.error_review_right))
            // Not due yet (the first review is a day after the exam): practice only.
            waitFor(string(R.string.error_review_practice))
            clickable(string(R.string.error_session_finish)).performScrollTo().performClick()
            waitFor(string(R.string.error_session_summary, 1, 1))
            clickable(string(R.string.error_review_done)).performScrollTo().performClick()
            waitFor(string(R.string.error_state_reviewing))
            assertEquals(1, runBlocking { AppTestState.appContainer.errorRepository.observeReviewLog(id).first().size })

            // Back in the list it is "Đang ôn".
            Espresso.pressBack()
            chip(ErrorNotebookTags.STATES, string(R.string.errors_filter_reviewing)).performClick()
            waitFor(string(R.string.errors_count_shown, 1))
            composeRule.onNodeWithTag(ErrorNotebookTags.row(id)).assertExists()
        }
    }

    @Test
    fun dueErrors_showOnTheReviewTab_andASessionGoesThroughThemWithAResult() {
        launch().use {
            // As if the review dates had come: due a day ago.
            runBlocking {
                AppTestState.appContainer.database.openHelper.writableDatabase
                    .execSQL("UPDATE review_items SET next_review_at = ${System.currentTimeMillis() - 24 * 60 * 60_000L}")
            }
            composeRule.onNodeWithTag(TopLevelDestination.REVIEW.testTag).performClick()
            waitFor(string(R.string.review_error_card_body, 2))
            clickable(string(R.string.review_error_card_action)).performClick()
            waitFor(string(R.string.error_session_due, 2))
            repeat(2) { i ->
                composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(ErrorReviewTags.option(0))).fetchSemanticsNodes().isNotEmpty() }
                composeRule.onNodeWithTag(ErrorReviewTags.option(0)).performClick()
                clickable(string(R.string.error_review_check)).performClick()
                waitFor(string(R.string.error_review_right))
                waitFor(string(R.string.error_level_change, 0, 1, 5))
                clickable(string(if (i == 0) R.string.error_session_next else R.string.error_session_finish)).performScrollTo().performClick()
            }
            waitFor(string(R.string.error_session_summary, 2, 2))
            clickable(string(R.string.error_review_done)).performClick()
            // Nothing due any more: the card is gone.
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText(string(R.string.review_error_card_title)).fetchSemanticsNodes().isEmpty()
            }
        }
    }
}
