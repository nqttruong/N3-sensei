package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.ExamTestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Phân tích JLPT" in the real app, from the Stats tab, with TEST DATA ONLY exams. */
@RunWith(AndroidJUnit4::class)
class ExamDashboardUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun launch(exams: Int, theme: ThemeMode = ThemeMode.SYSTEM): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = false)
        val c = AppTestState.appContainer
        runBlocking {
            c.userPreferencesRepository.setThemeMode(theme)
            c.examRepository.saveExamSet(ExamTestData.set())
            repeat(exams) {
                val a = c.examRepository.startAttempt(ExamTestData.SET_ID)!!
                c.examRepository.answer(a.id, ExamTestData.Q_KANJI, AnswerInput.Choice(1), 2_000)
                c.examRepository.answer(a.id, ExamTestData.Q_READ_1, AnswerInput.Choice(1), 2_000)
                c.examRepository.submit(a.id)
            }
            // As if the review dates had come.
            c.database.openHelper.writableDatabase.execSQL("UPDATE review_items SET next_review_at = ${System.currentTimeMillis() - 60_000}")
        }
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun clickable(text: String): SemanticsNodeInteraction = composeRule.onNode(hasText(text) and hasClickAction())

    private fun openDashboard() {
        composeRule.onNodeWithTag(TopLevelDestination.STATS.testTag).performClick()
        clickable(string(R.string.jlpt_dashboard_entry)).performScrollTo().performClick()
        waitFor(string(R.string.jlpt_overview))
    }

    @Test
    fun noExam_saysSo_andShowsZeros() {
        launch(exams = 0).use {
            openDashboard()
            waitFor(string(R.string.jlpt_no_history))
            waitFor(string(R.string.jlpt_plan_empty))
            composeRule.onNode(hasText(string(R.string.jlpt_not_official))).performScrollTo()
        }
    }

    @Test
    fun theOverviewPlanSkillsAndProgress_comeFromTheExams_andTasksOpenTheirScreens() {
        launch(exams = 2, theme = ThemeMode.DARK).use {
            openDashboard()
            // 2 exams × 6 questions, 4 answered, 2 right; 5 open errors, all due.
            waitFor(string(R.string.jlpt_progress_questions, 12, 0))
            // The plan: five errors (kanji 1, grammar 1, reading 1, listening 2) at 90 s each fit in 20 minutes.
            val reading = string(R.string.jlpt_task_errors, 1, string(R.string.exam_skill_reading).lowercase())
            waitFor(reading)
            waitFor(string(R.string.jlpt_plan_total, 8, 20))

            // Another budget is saved and the plan recomputed (cutting it short is covered by DailyPlannerTest).
            clickable(string(R.string.jlpt_plan_minutes, 10)).performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                runBlocking { AppTestState.appContainer.userPreferencesRepository.preferences.first().dailyStudyMinutes } == 10
            }
            waitFor(string(R.string.jlpt_plan_total, 8, 10))

            // A task opens the review of those errors.
            clickable(string(R.string.jlpt_plan_minutes, 20)).performScrollTo().performClick()
            waitFor(string(R.string.jlpt_plan_total, 8, 20))
            val row = (0 until 5).first { i ->
                runCatching { composeRule.onNodeWithTag(ExamDashboardTags.task(i)).assert(hasAnyDescendant(hasText(reading))) }.isSuccess
            }
            composeRule.onNode(hasText(string(R.string.jlpt_task_start)) and hasAnyAncestor(hasTestTag(ExamDashboardTags.task(row))))
                .performScrollTo().performClick()
            // Only the reading error, one card.
            waitFor(string(R.string.error_session_due, 1))
            Espresso.pressBack()
            waitFor(string(R.string.jlpt_overview))

            // A skill opens its details, and from there the notebook filtered to it.
            composeRule.onNodeWithTag(ExamDashboardTags.skill(ExamSkill.READING)).performScrollTo().performClick()
            waitFor(string(R.string.skill_by_type))
            waitFor(string(R.string.skill_open_errors, 1))
            clickable(string(R.string.skill_open_notebook)).performScrollTo().performClick()
            waitFor(string(R.string.errors_count_shown, 1))
            assertEquals(1, composeRule.onAllNodesWithText(string(R.string.errors_count_shown, 1)).fetchSemanticsNodes().size)
        }
    }
}
