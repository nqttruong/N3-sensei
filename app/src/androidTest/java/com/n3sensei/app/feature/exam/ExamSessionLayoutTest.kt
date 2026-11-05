package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AttemptStatus
import com.n3sensei.app.domain.exam.ExamAnswer
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamSessionStatus
import com.n3sensei.app.testing.ExamTestData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exam screen on small, medium and large phones, light and dark, and with large text: the
 * header (title, timer, submit), the options and the previous/next buttons all stay on screen,
 * and what is shown follows the state (answers, flags, current question, timer).
 */
@RunWith(AndroidJUnit4::class)
class ExamSessionLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private data class Device(val width: Dp, val height: Dp, val dark: Boolean, val fontScale: Float = 1f)

    private val set = ExamTestData.set()
    private val attempt = ExamAttempt("a", set.id, set.title, 0, null, 1_800, null, null, set.questions.size, AttemptStatus.IN_PROGRESS)
    private var device by mutableStateOf(Device(360.dp, 640.dp, dark = false))
    private var state by mutableStateOf(
        ExamSessionUiState(ExamSessionStatus.IN_PROGRESS, set, attempt, currentIndex = 0, remainingMillis = 1_800_000),
    )

    /** Stands in for the ViewModel: each action changes the state the way it would. */
    private fun show() = composeRule.setContent {
        val d = device
        N3SenseiTheme(darkTheme = d.dark) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, d.fontScale)) {
                Surface(Modifier.requiredSize(d.width, d.height).testTag(ROOT)) {
                    Box {
                        ExamSessionContent(
                            state = state,
                            onAnswer = { input ->
                                val q = state.currentQuestion!!
                                val answer = ExamAnswer("a", q.id, (input as? AnswerInput.Choice)?.index, (input as? AnswerInput.Text)?.value, false, 0, null, 0, 0)
                                state = state.copy(answers = state.answers + (q.id to answer))
                            },
                            onGoTo = { state = state.copy(currentIndex = it) },
                            onPrevious = { state = state.copy(currentIndex = state.currentIndex - 1) },
                            onNext = { state = state.copy(currentIndex = state.currentIndex + 1) },
                            onToggleFlag = {
                                val id = state.currentQuestion!!.id
                                state = state.copy(flagged = if (id in state.flagged) state.flagged - id else state.flagged + id)
                            },
                            onSubmit = {},
                            onDone = {},
                        )
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.assertInside() {
        assertIsDisplayed()
        val bounds = getBoundsInRoot()
        val root = composeRule.onNodeWithTag(ROOT).getBoundsInRoot()
        assertTrue("$bounds outside $root on $device", bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom)
    }

    private fun assertEssentialsOnScreen() {
        composeRule.onNodeWithTag(ExamSessionTags.TIMER).assertInside()
        composeRule.onNode(hasText(string(R.string.exam_submit)) and hasClickAction()).assertInside()
        composeRule.onNode(hasText(string(R.string.exam_previous)) and hasClickAction()).assertInside()
        composeRule.onNode(hasText(string(R.string.exam_next)) and hasClickAction()).assertInside()
        composeRule.onNodeWithTag(ExamSessionTags.cell(0)).assertInside()
        composeRule.onNodeWithText(string(R.string.exam_question_position, state.currentIndex + 1, set.questions.size)).assertIsDisplayed()
    }

    @Test
    fun everyPhoneSize_lightAndDark_keepsTheHeaderOptionsAndButtonsOnScreen() {
        show()
        listOf(
            Device(320.dp, 568.dp, dark = false),
            Device(320.dp, 568.dp, dark = true),
            Device(360.dp, 640.dp, dark = false),
            Device(360.dp, 640.dp, dark = true),
            Device(411.dp, 820.dp, dark = false),
            Device(411.dp, 820.dp, dark = true),
            Device(320.dp, 568.dp, dark = true, fontScale = 1.5f),
        ).forEach { d ->
            device = d
            state = state.copy(currentIndex = 0)
            composeRule.waitForIdle()
            assertEssentialsOnScreen()
            // The four options of the first question are reachable (the body scrolls if needed).
            (0..3).forEach { composeRule.onNodeWithTag(ExamSessionTags.option(it)).assertExists() }
            composeRule.onNodeWithTag(ExamSessionTags.option(0)).assertInside()
        }
    }

    @Test
    fun answersFlagsAndTheCurrentQuestion_showInTheQuestionNumbers() {
        show()
        val answered = string(R.string.exam_legend_answered).lowercase()
        val unanswered = string(R.string.exam_legend_unanswered).lowercase()
        composeRule.onNodeWithTag(ExamSessionTags.cell(0)).assert(hasContentDescription(unanswered, substring = true))
        composeRule.onNodeWithTag(ExamSessionTags.cell(0)).assert(hasContentDescription(string(R.string.exam_cell_current), substring = true))

        composeRule.onNodeWithTag(ExamSessionTags.option(2)).performClick()
        composeRule.onNodeWithTag(ExamSessionTags.cell(0)).assert(hasContentDescription(answered, substring = true))
        composeRule.onNodeWithContentDescription(string(R.string.exam_flag)).performClick()
        composeRule.onNodeWithTag(ExamSessionTags.cell(0)).assert(hasContentDescription(string(R.string.exam_cell_flagged), substring = true))
        composeRule.onNodeWithContentDescription(string(R.string.exam_unflag)).assertIsDisplayed()

        composeRule.onNodeWithTag(ExamSessionTags.cell(3)).performClick()
        composeRule.onNodeWithText(string(R.string.exam_question_position, 4, 6)).assertIsDisplayed()
        composeRule.onNodeWithTag(ExamSessionTags.cell(3)).assert(hasContentDescription(string(R.string.exam_cell_current), substring = true))
        composeRule.onNodeWithTag(ExamSessionTags.cell(0)).assert(hasContentDescription(answered, substring = true))
        // Question 4 shares its reading text with question 3.
        composeRule.onNodeWithText("来週の月曜日は図書館が休みです。").assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.exam_previous)) and hasClickAction()).performClick()
        composeRule.onNodeWithText(string(R.string.exam_question_position, 3, 6)).assertIsDisplayed()
    }

    @Test
    fun theTimer_followsTheTimeLeft_andIsReadAsWords() {
        show()
        fun timer() = composeRule.onNodeWithTag(ExamSessionTags.TIMER)
        timer().assert(hasContentDescription(string(R.string.exam_timer_description, 30, 0)))
        state = state.copy(remainingMillis = 61_500)
        timer().assert(hasContentDescription(string(R.string.exam_timer_description, 1, 2)))
        state = state.copy(remainingMillis = null)
        timer().assert(hasContentDescription(string(R.string.exam_timer_untimed)))
    }

    private companion object {
        const val ROOT = "exam_layout_root"
    }
}
