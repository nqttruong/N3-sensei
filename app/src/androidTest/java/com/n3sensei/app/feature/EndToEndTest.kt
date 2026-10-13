package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import com.n3sensei.app.testing.countRows
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full app against the real Room database and DataStore, with SEED TEST DATA loaded only for
 * the duration of the test.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun launch(
        seedContent: Boolean = true,
        onboardingCompleted: Boolean = true,
        bundledContent: Boolean = false,
    ): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent, onboardingCompleted, bundledContent)
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForText(text: String, substring: Boolean = false) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun clickable(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasClickAction())

    private fun openTab(tab: TopLevelDestination) = composeRule.onNodeWithTag(tab.testTag).performClick()

    private fun openLesson1() {
        openTab(TopLevelDestination.LEARN)
        clickable(string(R.string.lessons_title)).performClick()
        waitForText("Bài seed 1")
        composeRule.onNodeWithText("Bài seed 1").performClick()
        waitForText(string(R.string.lesson_counts, 4, 2, 1))
    }

    @Test
    fun emptyDatabase_screensSayNoContentIsLoaded() {
        launch(seedContent = false).use {
            waitForText(string(R.string.common_no_content))
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            waitForText(string(R.string.common_no_content))
            Espresso.pressBack()
            clickable(string(R.string.vocabulary_title)).performClick()
            waitForText(string(R.string.vocabulary_empty))
        }
    }

    @Test
    fun dashboard_seedPackIsTestData_andNotCounted() {
        launch().use {
            // The seed pack is marked is_seed = 1, so statistics treat the app as having no content.
            waitForText(string(R.string.dashboard_goal))
            composeRule.onNodeWithText(string(R.string.common_no_content)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.dashboard_priority_empty)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun lesson_toVocabularyKanjiAndGrammarDetails() {
        launch().use {
            openLesson1()

            composeRule.onNodeWithText("経験").performClick()
            waitForText("kinh nghiệm")
            composeRule.onNodeWithText("Tôi có kinh nghiệm làm việc ở Nhật.").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.source_license_unverified, ""), substring = true).performScrollTo().assertIsDisplayed()
            Espresso.pressBack()

            clickable(string(R.string.lesson_tab_kanji, 2)).performClick()
            composeRule.onNodeWithText("経").performClick()
            waitForText("KINH")
            composeRule.onNodeWithText("ケイ、キョウ").assertIsDisplayed()
            Espresso.pressBack()

            clickable(string(R.string.lesson_tab_grammar, 1)).performClick()
            composeRule.onNodeWithText("〜たばかり").performClick()
            waitForText(string(R.string.grammar_caution))
            composeRule.onNodeWithText("日本に来たばかりです。").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun emptyLesson_cannotBePracticed() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            waitForText("Bài seed 2 (rỗng)")
            composeRule.onNodeWithText("Bài seed 2 (rỗng)").performClick()
            waitForText(string(R.string.lesson_empty_vocabulary))
            composeRule.onNodeWithText(string(R.string.lesson_practice)).assertIsNotEnabled()
        }
    }

    @Test
    fun vocabularySearch_findsByReading_reportsNoResult_andFiltersByLesson() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            waitForText("経験")

            val field = composeRule.onNode(hasSetTextAction())
            field.performTextInput("ふえる")
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextExact("経験").isEmpty() }
            composeRule.onNodeWithText("増える").assertIsDisplayed()

            field.performTextReplacement("zzz")
            waitForText(string(R.string.common_no_results, "zzz"))

            field.performTextReplacement("")
            waitForText("経験")
            composeRule.onNodeWithText(string(R.string.lesson_number, 2, "N3")).performClick()
            waitForText(string(R.string.lesson_empty_vocabulary))
        }
    }

    @Test
    fun practiceSession_ratesEveryCard_thenShowsResult_andReturnsToLesson() {
        launch().use {
            openLesson1()
            composeRule.onNodeWithText(string(R.string.lesson_practice)).performClick()
            waitForText(string(R.string.practice_not_saved))

            repeat(7) { index ->
                waitForText(string(R.string.practice_progress, index + 1, 7))
                composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
                clickable(string(if (index == 0) R.string.rating_again else R.string.rating_good)).performClick()
            }

            waitForText(string(R.string.practice_result_title))
            composeRule.onNodeWithText(string(R.string.practice_result_not_saved)).assertIsDisplayed()
            composeRule.onNodeWithText("7 / 7", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.practice_result_back)).performClick()
            waitForText(string(R.string.lesson_counts, 4, 2, 1))

            // Practice results are not saved to the review schedule.
            openTab(TopLevelDestination.REVIEW)
            waitForText(string(R.string.review_overview_today, 0, 200, 0, 20))
        }
    }

    @Test
    fun onboarding_canBeSkipped_isRemembered_andCanBeReplayed() {
        launch(onboardingCompleted = false).use {
            waitForText(string(R.string.onboarding_1_title))
            composeRule.onNodeWithText(string(R.string.onboarding_skip)).performClick()
            waitForText(string(R.string.dashboard_goal))
            assertTrue(runBlocking { AppTestState.preferences.first().onboardingCompleted })
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(string(R.string.dashboard_goal))
            openTab(TopLevelDestination.SETTINGS)
            clickable(string(R.string.settings_replay_onboarding)).performScrollTo().performClick()
            waitForText(string(R.string.onboarding_1_title))
        }
    }

    @Test
    fun settings_themeAndFuriganaArePersisted() {
        launch().use {
            openTab(TopLevelDestination.SETTINGS)
            clickable(string(R.string.settings_theme_dark)).performClick()
            clickable(string(R.string.settings_show_furigana)).performClick()
            composeRule.waitUntil(5_000) {
                runBlocking { AppTestState.preferences.first() }.let { it.themeMode == ThemeMode.DARK && !it.showFurigana }
            }
            val prefs = runBlocking { AppTestState.preferences.first() }
            assertEquals(ThemeMode.DARK, prefs.themeMode)
            assertFalse(prefs.showFurigana)
            composeRule.onNodeWithText(SEED_SOURCE_NAME, substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun bundledIllustrationPack_isInstalledOnFirstLaunch_andReadFromRoom() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            // Both bundled packs are installed; the Mimikara lessons come first (pack id order).
            waitForText("Mimikara N3 · 名詞")
            composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(ILLUSTRATION_LESSON))
            composeRule.onNodeWithText(ILLUSTRATION_LESSON).performClick()
            waitForText(string(R.string.lesson_counts, 30, 27, 7))
        }
    }

    @Test
    fun bundledMimikaraPack_isInstalledOnFirstLaunch_withAll880Words() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            waitForText("Mimikara N3 · 名詞")
            composeRule.onNodeWithText("Mimikara N3 · 名詞").performClick()
            waitForText(string(R.string.lesson_counts, 120, 0, 0))
            waitForText("男性")
        }
        val vocabulary = AppTestState.appContainer.database.countRows("vocabulary")
        assertEquals(30 + 880, vocabulary)
    }

    @Test
    fun vocabularySearch_ignoresVietnameseDiacritics() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            waitForText("経験")
            composeRule.onNode(hasSetTextAction()).performTextInput("gui den")
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextExact("経験").isEmpty() }
            composeRule.onNodeWithText("届ける").assertIsDisplayed()
        }
    }

    @Test
    fun markItem_filterMarked_thenPracticeMarkedItems() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            waitForText("増える")
            composeRule.onNodeWithText("増える").performClick()
            clickable(string(R.string.mark_add)).performClick()
            waitForText(string(R.string.mark_remove))
            Espresso.pressBack()

            clickable(string(R.string.filter_marked)).performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextExact("経験").isEmpty() }
            composeRule.onNodeWithText("増える").assertIsDisplayed()

            composeRule.onNodeWithText(string(R.string.practice_marked)).performClick()
            waitForText(string(R.string.practice_progress, 1, 1))
            composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
            clickable(string(R.string.rating_good)).performClick()
            waitForText(string(R.string.practice_result_title))
            composeRule.onNodeWithText("1 / 1", substring = true).assertIsDisplayed()

            openTab(TopLevelDestination.REVIEW)
            waitForText(string(R.string.practice_marked_count, 1))
        }
    }

    @Test
    fun grammarExercise_showsResultAndExplanation() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.grammar_title)).performClick()
            waitForText("〜たばかり")
            composeRule.onNodeWithText("〜たばかり").performClick()
            waitForText(string(R.string.grammar_exercises))
            composeRule.onNodeWithText("来た").performScrollTo().performClick()
            composeRule.onNodeWithText(string(R.string.grammar_exercise_correct)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.grammar_exercise_retry)).performScrollTo().performClick()
            composeRule.onNodeWithText("来る").performScrollTo().performClick()
            waitForText(string(R.string.grammar_exercise_incorrect, "来た"))
        }
    }

    @Test
    fun kanjiDetail_saysStrokeOrderIsNotAvailable() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.kanji_title)).performClick()
            waitForText("増")
            composeRule.onNodeWithText("増").performClick()
            composeRule.onNodeWithText(string(R.string.kanji_stroke_order_unavailable)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun reviewSession_savesEveryAnswer_andProgressSurvivesRestart() {
        launch().use {
            openTab(TopLevelDestination.REVIEW)
            // 7 seed items are new; the default limit allows 20 new items a day.
            waitForText(string(R.string.review_start, 7))
            clickable(string(R.string.review_start, 7)).performClick()
            waitForText(string(R.string.review_session_saved_note))
            repeat(7) { index ->
                waitForText(string(R.string.practice_progress, index + 1, 7))
                composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
                // Button label includes the next interval, e.g. "Tốt\n1 giờ".
                composeRule.onNode(hasText(string(R.string.rating_good), substring = true) and hasClickAction()).performClick()
            }
            waitForText(string(R.string.review_result_saved))
            composeRule.onNodeWithText(string(R.string.review_result_back)).performClick()
            waitForText(string(R.string.review_overview_today, 0, 200, 7, 20))
            composeRule.onNodeWithText(string(R.string.review_start, 0)).assertIsNotEnabled()
        }
        // Relaunch: progress is read back from Room.
        ActivityScenario.launch(MainActivity::class.java).use {
            openTab(TopLevelDestination.REVIEW)
            waitForText(string(R.string.review_overview_today, 0, 200, 7, 20))
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            waitForText("経験")
            composeRule.onNodeWithText("経験").performClick()
            waitForText(string(R.string.item_memory_level, 2, 7))
            composeRule.onNodeWithText(string(R.string.due_state_not_due), substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun reviewSettings_validatesAndSaves() {
        launch().use {
            openTab(TopLevelDestination.SETTINGS)
            clickable(string(R.string.review_settings_title)).performClick()
            waitForText(string(R.string.review_settings_intervals))
            val firstAmount = composeRule.onAllNodes(hasSetTextAction())[0]
            firstAmount.performTextReplacement("")
            composeRule.onNodeWithText(string(R.string.review_settings_save)).performScrollTo().performClick()
            waitForText(string(R.string.review_settings_error_amount, 1))

            firstAmount.performTextReplacement("10")
            composeRule.onNodeWithText(string(R.string.review_settings_save)).performScrollTo().performClick()
            waitForText(string(R.string.review_settings_saved))
            assertEquals("10m,1h,1d,2d,1w,2w,1mo", runBlocking { AppTestState.preferences.first() }.reviewSettings.ladder.encode())
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextExact(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes()

    private companion object {
        const val SEED_SOURCE_NAME = "dữ liệu seed cho kiểm thử"
        const val ILLUSTRATION_LESSON = "Bài minh họa 1: Công việc và đời sống"
    }
}
