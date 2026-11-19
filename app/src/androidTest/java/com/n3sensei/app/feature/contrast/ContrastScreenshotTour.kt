package com.n3sensei.app.feature.contrast

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * NOT a regular test: a screenshot tour of every Contrast screen on the debug preview pack, for the
 * UI redesign report (before / after). Runs only with the instrumentation argument
 * `screenshots=<prefix>` (e.g. `-Pandroid.testInstrumentationRunnerArguments.screenshots=after`),
 * otherwise it is skipped. PNGs go to the app's external files dir `screenshots/` (pulled with adb).
 * It uses only test data (the app database is reset; some practice history is created through the
 * real use cases so the insight screens have something to show).
 */
@RunWith(AndroidJUnit4::class)
class ContrastScreenshotTour {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefix: String? = InstrumentationRegistry.getArguments().getString("screenshots")
    private fun string(id: Int, vararg args: Any) = instrumentation.targetContext.getString(id, *args)
    private val container get() = AppTestState.appContainer
    private val db get() = container.database
    private val dir by lazy { File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() } }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun tagExists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400) // let enter animations finish
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$prefix-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Some history: two groups answered 4 days ago (one wrong answer each), so errors, review and progress have data. */
    private fun seedHistory() = runBlocking {
        val content = container.contrastContentRepository
        val practice = container.contrastPracticeRepository
        val progress = container.contrastProgressRepository
        val zone = ZoneId.systemDefault()
        var now = Instant.now().minus(Duration.ofDays(4))
        listOf("cg-g-koto-ni-shiteiru-naru", "cg-v-sameru-cold-awake", "cg-k-seki-tsumu-seiseki-sekinin").forEachIndexed { g, groupId ->
            val session = (StartOrResumePractice(content, practice, progress)(PracticeScope.Group(groupId), now, zone) as StartPracticeResult.Started).session
            var position = 0
            while (true) {
                val item = practice.session(session.id)!!.items.firstOrNull { it.position == position } ?: break
                val q = content.getQuestion(item.questionId)!!
                val wrong = position == 0 && g < 2
                val choice = if (wrong) q.options.first { it.id != q.correctOptionId && it.itemId != null }.id else q.correctOptionId
                SubmitContrastAnswer(content, practice, progress)(session.id, position, AnswerInput.Chosen(choice), "tour-$g-$position", now, zone)
                now = now.plusSeconds(30)
                position++
            }
        }
    }

    private fun openContrast() {
        compose.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
        waitFor { exists(string(R.string.learn_contrast_title)) }
        compose.onNode(hasText(string(R.string.learn_contrast_title)) and hasClickAction()).performScrollTo().performClick()
        waitFor { tagExists(ContrastTags.HOME) && tagExists(ContrastTags.EXPLORE) }
    }

    private fun openGroupFromLibrary(search: String, title: String) {
        compose.onNodeWithTag(ContrastTags.SEARCH).performTextInput(search)
        waitFor { compose.onAllNodes(hasText(title) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(title))[0].performClick()
        waitFor { tagExists(ContrastTags.GROUP) && tagExists(ContrastTags.PRACTICE_GROUP) }
    }

    private fun scrollGroupTo(tag: String) = reveal(tag)

    /** Scrolls [tag] into view whatever the container (plain scroll column or lazy list). */
    private fun reveal(tag: String) {
        waitFor { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        if (runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }.isSuccess) return
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun tour() {
        assumeTrue("screenshot tour runs only with -e screenshots <prefix>", prefix != null)
        runBlocking {
            AppTestState.reset(seedContent = false, bundledContent = true, contrastPreview = true)
            container.contentImportRepository.installBundledContent()
        }
        seedHistory()
        val seen = mutableSetOf<ContrastQuestionType>()
        ActivityScenario.launch(MainActivity::class.java).use {
            openContrast()
            shot("01-home")
            reveal(ContrastTags.EXPLORE)
            shot("01b-home-bottom")
            compose.onNodeWithTag(ContrastTags.EXPLORE).performClick()
            waitFor { tagExists(ContrastTags.SEARCH) }
            shot("02-library")
            openGroupFromLibrary("ことにしている", "〜ことにしている / 〜ことになっている")
            shot("03-compare-grammar")
            scrollGroupTo(ContrastTags.itemCard("ci-g-koto-ni-shiteiru-naru-natteiru"))
            shot("03b-compare-grammar-items")
            scrollGroupTo(ContrastTags.example("cx-g-koto-ni-shiteiru-naru-1"))
            shot("03c-compare-grammar-examples")
            Espresso.pressBack()
            waitFor { tagExists(ContrastTags.SEARCH) }
            Espresso.pressBack()
            waitFor { tagExists(ContrastTags.HOME) }
            reveal(ContrastTags.EXPLORE)
            compose.onNodeWithTag(ContrastTags.EXPLORE).performClick()
            waitFor { tagExists(ContrastTags.SEARCH) }
            openGroupFromLibrary("乾く", "乾く / 渇く")
            shot("04-compare-vocabulary")
            scrollGroupTo(ContrastTags.itemCard("ci-v-kawaku-dry-thirsty-dry"))
            shot("04b-compare-vocabulary-items")
            Espresso.pressBack()
            waitFor { tagExists(ContrastTags.SEARCH) }
            Espresso.pressBack()
            waitFor { tagExists(ContrastTags.HOME) }
            reveal(ContrastTags.EXPLORE)
            compose.onNodeWithTag(ContrastTags.EXPLORE).performClick()
            waitFor { tagExists(ContrastTags.SEARCH) }
            openGroupFromLibrary("鳥", "鳥 / 島")
            shot("05-compare-kanji")
            scrollGroupTo(ContrastTags.itemCard("ci-k-tori-shima-shima"))
            shot("05b-compare-kanji-items")
            // Practice this group, then more groups until the four question types were captured.
            reveal(ContrastTags.PRACTICE_GROUP)
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performClick()
            var answered = 0
            repeat(14) {
                waitFor { tagExists(PracticeTags.CHECK) || tagExists(SummaryTags.SCREEN) }
                if (tagExists(SummaryTags.SCREEN)) return@repeat
                val q = runBlocking {
                    val s = db.contrastDao().activeSession()!!
                    val done = db.contrastDao().attemptsOfSession(s.id).map { it.position }.toSet()
                    container.contrastContentRepository.getQuestion(db.contrastDao().sessionItems(s.id).first { it.position !in done }.questionId)!!
                }
                val first = seen.add(q.type)
                if (first) shot("06-practice-${q.type.name.lowercase()}")
                val choice = if (answered == 1) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId
                val tag = if (q.type == ContrastQuestionType.ERROR_DETECTION) PracticeTags.segment(choice) else PracticeTags.option(choice)
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                if (first) shot("06-practice-${q.type.name.lowercase()}-selected")
                compose.onNodeWithTag(PracticeTags.CHECK).performClick()
                waitFor { tagExists(PracticeTags.NEXT) }
                if (answered <= 1) shot("07-feedback-${if (answered == 1) "incorrect" else "correct"}")
                compose.onNodeWithTag(PracticeTags.NEXT).performClick()
                answered++
            }
            waitFor { tagExists(SummaryTags.SCREEN) }
            shot("08-summary")
            compose.onNodeWithTag(SummaryTags.HOME).performScrollTo().performClick()
            waitFor { tagExists(ContrastTags.HOME) }
            reveal(InsightTags.OPEN_ERRORS)
            compose.onNodeWithTag(InsightTags.OPEN_ERRORS).performClick()
            waitFor { tagExists(InsightTags.LIST) }
            shot("09-errors")
            Espresso.pressBack()
            waitFor { tagExists(ContrastTags.HOME) }
            reveal(InsightTags.OPEN_REVIEW)
            compose.onNodeWithTag(InsightTags.OPEN_REVIEW).performClick()
            waitFor { tagExists(InsightTags.LIST) }
            shot("10-review")
            Espresso.pressBack()
            waitFor { tagExists(ContrastTags.HOME) }
            reveal(InsightTags.OPEN_PROGRESS)
            compose.onNodeWithTag(InsightTags.OPEN_PROGRESS).performClick()
            waitFor { tagExists(InsightTags.LIST) }
            shot("11-progress")
        }
        // Dark mode: home, a compare screen, practice.
        runBlocking { container.userPreferencesRepository.setThemeMode(ThemeMode.DARK) }
        ActivityScenario.launch(MainActivity::class.java).use {
            openContrast()
            shot("12-dark-home")
            reveal(ContrastTags.EXPLORE)
            compose.onNodeWithTag(ContrastTags.EXPLORE).performClick()
            waitFor { tagExists(ContrastTags.SEARCH) }
            shot("13-dark-library")
            openGroupFromLibrary("ことにしている", "〜ことにしている / 〜ことになっている")
            shot("14-dark-compare")
            reveal(ContrastTags.PRACTICE_GROUP)
            compose.onNodeWithTag(ContrastTags.PRACTICE_GROUP).performClick()
            waitFor { tagExists(PracticeTags.CHECK) }
            shot("15-dark-practice")
        }
        runBlocking { container.userPreferencesRepository.setThemeMode(ThemeMode.SYSTEM) }
    }
}
