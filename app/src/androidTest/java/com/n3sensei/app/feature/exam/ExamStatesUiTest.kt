package com.n3sensei.app.feature.exam

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.app.reminder.ReminderDeepLinks
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.ExamTestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** QA: empty, error and deep-link states of the exam screens. Exam data is TEST DATA ONLY. */
@RunWith(AndroidJUnit4::class)
class ExamStatesUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun noExamsAndNoErrors_everyScreenSaysSo() {
        AppTestState.reset(seedContent = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            composeRule.onNode(hasText(string(R.string.learn_exam)) and hasClickAction()).performScrollTo().performClick()
            waitFor(string(R.string.exam_list_empty))
            Espresso.pressBack()
            composeRule.onNode(hasText(string(R.string.mistakes_title)) and hasClickAction()).performScrollTo().performClick()
            waitFor(string(R.string.mistakes_empty))
            // No due errors: no card on the Review tab.
            composeRule.onNodeWithTag(TopLevelDestination.REVIEW.testTag).performClick()
            waitFor(string(R.string.review_title))
            assertTrue(composeRule.onAllNodesWithText(string(R.string.review_error_card_title)).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun theReminderLink_opensTheReviewSession_orSaysNothingIsDue() {
        AppTestState.reset(seedContent = false)
        val link = Intent(Intent.ACTION_VIEW, ReminderDeepLinks.ERROR_REVIEW.toUri(), context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(link).use { waitFor(string(R.string.error_session_none)) }

        runBlocking {
            val c = AppTestState.appContainer
            c.examRepository.saveExamSet(ExamTestData.set())
            val a = c.examRepository.startAttempt(ExamTestData.SET_ID)!!
            c.examRepository.answer(a.id, ExamTestData.Q_KANJI, AnswerInput.Choice(1), 1_000)
            c.examRepository.submit(a.id)
            c.database.openHelper.writableDatabase.execSQL("UPDATE review_items SET next_review_at = ${System.currentTimeMillis() - 60_000}")
        }
        ActivityScenario.launch<MainActivity>(link).use { waitFor(string(R.string.error_session_due, 6)) }
    }
}
