package com.n3sensei.app.feature.contrast

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.data.importing.ContrastPackCheck
import com.n3sensei.app.domain.contrast.Accuracy
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.QuestionContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The practice and summary screens rendered from states (no database), for the four exercise
 * types, the feedback, saving and failure states, and accessibility (dark mode, large font,
 * selectable segments for TalkBack). Questions come from the TEST-ONLY copy of the sample pack.
 */
@RunWith(AndroidJUnit4::class)
class ContrastPracticeScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun string(id: Int, vararg args: Any) = instrumentation.targetContext.getString(id, *args)

    private val pack = ContrastPackCheck.check(
        instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) },
    ).validation.pack!!
    private val grammar = pack.groups[0]
    private fun question(id: String) = pack.groups.flatMap { it.questions }.single { it.id == id }
    private val labels = grammar.detail.items.associate { it.id to it.label }

    private fun item(q: ContrastQuestion, done: Int = 0) = PracticeQuestion("s1", done, q, grammar.detail.group.title, labels, done, 3)

    private fun feedback(q: ContrastQuestion, outcome: AnswerOutcome, chosen: String?, last: Boolean = false) = PracticeFeedback(
        outcome = outcome,
        chosenOptionId = chosen,
        correctOptionId = q.correctOptionId,
        explanationVi = q.explanationVi,
        chosenFeedbackVi = chosen?.takeIf { it != q.correctOptionId }?.let(q::option)?.feedbackVi,
        confusion = if (outcome == AnswerOutcome.INCORRECT) "ci-g-koto-ni-natteiru" to "ci-g-koto-ni-shiteiru" else null,
        retryAdded = outcome == AnswerOutcome.INCORRECT,
        nextDueAt = if (last) Instant.parse("2026-11-20T02:00:00Z") else null,
        sessionCompleted = last,
    )

    private fun show(state: () -> PracticeUiState, actions: PracticeActions = PracticeActions()) =
        compose.setContent { N3SenseiTheme { ContrastPracticeContent(state(), actions) } }

    @Test
    fun multipleChoice_select_check_andWrongAnswerFeedback() {
        val q = question("cq-g-koto-mc1")
        var state: PracticeUiState by mutableStateOf(PracticeUiState.Ready(item(q), null))
        var selected: String? = null
        var submitted = 0
        show({ state }, PracticeActions(select = { selected = it }, submit = { submitted++ }))
        compose.onNodeWithTag(PracticeTags.CHECK).assertIsNotEnabled()
        compose.onNodeWithTag(PracticeTags.option("a")).performClick()
        assertEquals("a", selected)
        state = PracticeUiState.Ready(item(q), "a")
        compose.onNodeWithTag(PracticeTags.option("a")).assertIsSelected()
        compose.onNodeWithTag(PracticeTags.CHECK).assertIsEnabled().performClick()
        assertEquals(1, submitted)
        // No answer is shown before the submit.
        assertEquals(0, compose.onAllNodesWithText(string(R.string.contrast_option_correct)).fetchSemanticsNodes().size)

        state = PracticeUiState.Feedback(item(q), feedback(q, AnswerOutcome.INCORRECT, "a"))
        compose.onNodeWithTag(PracticeTags.FEEDBACK).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_feedback_incorrect)).assertExists()
        compose.onNodeWithText(q.explanationVi).assertExists()
        compose.onNodeWithText(string(R.string.contrast_feedback_confusion, "〜ことになっている", "〜ことにしている")).assertExists()
        // Right and wrong are said in words, not only colours.
        compose.onNodeWithTag(PracticeTags.option("b")).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(R.string.contrast_option_correct)))
        compose.onNodeWithTag(PracticeTags.option("a")).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(R.string.contrast_option_chosen)))
        compose.onNodeWithTag(PracticeTags.NEXT).assertIsDisplayed()
    }

    @Test
    fun fillInTheBlank_showsTheChosenFormInTheBlank() {
        val q = question("cq-g-koto-fb1")
        show({ PracticeUiState.Ready(item(q), "b") })
        compose.onNode(hasText("日本では、車は道の左側を走ることになっている。"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(PracticeTags.option("b")).assertIsSelected()
    }

    @Test
    fun errorDetection_segmentsAreSelectableForTalkBack_andTheWrongOneIsMarked() {
        val q = question("cq-g-koto-ed1")
        var state: PracticeUiState by mutableStateOf(PracticeUiState.Ready(item(q), null))
        var selected: String? = null
        show({ state }, PracticeActions(select = { selected = it }))
        compose.onNodeWithContentDescription(string(R.string.contrast_segment_description, 4, "ことになっている。")).performClick()
        assertEquals("d", selected)
        state = PracticeUiState.Ready(item(q), "d")
        compose.onNodeWithTag(PracticeTags.segment("d")).assertIsSelected()
        state = PracticeUiState.Feedback(item(q), feedback(q, AnswerOutcome.CORRECT, "d"))
        compose.onNodeWithText(string(R.string.contrast_feedback_correction, "ことにしている。")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contextSelection_keepsTheSituationAndEveryJapaneseLine() {
        val base = question("cq-g-koto-cs1")
        val q = base.copy(context = QuestionContext(base.context!!.descriptionVi, "A：会議は何時ですか。\nB：毎週金曜日の十時です。", null))
        show({ PracticeUiState.Ready(item(q), null) })
        compose.onNodeWithTag(PracticeTags.CONTEXT).assertIsDisplayed()
        compose.onNodeWithText(base.context!!.descriptionVi).assertIsDisplayed()
        compose.onNode(hasText("A：会議は何時ですか。"), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("B：毎週金曜日の十時です。"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun skipped_isNotCalledWrong_saving_failure_unavailable_nothing_andConfirm() {
        val q = question("cq-g-koto-fb2")
        var state: PracticeUiState by mutableStateOf(PracticeUiState.Feedback(item(q), feedback(q, AnswerOutcome.SKIPPED, null)))
        var skipped = 0
        var next = 0
        var resumed = 0
        show({ state }, PracticeActions(skip = { skipped++ }, next = { next++ }, continueActive = { resumed++ }))
        compose.onNodeWithText(string(R.string.contrast_feedback_skipped)).performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.contrast_feedback_incorrect)).fetchSemanticsNodes().size)

        state = PracticeUiState.Submitting(item(q), "a")
        compose.onNodeWithText(string(R.string.contrast_practice_saving)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.contrast_practice_check)).fetchSemanticsNodes().size)

        state = PracticeUiState.Ready(item(q), "a", saveFailed = true)
        compose.onNodeWithText(string(R.string.contrast_practice_save_failed)).assertIsDisplayed()
        compose.onNodeWithTag(PracticeTags.option("a")).assertIsSelected()
        compose.onNodeWithTag(PracticeTags.SKIP).performClick()
        assertEquals(1, skipped)

        state = PracticeUiState.QuestionUnavailable("s1", 2)
        compose.onNodeWithText(string(R.string.contrast_practice_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_practice_next)).performClick()
        assertEquals(1, next)

        state = PracticeUiState.NothingToPractice
        compose.onNodeWithText(string(R.string.contrast_practice_nothing)).assertIsDisplayed()

        state = PracticeUiState.ConfirmReplace(listOf("Nhóm A"), 2)
        compose.onNodeWithText(string(R.string.contrast_practice_confirm_body, "Nhóm A", 2)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_practice_continue_old)).performClick()
        assertEquals(1, resumed)
    }

    @Test
    fun lastQuestion_offersTheSummary_inDarkModeWithALargeFont() {
        val q = question("cq-g-koto-cs1")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                N3SenseiTheme(darkTheme = true) { ContrastPracticeContent(PracticeUiState.Feedback(item(q, done = 2), feedback(q, AnswerOutcome.CORRECT, "a", last = true)), PracticeActions()) }
            }
        }
        compose.onNodeWithText(string(R.string.contrast_practice_progress, 3, 3)).assertIsDisplayed()
        compose.onNodeWithTag(PracticeTags.option("c")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PracticeTags.FEEDBACK).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_practice_finish)).assertIsDisplayed()
    }

    @Test
    fun summary_countsAccuracyAndAnswers_withoutCountingPendingAsWrong() {
        var state: UiState<ContrastSessionSummary> by mutableStateOf(
            UiState.Success(
                ContrastSessionSummary(
                    groups = listOf(SummaryGroup("g", grammar.detail.group.title, ContrastMastery.LEARNING, Instant.parse("2026-11-18T02:00:00Z"))),
                    correct = 2, incorrect = 1, skipped = 1, pending = 1, unavailable = 0,
                    accuracy = Accuracy(2, 3),
                    answers = listOf(SummaryAnswer(0, grammar.detail.group.title, ContrastQuestionType.MULTIPLE_CHOICE, AnswerOutcome.INCORRECT, "毎晩、十時に寝ることにしている。", "この寮では、十時に寝ることになっている。")),
                ),
            ),
        )
        var home = 0
        compose.setContent { N3SenseiTheme { ContrastSessionSummaryContent(state, { home++ }, {}, {}) } }
        compose.onNodeWithContentDescription("${string(R.string.contrast_summary_pending)}: 1").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_summary_accuracy, 67, 2, 3)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_summary_handled, 4)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_summary_answer_chosen, "毎晩、十時に寝ることにしている。")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.HOME).performScrollTo().performClick()
        assertEquals(1, home)
        state = UiState.Success((state as UiState.Success).data.copy(correct = 0, incorrect = 0, accuracy = Accuracy(0, 0)))
        compose.onNodeWithText(string(R.string.contrast_summary_accuracy_none)).assertIsDisplayed()
    }
}
