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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.Accuracy
import com.n3sensei.app.domain.contrast.CategoryProgress
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastProgressReport
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.DirectionStatus
import com.n3sensei.app.domain.repository.MistakeSnapshot
import com.n3sensei.app.domain.repository.OutcomeCounts
import com.n3sensei.app.domain.repository.SessionOutcomes
import com.n3sensei.app.domain.repository.TypeOutcomes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** M7 screens rendered from states: errors, review, progress; filters, empty / loading / error, long lists, dark mode + large font. */
@RunWith(AndroidJUnit4::class)
class ContrastInsightsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)
    private val t0 = Instant.parse("2026-11-17T02:00:00Z")

    private fun group(i: Int, type: ContrastContentType = ContrastContentType.GRAMMAR) = ContrastGroup("g$i", "p", type, "N3", "Nhóm $i", "", 1, listOf("A", "B"), 3)

    private fun snapshot(chosen: String, correct: String, revision: Int = 1) =
        MistakeSnapshot("g1", "A", "B", "q1", revision, 1, ContrastQuestionType.MULTIPLE_CHOICE, chosen, correct, t0)

    private fun errors(incorrect: Int = 3) = ContrastErrorsUiState(
        totals = OutcomeCounts(4, incorrect, 1),
        rate = RateView(Accuracy(4, 4 + incorrect), 5),
        directions = if (incorrect == 0) emptyList() else listOf(
            DirectionRow("g1", "Nhóm 1", true, "〜ことになっている", "〜ことにしている", 2, 2, 0, DirectionStatus.TO_REINFORCE, snapshot("毎晩寝ることにしている。", "寮では寝ることになっている。", 1), exampleOutdated = true),
            DirectionRow("old", "Nhóm đã ngừng sử dụng (old)", false, "R", "S", 1, 1, 0, DirectionStatus.SINGLE, null, false),
        ),
        groups = if (incorrect == 0) emptyList() else listOf(ErrorGroupRow("g1", "Nhóm 1", true, 2, RateView(Accuracy(1, 3), 5))),
        types = listOf(ErrorTypeRow(ContrastQuestionType.MULTIPLE_CHOICE, incorrect, RateView(Accuracy(4, 4 + incorrect), 5))),
        recent = if (incorrect == 0) emptyList() else listOf(RecentMistakeRow("Nhóm 1", snapshot("A文", "B文"))),
        otherIncorrect = if (incorrect == 0) 0 else 1,
        sessions = listOf(SessionOutcomes("s1", t0, OutcomeCounts(4, incorrect, 1))),
        recentRate = null,
        earlierRate = null,
        recentSessionCount = 1,
        contentType = null,
        questionType = null,
        groupId = null,
        groupOptions = listOf(group(1)),
    )

    @Test
    fun errors_directionsExampleOldVersion_filters_andBackToCompare() {
        var opened: String? = null
        var type: ContrastContentType? = null
        var qtype: ContrastQuestionType? = null
        compose.setContent { N3SenseiTheme { ContrastErrorsContent(UiState.Success(errors()), { opened = it }, { type = it }, { qtype = it }, {}, {}) } }
        compose.onNodeWithText(string(R.string.contrast_errors_total, 3)).assertIsDisplayed()
        // 7 answered < 5? no: 7 ≥ 5 → a percentage, over answered (skips excluded).
        compose.onNodeWithText(string(R.string.contrast_rate_value, 57, 4, 7)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_errors_direction, "〜ことになっている", "〜ことにしている")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_errors_to_reinforce)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_errors_example_old)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_errors_open_group)).performClick()
        assertEquals("g1", opened)
        // A retired group's direction has no link (one button only, for g1).
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasTestTag(InsightTags.direction("old", "R", "S")))
        assertEquals(1, compose.onAllNodesWithText(string(R.string.contrast_errors_open_group)).fetchSemanticsNodes().size)
        compose.onNodeWithText(typeLabelText(ContrastContentType.VOCABULARY)).performClick()
        assertEquals(ContrastContentType.VOCABULARY, type)
        compose.onNodeWithText(string(R.string.contrast_qtype_ed)).performClick()
        assertEquals(ContrastQuestionType.ERROR_DETECTION, qtype)
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasText(string(R.string.contrast_errors_other, 1)))
    }

    private fun typeLabelText(type: ContrastContentType) = string(
        when (type) {
            ContrastContentType.GRAMMAR -> R.string.contrast_type_grammar
            ContrastContentType.VOCABULARY -> R.string.contrast_type_vocabulary
            ContrastContentType.KANJI -> R.string.contrast_type_kanji
        },
    )

    @Test
    fun errors_emptyLoadingAndError() {
        var state: UiState<ContrastErrorsUiState> by mutableStateOf(UiState.Success(errors(incorrect = 0).copy(totals = OutcomeCounts.NONE, rate = RateView(Accuracy(0, 0), 5))))
        var retried = 0
        compose.setContent { N3SenseiTheme { ContrastErrorsContent(state, {}, {}, {}, {}, { retried++ }) } }
        compose.onNodeWithText(string(R.string.contrast_errors_none)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_rate_none)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("100%", substring = true).fetchSemanticsNodes().size)
        state = UiState.Loading
        compose.onNodeWithContentDescription(string(R.string.common_loading)).assertExists()
        state = UiState.Error(IllegalStateException())
        compose.onNodeWithText(string(R.string.common_retry)).performClick()
        assertEquals(1, retried)
    }

    private fun reviewRow(i: Int, dueAt: Instant = t0, reinforce: Boolean = false) =
        ReviewRow("g$i", "Nhóm $i", ContrastContentType.KANJI, ContrastMastery.LEARNING, dueAt, 2, 6, 3, reinforce)

    @Test
    fun review_startNow_resume_reinforce_unavailable_andALongQueue() {
        var started = 0
        var resumed = 0
        var opened: String? = null
        val upcoming = (10 until 70).map { reviewRow(it, t0.plusSeconds(it * 86_400L)) }
        compose.setContent {
            N3SenseiTheme {
                ContrastReviewContent(
                    UiState.Success(ContrastReviewUiState(listOf(reviewRow(1, reinforce = true)), upcoming, listOf(reviewRow(1, reinforce = true)), listOf("Nhóm đã ngừng sử dụng (x)"), upcoming.first().dueAt, 2, 2)),
                    { started++ }, { resumed++ }, { opened = it }, {},
                )
            }
        }
        compose.onNodeWithText(string(R.string.contrast_review_due, 1)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_review_upcoming_count, 60)).assertIsDisplayed()
        compose.onNodeWithTag(InsightTags.START_REVIEW).performClick()
        assertEquals(1, started)
        compose.onNodeWithTag(InsightTags.RESUME_REVIEW).performClick()
        assertEquals(1, resumed)
        assertTrue(compose.onAllNodesWithText(string(R.string.contrast_review_step, 2, 6, 3), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasText(string(R.string.contrast_review_unavailable_row, "Nhóm đã ngừng sử dụng (x)")))
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasTestTag(InsightTags.reviewRow("g69")))
        compose.onNodeWithTag(InsightTags.reviewRow("g69")).performClick()
        assertEquals("g69", opened)
    }

    @Test
    fun review_nothingDue_hasNoStartButton() {
        var state: UiState<ContrastReviewUiState> by mutableStateOf(UiState.Success(ContrastReviewUiState(emptyList(), listOf(reviewRow(2, t0.plusSeconds(86_400))), emptyList(), emptyList(), t0, 3, null)))
        compose.setContent { N3SenseiTheme { ContrastReviewContent(state, {}, {}, {}, {}) } }
        compose.onNodeWithText(string(R.string.contrast_review_none_due)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.contrast_review_start)).fetchSemanticsNodes().size)
        state = UiState.Success(ContrastReviewUiState(emptyList(), emptyList(), emptyList(), emptyList(), null, 3, null))
        compose.onNodeWithText(string(R.string.contrast_review_nothing_scheduled)).assertIsDisplayed()
        state = UiState.Empty
        compose.onNodeWithText(string(R.string.contrast_empty)).assertIsDisplayed()
    }

    private fun progress(groups: Int = 200): ContrastProgressUiState {
        val rows = (0 until groups).map { i ->
            val counts = if (i == 0) OutcomeCounts(8, 2, 1) else if (i == 1) OutcomeCounts(1, 1, 0) else OutcomeCounts.NONE
            ProgressGroupRow("g$i", "Nhóm $i", ContrastContentType.entries[i % 3], if (i < 2) ContrastMastery.LEARNING else ContrastMastery.NEW, if (i < 2) t0 else null, i == 0, counts, RateView(counts.accuracy, 5))
        }
        val report = ContrastProgressReport(
            published = groups, notStarted = groups - 2, learning = 2, due = 1, mastered = 0,
            totals = OutcomeCounts(9, 3, 1), historical = OutcomeCounts(0, 1, 0),
            categories = ContrastContentType.entries.map { CategoryProgress(it, groups / 3, 1, 0, if (it == ContrastContentType.GRAMMAR) 1 else 0) },
            groups = emptyList(), byQuestionType = listOf(TypeOutcomes(ContrastQuestionType.CONTEXT_SELECTION, OutcomeCounts(1, 1, 0))), nextDueAt = t0,
        )
        return ContrastProgressUiState(report, RateView(Accuracy(9, 12), 5), rows, listOf(ProgressTypeRow(ContrastQuestionType.CONTEXT_SELECTION, RateView(Accuracy(1, 2), 5))))
    }

    @Test
    fun progress_overviewForTalkBack_rates_categories_review_andALongList_inDarkModeWithALargeFont() {
        var review = 0
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                N3SenseiTheme(darkTheme = true) { ContrastProgressContent(UiState.Success(progress()), { review++ }, { opened = it }, {}) }
            }
        }
        compose.onNodeWithContentDescription("${string(R.string.contrast_progress_published)}: 200").assertExists()
        compose.onNodeWithContentDescription("${string(R.string.contrast_progress_mastered)}: 0").assertExists()
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasText(string(R.string.contrast_rate_value, 75, 9, 12)))
        compose.onNodeWithText(string(R.string.contrast_rate_value, 75, 9, 12)).assertIsDisplayed()
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasText(string(R.string.contrast_progress_historical, 1)))
        // A rate on 2 answers is "not enough data", never 50%.
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasText("${string(R.string.contrast_qtype_context)}: ${string(R.string.contrast_rate_short_not_enough, 2)}"))
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasTestTag(InsightTags.PROGRESS_REVIEW))
        compose.onNodeWithTag(InsightTags.PROGRESS_REVIEW).performClick()
        assertEquals(1, review)
        compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasTestTag(InsightTags.progressRow("g199")))
        compose.onNodeWithTag(InsightTags.progressRow("g199")).assertIsDisplayed().performClick()
        assertEquals("g199", opened)
        compose.onNodeWithTag(InsightTags.progressRow("g199")).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick))
    }
}
