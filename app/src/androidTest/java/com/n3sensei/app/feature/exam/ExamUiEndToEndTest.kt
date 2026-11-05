package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.repository.SaveExamResult
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.ExamTestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exam screens in the real app (real database, real clock), with the TEST DATA ONLY set of
 * [ExamTestData] saved for the test: list and filters, intro, answering, flags, jumping,
 * submitting, recreation of the Activity and the automatic submit when the time runs out.
 */
@RunWith(AndroidJUnit4::class)
class ExamUiEndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun launch(durationSeconds: Int = 1_800, theme: ThemeMode = ThemeMode.SYSTEM): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = false)
        runBlocking {
            AppTestState.appContainer.userPreferencesRepository.setThemeMode(theme)
            assertEquals(SaveExamResult.Saved, AppTestState.appContainer.examRepository.saveExamSet(ExamTestData.set().copy(durationSeconds = durationSeconds)))
        }
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(text: String, timeout: Long = 5_000) = composeRule.waitUntil(timeout) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun clickable(text: String): SemanticsNodeInteraction = composeRule.onNode(hasText(text) and hasClickAction())

    private fun openExamList() {
        composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        clickable(string(R.string.learn_exam)).performScrollTo().performClick()
        waitFor("Đề kiểm thử")
    }

    private fun startExam() {
        openExamList()
        clickable("Đề kiểm thử").performClick()
        waitFor(string(R.string.exam_start))
        clickable(string(R.string.exam_start)).performClick()
        waitFor(string(R.string.exam_question_position, 1, 6))
    }

    private fun cell(index: Int) = composeRule.onNodeWithTag(ExamSessionTags.cell(index))

    private fun SemanticsNodeInteraction.describedAs(part: String): Boolean = runCatching {
        assert(hasContentDescription(part, substring = true))
    }.isSuccess

    private fun awaitCell(index: Int, part: String) = composeRule.waitUntil(5_000) { cell(index).describedAs(part) }

    /** The countdown as TalkBack reads it ("Còn 29 phút 58 giây"). */
    private fun timerText(): String =
        composeRule.onNodeWithTag(ExamSessionTags.TIMER).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    /** A filter chip (an exam card also contains the level and skill names). */
    private fun chip(text: String) = composeRule.onNode(chipMatcher(text))

    private fun chipMatcher(text: String) = hasText(text) and hasClickAction() and !hasText("Đề kiểm thử")

    /** A chip of the "Loại đề" row, scrolled into view first (the row is wider than the screen). */
    private fun kindChip(text: String): SemanticsNodeInteraction {
        composeRule.onNodeWithTag(ExamListTags.KINDS).performScrollToNode(chipMatcher(text))
        return chip(text)
    }

    @Test
    fun theList_showsEachSetWithItsFacts_andFiltersByLevelSkillAndFullExam() {
        launch().use {
            openExamList()
            composeRule.onNodeWithText(
                string(R.string.exam_intro_facts, string(R.string.exam_questions_count, 6), string(R.string.exam_duration_minutes, 30)),
            ).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_status_new)).assertIsDisplayed()
            composeRule.onNode(hasText("Đề kiểm thử") and hasText(string(R.string.exam_skill_listening)) and hasText(string(R.string.exam_skill_kanji)))
                .assertIsDisplayed()

            chip("N2").performClick()
            waitFor(string(R.string.exam_list_empty_filter))
            chip("N3").performClick()
            waitFor("Đề kiểm thử")
            // Vocabulary questions: none in this set (its first part is kanji reading).
            kindChip(string(R.string.exam_skill_vocabulary)).performClick()
            waitFor(string(R.string.exam_list_empty_filter))
            kindChip(string(R.string.exam_skill_listening)).performClick()
            waitFor("Đề kiểm thử")
            kindChip(string(R.string.exam_kind_full)).performClick()
            waitFor("Đề kiểm thử")
        }
    }

    @Test
    fun takingTheExam_answersFlagsJumpsAndSubmitsAfterAWarning() {
        launch().use {
            startExam()
            assertTrue(timerText(), timerText() in listOf(30 to 0, 29 to 59, 29 to 58).map { (m, s) -> string(R.string.exam_timer_description, m, s) })
            cell(0).assert(hasContentDescription(string(R.string.exam_legend_unanswered).lowercase(), substring = true))

            composeRule.onNodeWithTag(ExamSessionTags.option(0)).performClick() // right
            awaitCell(0, string(R.string.exam_legend_answered).lowercase())
            composeRule.onNodeWithContentDescription(string(R.string.exam_flag)).performClick()
            awaitCell(0, string(R.string.exam_cell_flagged))

            clickable(string(R.string.exam_next)).performClick()
            waitFor(string(R.string.exam_question_position, 2, 6))
            composeRule.onNode(hasSetTextAction()).performTextInput("ため")
            clickable(string(R.string.exam_text_answer_save)).performClick()
            waitFor(string(R.string.exam_text_answer_saved, "ため"))
            awaitCell(1, string(R.string.exam_legend_answered).lowercase())

            // Jump from the full list of questions.
            composeRule.onNodeWithContentDescription(string(R.string.exam_all_questions)).performClick()
            waitFor(string(R.string.exam_legend_flagged))
            composeRule.onNodeWithTag(ExamSessionTags.sheetCell(4)).performClick()
            waitFor(string(R.string.exam_question_position, 5, 6))
            composeRule.onNodeWithText(string(R.string.exam_audio_unavailable)).assertIsDisplayed()
            awaitCell(4, string(R.string.exam_cell_current))

            // The timer runs.
            val before = timerText()
            composeRule.waitUntil(5_000) { timerText() != before }

            clickable(string(R.string.exam_submit)).performClick()
            waitFor(string(R.string.exam_submit_unanswered, 4))
            composeRule.onNodeWithText(string(R.string.exam_submit_answered, 2, 6)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_submit_flagged, 1)).assertIsDisplayed()
            clickable(string(R.string.exam_submit_keep_going)).performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(string(R.string.exam_submit_title)).fetchSemanticsNodes().isEmpty() }

            clickable(string(R.string.exam_submit)).performClick()
            composeRule.onNode(hasText(string(R.string.exam_submit_confirm)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

            // The result screen: practice score, never called a JLPT score.
            waitFor(string(R.string.exam_result_practice_score))
            composeRule.onNodeWithText(string(R.string.exam_result_submitted)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_score, 2, 6)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_accuracy, 33)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_counts, 2, 0, 4)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_not_official)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_skills)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_compare_first)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_blank_list, 4)).performScrollTo().assertIsDisplayed()

            // A blank question opens the review at that question.
            clickable(string(R.string.exam_result_item_row, 4, string(R.string.exam_skill_reading))).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(ExamReviewTags.item(3))).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag(ExamReviewTags.item(3)).assertIsDisplayed()
            // …and from there, its entry in the error notebook.
            composeRule.onNode(hasText(string(R.string.exam_review_open_error)) and hasAnyAncestor(hasTestTag(ExamReviewTags.item(3)))).performScrollTo().performClick()
            waitFor(string(R.string.error_detail_title))
            waitFor(string(R.string.error_reviews_none))
            Espresso.pressBack()
            Espresso.pressBack()

            clickable(string(R.string.exam_result_review_all)).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(ExamReviewTags.item(0))).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(string(R.string.exam_review_correct_choice) + " · " + string(R.string.exam_review_your_choice)).assertIsDisplayed()
            Espresso.pressBack()

            // The analysis: every blank is "not known", with the reason, the right answer and a fix.
            clickable(string(R.string.exam_result_analyze)).performScrollTo().performClick()
            waitFor(string(R.string.mistakes_analysis_intro))
            composeRule.onNode(hasTestTag(MistakeTags.card(ExamTestData.Q_READ_1)))
                .performScrollTo()
                .assert(hasAnyDescendant(hasText(string(R.string.mistake_evidence_blank), substring = true)))
            Espresso.pressBack()

            // The notebook: four blanks recorded, none fixed yet.
            clickable(string(R.string.exam_result_open_notebook)).performScrollTo().performClick()
            waitFor(string(R.string.errors_count_shown, 4))
            clickable(string(R.string.errors_open_weakness)).performClick()
            waitFor(string(R.string.mistakes_weakness_few, string(R.string.exam_skill_reading), 2, 10))
            Espresso.pressBack()
            Espresso.pressBack()

            clickable(string(R.string.exam_result_retake)).performScrollTo().performClick()
            waitFor(string(R.string.exam_intro_rules))
            waitFor(
                listOf(string(R.string.exam_status_done, 1), string(R.string.exam_last_score, 2, 6)).joinToString(" · "),
            )
            clickable(string(R.string.exam_intro_last_result)).performScrollTo().performClick()
            waitFor(string(R.string.exam_result_practice_score))
        }
    }

    @Test
    fun recreatingTheActivity_orLeavingAndContinuing_keepsTheQuestionAnswersAndFlags() {
        launch().use { scenario ->
            startExam()
            composeRule.onNodeWithTag(ExamSessionTags.option(1)).performClick()
            awaitCell(0, string(R.string.exam_legend_answered).lowercase())
            clickable(string(R.string.exam_next)).performClick()
            waitFor(string(R.string.exam_question_position, 2, 6))
            composeRule.onNodeWithContentDescription(string(R.string.exam_flag)).performClick()
            awaitCell(1, string(R.string.exam_cell_flagged))

            scenario.recreate()
            waitFor(string(R.string.exam_question_position, 2, 6))
            awaitCell(0, string(R.string.exam_legend_answered).lowercase())
            awaitCell(1, string(R.string.exam_cell_flagged))

            // Leaving keeps the attempt; the intro offers to continue it.
            Espresso.pressBack()
            waitFor(string(R.string.exam_resume))
            clickable(string(R.string.exam_resume)).performClick()
            waitFor(string(R.string.exam_question_position, 2, 6))
            awaitCell(0, string(R.string.exam_legend_answered).lowercase())
        }
    }

    @Test
    fun whenTheTimeRunsOut_theExamIsSubmittedByItself_inDarkMode() {
        launch(durationSeconds = 6, theme = ThemeMode.DARK).use {
            startExam()
            composeRule.onNodeWithTag(ExamSessionTags.option(0)).performClick()
            awaitCell(0, string(R.string.exam_legend_answered).lowercase())
            waitFor(string(R.string.exam_result_practice_score), timeout = 10_000)
            composeRule.onNodeWithText(string(R.string.exam_result_expired)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.exam_result_score, 1, 6)).assertIsDisplayed()
            composeRule.onNodeWithText("• " + string(R.string.exam_improve_unanswered_time, 5)).performScrollTo().assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText(string(R.string.exam_next)).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodes(hasTestTag(ExamSessionTags.option(0))).fetchSemanticsNodes().isEmpty())
        }
    }
}
