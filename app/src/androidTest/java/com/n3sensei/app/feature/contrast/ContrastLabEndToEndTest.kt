package com.n3sensei.app.feature.contrast

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.AppScreen
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.data.importing.ContrastContentImporter
import com.n3sensei.app.data.importing.ContrastImportReport
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule

/**
 * Contrast Lab in the full app, on the real Room database: the real bundled source packs plus the
 * TEST-ONLY copy of the sample pack imported by the real importer (the app ships no Contrast pack).
 * Learn → Contrast Lab → Library → Compare and back, searches, and no learner data written by reading.
 */
@RunWith(AndroidJUnit4::class)
class ContrastLabEndToEndTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun string(id: Int) = context.getString(id)
    private val db get() = AppTestState.appContainer.database

    private fun installContent(withContrast: Boolean = true) = runBlocking {
        AppTestState.reset(seedContent = false, bundledContent = true)
        // Sources first (as installBundledContent does), then the test pack.
        AppTestState.appContainer.contentImportRepository.installBundledContent()
        if (withContrast) {
            val text = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }
            val report = ContrastContentImporter(db.contrastDao(), AppTestState.appContainer.clock).import(text)
            assertEquals(ContrastContentImporter.summary(report), ContrastImportReport.Outcome.IMPORTED, report.outcome)
        }
    }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun exists(text: String, substring: Boolean = false) = compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    private fun awaitClickable(text: String): SemanticsNodeInteraction {
        waitFor { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        return compose.onAllNodes(hasText(text) and hasClickAction()).onFirst()
    }

    private fun learnerRows() = listOf("contrast_attempts", "contrast_sessions", "contrast_progress", "contrast_reviews").sumOf { db.countRows(it) }

    private fun openContrastFromLearn() {
        compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        waitFor { exists(string(R.string.learn_contrast_title)) }
        compose.onNodeWithText(string(R.string.learn_contrast_title)).performScrollTo().performClick()
        waitFor { compose.onAllNodes(hasTestTag(ContrastTags.HOME)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun learnHub_home_library_compare_andBack_readingWritesNothing() {
        installContent()
        ActivityScenario.launch(MainActivity::class.java).use {
            // Still five tabs.
            assertEquals(5, TopLevelDestination.entries.size)
            openContrastFromLearn()
            // The real figure: the three groups of the imported pack.
            waitFor { runCatching { compose.onNodeWithContentDescription("${string(R.string.contrast_overview_available)}: 3").assertExists() }.isSuccess }
            compose.onNodeWithTag(ContrastTags.EXPLORE).performScrollTo().performClick()

            // Library: type filter, then searches in Japanese and in Vietnamese without accents.
            waitFor { exists("〜ことにしている / 〜ことになっている") }
            awaitClickable(string(R.string.contrast_type_kanji)).performClick()
            waitFor { exists("積 / 績") && !exists("〜ことにしている / 〜ことになっている") }
            awaitClickable(string(R.string.common_all)).performClick()
            compose.onNodeWithTag(ContrastTags.SEARCH).performTextInput("冷める")
            waitFor { exists("冷める / 覚める") && !exists("積 / 績") }
            compose.onNodeWithTag(ContrastTags.SEARCH).performTextClearance()
            compose.onNodeWithTag(ContrastTags.SEARCH).performTextInput("thoi quen")
            waitFor { exists("〜ことにしている / 〜ことになっている") && !exists("冷める / 覚める") }

            // Compare: both items, sources verified against Room (no notice), an example.
            awaitClickable("〜ことにしている / 〜ことになっている").performClick()
            waitFor { compose.onAllNodes(hasTestTag(ContrastTags.itemCard("ci-g-koto-ni-natteiru"))).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(ContrastTags.itemCard("ci-g-koto-ni-shiteiru")).performScrollTo().assertIsDisplayed()
            assertTrue(compose.onAllNodes(hasTestTag(ContrastTags.sourceNotice("ci-g-koto-ni-shiteiru"))).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag(ContrastTags.example("cx-g-koto-b1")).performScrollTo().assertIsDisplayed()
            assertEquals(0, learnerRows())

            // Back: the library keeps the search; then home; then the Learn hub.
            Espresso.pressBack()
            waitFor { exists("thoi quen") && exists("〜ことにしている / 〜ことになっている") && !exists("冷める / 覚める") }
            Espresso.pressBack()
            waitFor { compose.onAllNodes(hasTestTag(ContrastTags.HOME)).fetchSemanticsNodes().isNotEmpty() }
            Espresso.pressBack()
            waitFor { exists(string(R.string.learn_contrast_title)) }
            compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).assertIsDisplayed()
        }
        // Reading the theory created no session, attempt, progress or review.
        assertEquals(0, learnerRows())
        // A new start of the app: the group is still read from Room.
        ActivityScenario.launch(MainActivity::class.java).use {
            openContrastFromLearn()
            compose.onNodeWithTag(ContrastTags.EXPLORE).performScrollTo().performClick()
            waitFor { exists("積 / 績") }
        }
    }

    @Test
    fun withoutAReviewedPack_theHomeSaysSo() {
        installContent(withContrast = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            openContrastFromLearn()
            waitFor { exists(string(R.string.contrast_empty)) }
            assertTrue(AppScreen.entries.filter { it.name.startsWith("CONTRAST_") }.all { it.tab == TopLevelDestination.LEARN && !it.isTabRoot })
        }
    }
}
