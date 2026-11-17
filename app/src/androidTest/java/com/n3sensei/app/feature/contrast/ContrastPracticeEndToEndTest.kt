package com.n3sensei.app.feature.contrast

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.data.importing.ContrastContentImporter
import com.n3sensei.app.data.importing.ContrastImportReport
import com.n3sensei.app.data.repository.OfflineContrastContentRepository
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A whole Contrast practice in the full app, on the real Room database (real bundled sources +
 * the TEST-ONLY copy of the sample pack): from the compare screen, the four kinds of answers,
 * feedback, the summary, the schedule written once; and an unfinished session resumed from Home.
 */
@RunWith(AndroidJUnit4::class)
class ContrastPracticeEndToEndTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)
    private val db get() = AppTestState.appContainer.database
    private val grammarId = "cg-g-koto-ni-shiteiru-naru"

    private fun installContent() = runBlocking {
        AppTestState.reset(seedContent = false, bundledContent = true)
        AppTestState.appContainer.contentImportRepository.installBundledContent()
        val text = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val report = ContrastContentImporter(db.contrastDao(), AppTestState.appContainer.clock).import(text)
        assertEquals(ContrastContentImporter.summary(report), ContrastImportReport.Outcome.IMPORTED, report.outcome)
    }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun tagExists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun openGroup(groupLabels: String) {
        compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        waitFor { exists(string(R.string.learn_contrast_title)) }
        compose.onNodeWithText(string(R.string.learn_contrast_title)).performScrollTo().performClick()
        waitFor { tagExists(ContrastTags.EXPLORE) }
        compose.onNodeWithTag(ContrastTags.EXPLORE).performScrollTo().performClick()
        waitFor { exists(groupLabels) }
        compose.onNodeWithText(groupLabels).performClick()
        waitFor { tagExists(ContrastTags.PRACTICE_GROUP) }
    }

    /** The question on screen, from the database (what the screen shows). */
    private fun currentQuestion(): com.n3sensei.app.domain.contrast.ContrastQuestion = runBlocking {
        val session = db.contrastDao().activeSession()!!
        val answered = db.contrastDao().attemptsOfSession(session.id).map { it.position }.toSet()
        val next = db.contrastDao().sessionItems(session.id).first { it.position !in answered }
        OfflineContrastContentRepository(db.contrastDao()).getQuestion(next.questionId)!!
    }

    private fun answer(optionId: String) {
        val q = currentQuestion()
        val tag = if (q.type == com.n3sensei.app.domain.contrast.ContrastQuestionType.ERROR_DETECTION) PracticeTags.segment(optionId) else PracticeTags.option(optionId)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag(PracticeTags.CHECK).performClick()
        waitFor { tagExists(PracticeTags.FEEDBACK) }
    }

    private fun next() {
        compose.onNodeWithTag(PracticeTags.NEXT).performClick()
    }

    @Test
    fun aWholeSession_fromCompareToSummary_schedulesOnce() {
        installContent()
        val oldSrs = db.countRows("learning_progress") to db.countRows("review_records")
        ActivityScenario.launch(MainActivity::class.java).use {
            openGroup("〜ことにしている / 〜ことになっている")
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performScrollTo().performClick()
            waitFor { tagExists(PracticeTags.CHECK) }
            // Q1 wrong: feedback says so, a re-ask is added at the end.
            val first = currentQuestion()
            answer(first.options.first { it.id != first.correctOptionId }.id)
            waitFor { exists(string(R.string.contrast_feedback_incorrect)) }
            assertTrue(exists(string(R.string.contrast_feedback_retry_added)))
            next()
            // Q2 right, Q3 right (completes the block: scheduled once).
            repeat(2) {
                waitFor { tagExists(PracticeTags.CHECK) }
                answer(currentQuestion().correctOptionId)
                waitFor { exists(string(R.string.contrast_feedback_correct)) }
                next()
            }
            // The re-ask: skipped (not wrong).
            waitFor { tagExists(PracticeTags.SKIP) }
            compose.onNodeWithTag(PracticeTags.SKIP).performClick()
            waitFor { exists(string(R.string.contrast_feedback_skipped)) }
            assertTrue(exists(string(R.string.contrast_practice_finish)))
            next()
            // Summary.
            waitFor { tagExists(SummaryTags.SCREEN) }
            assertTrue(exists(string(R.string.contrast_summary_handled, 4)))
            assertTrue(exists(string(R.string.contrast_summary_accuracy, 67, 2, 3)))
        }
        assertEquals(4, db.countRows("contrast_attempts"))
        assertEquals(1, db.countRows("contrast_reviews"))
        assertEquals(1, db.countRows("contrast_progress"))
        assertEquals(1, db.countRows("contrast_sessions WHERE status = 'COMPLETED'"))
        // The existing SRS was not touched.
        assertEquals(oldSrs, db.countRows("learning_progress") to db.countRows("review_records"))
        assertEquals(0, db.countRows("learning_progress WHERE item_type = '${ItemType.GRAMMAR}'"))
    }

    @Test
    fun anUnfinishedSession_survivesLeavingAndARestart_andResumesFromHome() {
        installContent()
        ActivityScenario.launch(MainActivity::class.java).use {
            openGroup("積 / 績")
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performScrollTo().performClick()
            waitFor { tagExists(PracticeTags.CHECK) }
            // Choose but do not submit, then leave: nothing is stored.
            compose.onNodeWithTag(PracticeTags.option("b")).performClick()
            compose.onNodeWithTag(PracticeTags.EXIT).performClick()
            waitFor { tagExists(ContrastTags.PRACTICE_GROUP) }
        }
        assertEquals(0, db.countRows("contrast_attempts"))
        assertEquals(1, db.countRows("contrast_sessions WHERE status = 'ACTIVE'"))
        // A new start of the app: Home offers to continue the session.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            waitFor { exists(string(R.string.learn_contrast_title)) }
            compose.onNodeWithText(string(R.string.learn_contrast_title)).performScrollTo().performClick()
            waitFor { tagExists(ContrastTags.RESUME) }
            assertTrue(exists(string(R.string.contrast_continue_session, 1)))
            compose.onNodeWithTag(ContrastTags.RESUME).performClick()
            waitFor { tagExists(PracticeTags.CHECK) }
            answer("b")
            waitFor { exists(string(R.string.contrast_feedback_correct)) }
            next()
            waitFor { tagExists(SummaryTags.SCREEN) }
            compose.onNodeWithTag(SummaryTags.HOME).performScrollTo().performClick()
            waitFor { tagExists(ContrastTags.HOME) && tagExists(ContrastTags.START) }
            assertTrue(!tagExists(ContrastTags.RESUME))
        }
        assertEquals(1, db.countRows("contrast_attempts"))
        assertEquals(1, db.countRows("contrast_sessions"))
        assertEquals(1, db.countRows("contrast_reviews"))
    }
    /** M8: a configuration change (rotation, dark mode, font) during practice keeps the session and writes nothing twice. */
    @Test
    fun aConfigurationChange_duringPractice_keepsTheQuestionAndStoresEachAnswerOnce() {
        installContent()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openGroup("〜ことにしている / 〜ことになっている")
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performScrollTo().performClick()
            waitFor { tagExists(PracticeTags.CHECK) }
            // On the feedback of Q1: recreate the Activity.
            answer(currentQuestion().correctOptionId)
            waitFor { exists(string(R.string.contrast_feedback_correct)) }
            scenario.recreate()
            waitFor { tagExists(PracticeTags.NEXT) || tagExists(PracticeTags.CHECK) }
            assertEquals(1, db.countRows("contrast_attempts"))
            if (tagExists(PracticeTags.NEXT)) next()
            // On Q2, before submitting: recreate again, then answer.
            waitFor { tagExists(PracticeTags.CHECK) }
            scenario.recreate()
            waitFor { tagExists(PracticeTags.CHECK) }
            assertEquals(1, db.countRows("contrast_attempts"))
            answer(currentQuestion().correctOptionId)
            waitFor { exists(string(R.string.contrast_feedback_correct)) }
        }
        assertEquals(2, db.countRows("contrast_attempts"))
        assertEquals(2, db.countRows("contrast_attempts WHERE position IN (0, 1)"))
        assertEquals(1, db.countRows("contrast_sessions WHERE status = 'ACTIVE'"))
        assertEquals(0, db.countRows("contrast_reviews"))
    }
}
