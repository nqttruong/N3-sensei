package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.core.ui.content.ItemBrowserTags
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.feature.review.ReviewTags
import com.n3sensei.app.feature.settings.KanjiReviewLessonsTags
import com.n3sensei.app.feature.settings.ReviewSettingsTags
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kanji N2 in the full app with the bundled content (real Room database and DataStore): learned
 * in Học → Kanji next to N3 without mixing, and taken into the existing review only from the
 * lessons chosen in Cài đặt ôn tập.
 */
@RunWith(AndroidJUnit4::class)
class KanjiN2EndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun launch(): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = false, bundledContent = true)
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(timeout: Long = 15_000, condition: () -> Boolean) = composeRule.waitUntil(timeout, condition)
    private fun exists(text: String, substring: Boolean = false) =
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    private fun waitForText(text: String, substring: Boolean = false) = waitFor { exists(text, substring) }
    private fun awaitClickable(text: String): SemanticsNodeInteraction {
        waitFor { composeRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        return composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst()
    }
    private fun openTab(tab: TopLevelDestination) = composeRule.onNodeWithTag(tab.testTag).performClick()
    private fun kanjiChoice() = runBlocking { AppTestState.preferences.first() }.kanjiReviewLessons

    private fun openKanjiList() {
        openTab(TopLevelDestination.LEARN)
        awaitClickable(string(R.string.kanji_title)).performClick()
        // Bundled content installs on first launch: wait for both levels.
        waitFor { composeRule.onAllNodes(hasTestTag(ItemBrowserTags.level("N2"))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun learnKanji_n3First_n2OnItsOwnTab_neverMixed_andAnN2LessonCanBeStudied() {
        launch().use {
            openKanjiList()
            composeRule.onNodeWithTag(ItemBrowserTags.level("N3")).assertIsSelected()
            waitForText("意")
            assertTrue("no N2 kanji in the N3 list", !exists("井"))

            composeRule.onNodeWithTag(ItemBrowserTags.level("N2")).performClick()
            waitForText("井")
            composeRule.onNodeWithTag(ItemBrowserTags.level("N2")).assertIsSelected()
            assertTrue("no N3 kanji in the N2 list", !exists("意"))
            // The lesson chips are the N2 lessons only (one pack: short labels).
            composeRule.onNodeWithText(string(R.string.lesson_number, 1, "N2")).assertIsDisplayed()
            assertTrue("no N3 lesson chip on the N2 tab", !exists(string(R.string.lesson_number, 1, "N3")))

            // Same detail screen as N3, with the deck's data and words, and where it is studied.
            awaitClickable("井").performClick()
            waitForText("TỈNH")
            // Its meaning (also the meaning of its word 井戸).
            composeRule.onAllNodes(hasText("Cái giếng")).onFirst().assertIsDisplayed()
            // Where it is studied: its N2 lesson (tag at the top and in "Vị trí học").
            composeRule.onAllNodes(hasText(string(R.string.location_kanji_level, "N2", 1))).onFirst().assertIsDisplayed()
            composeRule.onNodeWithText("井戸").performScrollTo().assertIsDisplayed()

            // Flashcards of its lesson, as for N3: 23 cards, answer shown on reveal.
            composeRule.onNodeWithText(string(R.string.practice_this_lesson)).performScrollTo().performClick()
            waitForText(string(R.string.practice_progress, 1, 23))
            composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
            waitForText("TỈNH")
            awaitClickable(string(R.string.rating_good)).performClick()
            waitForText(string(R.string.practice_progress, 2, 23))
        }
    }

    @Test
    fun reviewSettings_chooseN2Lessons_savedAcrossRestarts_andReviewTakesThemOnly() {
        launch().use {
            // Default: no N2 lesson, so the review offers the 420 N3 kanji only.
            openTab(TopLevelDestination.REVIEW)
            waitFor { exists(string(R.string.review_type_new_today, 0, 10, 420)) }

            openTab(TopLevelDestination.SETTINGS)
            awaitClickable(string(R.string.review_settings_title)).performClick()
            composeRule.onNodeWithTag(ReviewSettingsTags.KANJI_LESSONS).performScrollTo().performClick()
            waitForText(string(R.string.kanji_review_level_title, "N3"))
            composeRule.onNodeWithText(string(R.string.kanji_review_level_all, 17)).assertIsDisplayed()
            // N2 comes after the 17 N3 lessons.
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(KanjiReviewLessonsTags.clearAll("N2")))
            composeRule.onNodeWithText(string(R.string.kanji_review_level_none, 18)).assertIsDisplayed()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(KanjiReviewLessonsTags.lesson("n2k-l01")))
            composeRule.onNodeWithTag(KanjiReviewLessonsTags.lesson("n2k-l01")).performClick()
            waitForText(string(R.string.kanji_review_level_some, 1, 18))
            assertEquals(LessonSelection.Only(setOf("n2k-l01")), kanjiChoice().of("N2"))
            assertEquals(LessonSelection.All, kanjiChoice().of("N3"))
        }
        // A new start of the app: the choice is read back from DataStore.
        ActivityScenario.launch(MainActivity::class.java).use {
            openTab(TopLevelDestination.REVIEW)
            // 420 N3 + the 23 kanji of N2 lesson 1; same daily kanji cap (10).
            waitFor { exists(string(R.string.review_type_new_today, 0, 10, 443)) }
            composeRule.onNodeWithTag(ReviewTags.typeCard(ItemType.KANJI)).assertIsDisplayed()

            openTab(TopLevelDestination.SETTINGS)
            awaitClickable(string(R.string.review_settings_title)).performClick()
            composeRule.onNodeWithTag(ReviewSettingsTags.KANJI_LESSONS).performScrollTo().performClick()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(KanjiReviewLessonsTags.selectAll("N2")))
            composeRule.onNodeWithTag(KanjiReviewLessonsTags.selectAll("N2")).performClick()
            waitFor { kanjiChoice().of("N2") == LessonSelection.All }
            composeRule.onNodeWithTag(KanjiReviewLessonsTags.clearAll("N2")).performClick()
            waitFor { kanjiChoice().of("N2") == LessonSelection.Only(emptySet()) }
            assertEquals(LessonSelection.All, kanjiChoice().of("N3"))
            Espresso.pressBack()
            Espresso.pressBack()
            openTab(TopLevelDestination.REVIEW)
            waitFor { exists(string(R.string.review_type_new_today, 0, 10, 420)) }
        }
    }
}
