package com.n3sensei.app.feature.contrast

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastMasteryEvaluator
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.ResolvedSource
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.SourceRefStatus
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/**
 * The three Contrast Lab screens rendered from states (no database): content, empty, error,
 * groups of 3 items, source notices, long content, dark mode and a large font. Content comes from
 * the TEST-ONLY copy of the sample pack, parsed and validated by the production code.
 */
@RunWith(AndroidJUnit4::class)
class ContrastScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val pack = ContrastPackCheck.check(
        instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) },
    ).validation.pack!!
    private val grammar = pack.groups[0]
    private val vocabulary = pack.groups[1]
    private val noLinks = SourceLinks({}, {}, {})

    private fun row(g: ValidatedContrastGroup, mastery: ContrastMastery = ContrastMastery.NEW, due: Boolean = false) = ContrastGroupRow(g.detail.group, mastery, due)

    private fun learning(g: ValidatedContrastGroup, sources: Map<String, ResolvedSource> = emptyMap()): UiState<ContrastLearningContent> {
        val mastery = ContrastMasteryEvaluator().evaluate(g.detail.group.id, null, emptyList(), emptyList(), ZoneOffset.UTC)
        val all = g.detail.items.associate { it.id to (sources[it.id] ?: ResolvedSource(SourceRefStatus.OK, it.source.sourceContentId)) }
        return UiState.Success(ContrastLearningContent.Group(g.detail.copy(sources = all), mastery, isDue = false))
    }

    /** The vocabulary group with a third item (冷える), to check groups of more than two. */
    private fun threeItems(): ValidatedContrastGroup {
        val third = ContrastItem(
            "ci-v-hieru", "冷える", "{冷|ひ}える", vocabulary.detail.items[0].source.copy(sourceContentId = "mimikara-n3-v209"),
            ItemDetails.Vocabulary("ひえる", "bị lạnh đi", "Nhiệt độ của vật hay cơ thể hạ xuống.", emptyList()),
        )
        return vocabulary.copy(detail = vocabulary.detail.copy(items = vocabulary.detail.items + third, group = vocabulary.detail.group.copy(itemLabels = vocabulary.detail.group.itemLabels + "冷える")))
    }

    // ---- Home ----

    @Test
    fun home_figures_sections_andExploreButton() {
        var opened: String? = null
        var library = false
        val data = ContrastHomeUiState(3, 1, 0, 1, continueGroup = row(grammar, ContrastMastery.LEARNING, due = true), dueGroups = listOf(row(grammar, ContrastMastery.LEARNING, due = true)),
            confusions = listOf(ConfusionRow(grammar.detail.group.id, grammar.detail.group.title, "〜ことになっている", "〜ことにしている", 2)))
        compose.setContent { N3SenseiTheme { ContrastHomeContent(UiState.Success(data), { opened = it }, { library = true }, {}) } }
        compose.onNodeWithContentDescription("${string(R.string.contrast_overview_available)}: 3").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_continue_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_confused_row, "〜ことになっている", "〜ことにしている", 2)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ContrastTags.EXPLORE).performScrollTo().performClick()
        assertEquals(true, library)
        compose.onAllNodesWithText(grammar.detail.group.itemLabels.joinToString(" / ")).onFirst().performClick()
        assertEquals(grammar.detail.group.id, opened)
    }

    @Test
    fun home_emptyAndErrorStates() {
        var state: UiState<ContrastHomeUiState> by mutableStateOf(UiState.Empty)
        var retried = false
        compose.setContent { N3SenseiTheme { ContrastHomeContent(state, {}, {}, { retried = true }) } }
        compose.onNodeWithText(string(R.string.contrast_empty)).assertIsDisplayed()
        state = UiState.Error(IllegalStateException("x"))
        compose.onNodeWithText(context.getString(R.string.common_retry)).performClick()
        assertEquals(true, retried)
    }

    // ---- Library ----

    @Test
    fun library_rowsFiltersAndNoMatch() {
        var type: ContrastContentType? = ContrastContentType.GRAMMAR
        var status: LibraryStatus? = null
        val rows = pack.groups.map { row(it) }
        var state: UiState<ContrastLibraryUiState> by mutableStateOf(UiState.Success(ContrastLibraryUiState("", null, null, rows, 3)))
        compose.setContent { N3SenseiTheme { ContrastLibraryContent(state, "", {}, { type = it }, { status = it }, {}, {}) } }
        compose.onAllNodesWithText("ことにしている", substring = true).onFirst().assertIsDisplayed()
        // The chip of the type filter (the kanji group's card also carries a "Kanji" tag).
        compose.onNode(hasText(string(R.string.contrast_type_kanji)) and hasAnyAncestor(hasTestTag(ContrastTags.TYPE_FILTER))).performClick()
        assertEquals(ContrastContentType.KANJI, type)
        compose.onNodeWithText(string(R.string.contrast_status_needs_review)).performScrollTo().performClick()
        assertEquals(LibraryStatus.NEEDS_REVIEW, status)
        state = UiState.Success(ContrastLibraryUiState("zzz", null, null, emptyList(), 3))
        compose.onNodeWithText(string(R.string.contrast_library_no_match)).assertIsDisplayed()
    }

    // ---- Compare ----

    @Test
    fun compare_grammar_allSections_examplesWithFurigana_andSourceLink() {
        var openedGrammar: String? = null
        compose.setContent { N3SenseiTheme { ContrastGroupContent(learning(grammar), SourceLinks({ openedGrammar = it }, {}, {}), {}) } }
        compose.onAllNodesWithText("〜ことにしている").onFirst().assertIsDisplayed()
        compose.onNodeWithTag(ContrastTags.itemCard("ci-g-koto-ni-natteiru")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_section_examples)).performScrollTo().assertIsDisplayed()
        // The example's Japanese is shown (the ruby text is drawn above it).
        compose.onNodeWithTag(ContrastTags.example("cx-g-koto-a1")).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("私は寝る前に日記を書くことにしている。", substring = true), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(string(R.string.contrast_section_mistakes)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_license_unverified)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.contrast_open_source)).onFirst().performScrollTo().performClick()
        assertEquals("n3g-g001", openedGrammar)
    }

    @Test
    fun compare_sourceNotices_changedMissingAmbiguous_neverHideTheContent() {
        val sources = mapOf(
            "ci-v-sameru-cold" to ResolvedSource(SourceRefStatus.SOURCE_CHANGED, "mimikara-n3-v211"),
            "ci-v-sameru-awake" to ResolvedSource(SourceRefStatus.SOURCE_MISSING),
        )
        compose.setContent { N3SenseiTheme { ContrastGroupContent(learning(vocabulary, sources), noLinks, {}) } }
        compose.onNodeWithText(string(R.string.contrast_source_changed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_source_missing, "覚める · さめる · thức dậy")).performScrollTo().assertIsDisplayed()
        // The reviewed Contrast content is still there for both items.
        compose.onNodeWithText("nguội đi; (cảm xúc) nguội lạnh").assertExists()
        compose.onNodeWithText("tỉnh ra, thức giấc").assertExists()
    }

    @Test
    fun compare_threeItems_ambiguous_kanjiFields_andRetired() {
        var state by mutableStateOf(learning(threeItems(), mapOf("ci-v-hieru" to ResolvedSource(SourceRefStatus.AMBIGUOUS, candidateIds = listOf("a", "b")))))
        compose.setContent { N3SenseiTheme { ContrastGroupContent(state, noLinks, {}) } }
        compose.onNodeWithTag(ContrastTags.itemCard("ci-v-hieru")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ContrastTags.sourceNotice("ci-v-hieru")).performScrollTo().assertIsDisplayed()
        state = learning(pack.groups[2])
        // Both kanji items have a shape note.
        compose.onAllNodesWithText(string(R.string.contrast_field_shape)).onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("禾 · Hòa (cây lúa)").performScrollTo().assertIsDisplayed()
        state = UiState.Success(ContrastLearningContent.Retired(RetiredGroup("g", 2, "Gộp nhóm", null)))
        compose.onNodeWithText(string(R.string.contrast_retired, "Gộp nhóm")).assertIsDisplayed()
        state = UiState.NotFound
        compose.onNodeWithText(string(R.string.common_not_found)).assertIsDisplayed()
    }

    @Test
    fun compare_darkMode_andLargeFont_keepEveryPartReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                N3SenseiTheme(darkTheme = true) { ContrastGroupContent(learning(grammar), noLinks, {}) }
            }
        }
        compose.onNodeWithTag(ContrastTags.itemCard("ci-g-koto-ni-shiteiru")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ContrastTags.example("cx-g-koto-b2")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_section_tips)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.contrast_license_unverified)).performScrollTo().assertIsDisplayed()
    }
}
