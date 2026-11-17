package com.n3sensei.app.feature.contrast

import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.app.reminder.ReminderDeepLinks
import com.n3sensei.app.data.importing.ContrastContentImporter
import com.n3sensei.app.data.importing.ContrastImportReport
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C6 in the full app (real database, real bundled sources) with the TEST-ONLY pack of the 69
 * unreviewed groups: Học → Bài học → lesson 1 shows "Phân biệt kiến thức dễ nhầm", a grammar,
 * a vocabulary and a kanji page show "Xem các kiến thức dễ nhầm", each opens the Compare screen,
 * and a practice block of a new group runs to the summary. Without a pack nothing is shown.
 */
@RunWith(AndroidJUnit4::class)
class ContrastContentEntryPointsEndToEndTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)
    private val container get() = AppTestState.appContainer
    private val db get() = container.database

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun tagExists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun setUp(withPack: Boolean) = runBlocking {
        AppTestState.reset(seedContent = false, bundledContent = true)
        container.contentImportRepository.installBundledContent()
        if (withPack) {
            val text = instrumentation.context.assets.open("contrast/test-pack-n3-unreviewed.json").use { it.readBytes().toString(Charsets.UTF_8) }
            val report = ContrastContentImporter(db.contrastDao(), container.clock).import(text)
            assertEquals(ContrastContentImporter.summary(report), ContrastImportReport.Outcome.IMPORTED, report.outcome)
        }
    }

    private fun openLesson1() {
        compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        waitFor { exists(string(R.string.lessons_title)) }
        compose.onNode(hasText(string(R.string.lessons_title)) and hasClickAction()).performClick()
        waitFor { compose.onAllNodes(hasText(string(R.string.course_open)) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(string(R.string.course_open)) and hasClickAction())[0].performClick()
        waitFor { exists(string(R.string.course_lesson_title, 1)) && exists(string(R.string.lesson_practice)) }
    }

    private fun openItem(type: ItemType, id: String) =
        ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_VIEW, ReminderDeepLinks.itemUri(ItemRef(type, id)), context, MainActivity::class.java))

    @Test
    fun lessonAndItemPages_openTheirContrastGroups_andAPracticeBlockRuns() {
        setUp(withPack = true)
        val oldSrs = db.countRows("learning_progress") to db.countRows("review_records")
        ActivityScenario.launch(MainActivity::class.java).use {
            openLesson1()
            // Lesson 1 has n3g-g001..g007: 〜ことにしている／〜ことになっている is one of its groups.
            waitFor { tagExists(ContrastLinkTags.link("cg-g-koto-ni-shiteiru-naru")) }
            assertTrue(exists(string(R.string.contrast_links_lesson)))
            compose.onNodeWithTag(ContrastLinkTags.link("cg-g-koto-ni-shiteiru-naru")).performScrollTo().performClick()
            waitFor { tagExists(ContrastTags.PRACTICE_GROUP) }
            // Practice the whole session of this group (every question answered right).
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performScrollTo().performClick()
            repeat(12) {
                waitFor { tagExists(PracticeTags.CHECK) || tagExists(SummaryTags.SCREEN) }
                if (tagExists(SummaryTags.SCREEN)) return@repeat
                val q = runBlocking {
                    val s = db.contrastDao().activeSession()!!
                    val answered = db.contrastDao().attemptsOfSession(s.id).map { it.position }.toSet()
                    container.contrastContentRepository.getQuestion(db.contrastDao().sessionItems(s.id).first { it.position !in answered }.questionId)!!
                }
                val tag = if (q.type == ContrastQuestionType.ERROR_DETECTION) PracticeTags.segment(q.correctOptionId) else PracticeTags.option(q.correctOptionId)
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                compose.onNodeWithTag(PracticeTags.CHECK).performClick()
                waitFor { tagExists(PracticeTags.NEXT) }
                compose.onNodeWithTag(PracticeTags.NEXT).performClick()
            }
            waitFor { tagExists(SummaryTags.SCREEN) }
        }
        assertEquals(0, db.countRows("contrast_attempts WHERE outcome != 'CORRECT'"))
        assertTrue(db.countRows("contrast_attempts") >= 3)
        assertEquals(1, db.countRows("contrast_progress WHERE group_id = 'cg-g-koto-ni-shiteiru-naru'"))
        assertEquals(oldSrs, db.countRows("learning_progress") to db.countRows("review_records"))

        // Item pages: grammar 〜おかげ, vocabulary 冷める (two groups), kanji 皮.
        listOf(
            Triple(ItemType.GRAMMAR, "n3g-g018", listOf("cg-g-okage-sei")),
            Triple(ItemType.VOCABULARY, "mimikara-n3-v211", listOf("cg-v-sameru-cold-awake", "cg-v-hieru-sameru")),
            Triple(ItemType.KANJI, "n3k-k076ae", listOf("cg-k-hi-kare-nami")),
        ).forEach { (type, id, groups) ->
            openItem(type, id).use {
                waitFor { groups.all { tagExists(ContrastLinkTags.link(it)) } }
                assertTrue(exists(string(R.string.contrast_links_item)))
                compose.onNodeWithTag(ContrastLinkTags.link(groups.first())).performScrollTo().performClick()
                waitFor { tagExists(ContrastTags.PRACTICE_GROUP) }
            }
        }
        // Opening pages and Compare screens wrote nothing more.
        assertEquals(1, db.countRows("contrast_sessions"))
    }

    @Test
    fun withoutAReviewedPack_noEntryPointIsShown() {
        setUp(withPack = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            openLesson1()
            compose.waitForIdle()
            assertFalse(tagExists(ContrastLinkTags.SECTION))
        }
        openItem(ItemType.GRAMMAR, "n3g-g001").use {
            waitFor { exists("〜ことにしている") }
            compose.waitForIdle()
            assertFalse(tagExists(ContrastLinkTags.SECTION))
        }
    }
}
