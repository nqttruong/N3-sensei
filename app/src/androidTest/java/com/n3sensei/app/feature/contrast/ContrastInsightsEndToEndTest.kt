package com.n3sensei.app.feature.contrast

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.data.importing.ContrastContentImporter
import com.n3sensei.app.data.importing.ContrastImportReport
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * M7 in the full app on the real Room database (real bundled sources + the TEST-ONLY copy of the
 * sample pack). A first block is answered 4 days ago through the real use cases (the app clock is
 * the device clock), so the group is due now; then: Errors → the direction, Review → "Ôn tập ngay"
 * on the M6 practice → summary → new schedule written once → Progress shows the updated counts.
 */
@RunWith(AndroidJUnit4::class)
class ContrastInsightsEndToEndTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun string(id: Int, vararg args: Any) = instrumentation.targetContext.getString(id, *args)
    private val container get() = AppTestState.appContainer
    private val db get() = container.database
    private val grammar = "cg-g-koto-ni-shiteiru-naru"

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun tagExists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun setUp() = runBlocking {
        AppTestState.reset(seedContent = false, bundledContent = true)
        container.contentImportRepository.installBundledContent()
        val text = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals(ContrastImportReport.Outcome.IMPORTED, ContrastContentImporter(db.contrastDao(), container.clock).import(text).outcome)
        // A block answered 4 days ago (first question wrong: 〜ことになっている expected, 〜ことにしている chosen).
        val zone = ZoneId.systemDefault()
        var now = Instant.now().minus(Duration.ofDays(4))
        val content = container.contrastContentRepository
        val practice = container.contrastPracticeRepository
        val session = (StartOrResumePractice(content, practice, container.contrastProgressRepository)(PracticeScope.Group(grammar), now, zone) as StartPracticeResult.Started).session
        var position = 0
        while (true) {
            val item = practice.session(session.id)!!.items.firstOrNull { it.position == position } ?: break
            val q = content.getQuestion(item.questionId)!!
            val choice = if (position == 0) q.options.first { it.id != q.correctOptionId && it.itemId != null }.id else q.correctOptionId
            SubmitContrastAnswer(content, practice, container.contrastProgressRepository)(session.id, position, AnswerInput.Chosen(choice), "past-$position", now, zone)
            now = now.plusSeconds(20)
            position++
        }
    }

    private fun openHome() {
        compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        waitFor { exists(string(R.string.learn_contrast_title)) }
        compose.onNodeWithText(string(R.string.learn_contrast_title)).performScrollTo().performClick()
        waitFor { tagExists(InsightTags.OPEN_ERRORS) }
    }

    @Test
    fun errors_review_progress_onRealData() {
        setUp()
        val attemptsBefore = db.countRows("contrast_attempts")
        val oldSrs = db.countRows("learning_progress") to db.countRows("review_records")
        assertEquals(1, db.countRows("contrast_reviews"))
        ActivityScenario.launch(MainActivity::class.java).use {
            openHome()
            // Errors: the stored direction, with its group.
            compose.onNodeWithTag(InsightTags.OPEN_ERRORS).performScrollTo().performClick()
            waitFor { exists(string(R.string.contrast_errors_direction, "〜ことになっている", "〜ことにしている")) }
            assertTrue(exists(string(R.string.contrast_errors_total, 1)))
            Espresso.pressBack()
            waitFor { tagExists(InsightTags.OPEN_REVIEW) }
            // Review: the group is due; opening the screen created nothing.
            compose.onNodeWithTag(InsightTags.OPEN_REVIEW).performScrollTo().performClick()
            waitFor { exists(string(R.string.contrast_review_due, 1)) }
            assertEquals(1, db.countRows("contrast_sessions"))
            compose.onNodeWithTag(InsightTags.START_REVIEW).performClick()
            // The M6 practice, due groups only: answer every question right.
            repeat(10) {
                if (tagExists(SummaryTags.SCREEN)) return@repeat
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
            compose.onNodeWithTag(SummaryTags.HOME).performScrollTo().performClick()
            waitFor { tagExists(InsightTags.OPEN_PROGRESS) }
            // Progress: the counts include both sessions.
            compose.onNodeWithTag(InsightTags.OPEN_PROGRESS).performScrollTo().performClick()
            val answered = db.countRows("contrast_attempts")
            val incorrect = db.countRows("contrast_attempts WHERE outcome = 'INCORRECT'")
            val expected = string(R.string.contrast_answers_counts, answered, answered - incorrect, incorrect, 0)
            waitFor { tagExists(InsightTags.LIST) }
            compose.onNodeWithTag(InsightTags.LIST).performScrollToNode(hasText(expected))
        }
        // One review session: 1 new review row, progress scheduled again once, in the future.
        assertEquals(1, db.countRows("contrast_sessions WHERE scope_kind = 'REVIEW' AND status = 'COMPLETED'"))
        assertEquals(2, db.countRows("contrast_reviews"))
        assertEquals(1, db.countRows("contrast_progress WHERE review_count = 2 AND due_at > ${System.currentTimeMillis()}"))
        assertTrue(db.countRows("contrast_attempts") > attemptsBefore)
        // Only grammar was reviewed: no new group in the review session.
        assertEquals(0, db.countRows("contrast_attempts a JOIN contrast_sessions s ON s.id = a.session_id WHERE s.scope_kind = 'REVIEW' AND a.group_id != '$grammar'"))
        assertEquals(oldSrs, db.countRows("learning_progress") to db.countRows("review_records"))
    }
}
