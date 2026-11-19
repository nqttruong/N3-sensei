package com.n3sensei.app.feature.contrast

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole Contrast Lab on the debug preview pack, as a tester sees it in a debug build: the home is
 * no longer empty, every screen shows "NỘI DUNG THỬ NGHIỆM — CHƯA KIỂM DUYỆT", the library lists the 69
 * groups, Compare shows the theory and examples, and a practice session (one answer wrong, one
 * skipped, the rest right) ends on the summary. The old SRS is not touched.
 */
@RunWith(AndroidJUnit4::class)
class ContrastDebugPreviewEndToEndTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun string(id: Int, vararg args: Any) = instrumentation.targetContext.getString(id, *args)
    private val container get() = AppTestState.appContainer
    private val db get() = container.database

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun tagExists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun thePreviewPack_fillsTheWholeContrastLab_withTheUnreviewedLabel() {
        runBlocking {
            AppTestState.reset(seedContent = false, bundledContent = true, contrastPreview = true)
            container.contentImportRepository.installBundledContent()
        }
        assertEquals(69, db.countRows("contrast_groups"))
        val oldSrs = db.countRows("learning_progress") to db.countRows("review_records")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            waitFor { exists(string(R.string.learn_contrast_title)) }
            compose.onNode(hasText(string(R.string.learn_contrast_title)) and hasClickAction()).performScrollTo().performClick()
            // Home: not empty, the real number of groups, the label.
            waitFor { tagExists(ContrastTags.HOME) && tagExists(ContrastPreviewTags.BANNER) }
            assertTrue(!exists(string(R.string.contrast_empty)))
            assertTrue(exists(string(R.string.contrast_preview_banner)))
            waitFor { compose.onAllNodes(hasContentDescription("${string(R.string.contrast_overview_available)}: 69")).fetchSemanticsNodes().isNotEmpty() }
            // Library: search a group, open Compare.
            compose.onNodeWithTag(ContrastTags.EXPLORE).performScrollTo().performClick()
            waitFor { tagExists(ContrastTags.SEARCH) && tagExists(ContrastPreviewTags.BANNER) }
            compose.onNodeWithTag(ContrastTags.SEARCH).performTextInput("鳥")
            waitFor { exists("鳥 / 島") }
            compose.onNode(hasText("鳥 / 島") and hasClickAction()).performClick()
            // Compare: both items, an example, the label.
            waitFor { tagExists(ContrastTags.itemCard("ci-k-tori-shima-tori")) }
            assertTrue(tagExists(ContrastPreviewTags.BANNER))
            compose.onNodeWithTag(ContrastTags.example("cx-k-tori-shima-3")).performScrollTo()
            assertEquals(0, db.countRows("contrast_attempts"))
            // Practice the group: Q1 wrong, Q2 skipped, the rest right.
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performScrollTo().performClick()
            var answered = 0
            repeat(12) {
                waitFor { tagExists(PracticeTags.CHECK) || tagExists(SummaryTags.SCREEN) }
                if (tagExists(SummaryTags.SCREEN)) return@repeat
                assertTrue(tagExists(ContrastPreviewTags.BANNER))
                val q = runBlocking {
                    val s = db.contrastDao().activeSession()!!
                    val done = db.contrastDao().attemptsOfSession(s.id).map { it.position }.toSet()
                    container.contrastContentRepository.getQuestion(db.contrastDao().sessionItems(s.id).first { it.position !in done }.questionId)!!
                }
                if (answered == 1) {
                    compose.onNodeWithTag(PracticeTags.SKIP).performClick()
                } else {
                    val choice = if (answered == 0) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId
                    val tag = if (q.type == ContrastQuestionType.ERROR_DETECTION) PracticeTags.segment(choice) else PracticeTags.option(choice)
                    compose.onNodeWithTag(tag).performScrollTo().performClick()
                    compose.onNodeWithTag(PracticeTags.CHECK).performClick()
                }
                waitFor { tagExists(PracticeTags.NEXT) }
                compose.onNodeWithTag(PracticeTags.NEXT).performClick()
                answered++
            }
            waitFor { tagExists(SummaryTags.SCREEN) }
        }
        assertEquals(1, db.countRows("contrast_attempts WHERE outcome = 'INCORRECT'"))
        assertEquals(1, db.countRows("contrast_attempts WHERE outcome = 'SKIPPED'"))
        assertTrue(db.countRows("contrast_attempts WHERE outcome = 'CORRECT'") >= 1)
        assertEquals(oldSrs, db.countRows("learning_progress") to db.countRows("review_records"))
    }
}
