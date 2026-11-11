package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
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
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.feature.review.ReviewTags
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review by content type on the real app (SEED TEST DATA: 4 words, 2 kanji, 1 grammar point):
 * one type at a time, each on its own schedule, and what was done survives closing the app.
 */
@RunWith(AndroidJUnit4::class)
class ReviewByTypeUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
    private val db get() = AppTestState.appContainer.database

    @After
    fun tearDown() = AppTestState.reset(seedContent = false)

    private fun waitForText(text: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openReview() {
        composeRule.onNodeWithTag(TopLevelDestination.REVIEW.testTag).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ReviewTags.typeCard(ItemType.KANJI))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Reveals and rates every card of the session "Tốt", then waits for the result screen. */
    private fun answerAll(cards: Int, onEachCard: () -> Unit = {}) {
        repeat(cards) {
            waitForText(string(R.string.practice_reveal))
            composeRule.onNode(hasText(string(R.string.practice_reveal)) and hasClickAction()).performClick()
            onEachCard()
            composeRule.onNode(hasText(string(R.string.rating_good), substring = true) and hasClickAction()).performClick()
        }
        waitForText(string(R.string.review_result_back))
    }

    private fun progressOf(type: ItemType) = db.countRows("learning_progress WHERE item_type = '${type.name}'")

    @Test
    fun kanjiAlone_thenGrammarAlone_eachOnItsOwnSchedule_andItSurvivesARestart() {
        AppTestState.reset(seedContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            openReview()
            // Three cards, one per type, each with its own session.
            val kanji = string(R.string.review_type_start, string(R.string.totals_kanji), 2)
            composeRule.onNodeWithTag(ReviewTags.startType(ItemType.KANJI)).performScrollTo().assertIsEnabled()
            waitForText(kanji)
            composeRule.onNodeWithTag(ReviewTags.startType(ItemType.KANJI)).performClick()
            answerAll(2) {
                // Only kanji in this session.
                composeRule.onNode(hasText(string(R.string.practice_type_kanji))).assertIsDisplayed()
            }
            assertEquals(2, progressOf(ItemType.KANJI))
            assertEquals(0, progressOf(ItemType.VOCABULARY))
            assertEquals(0, progressOf(ItemType.GRAMMAR))
        }

        // Closed and opened again: the kanji answered today are still there, nothing else moved.
        ActivityScenario.launch(MainActivity::class.java).use {
            openReview()
            val done = string(R.string.review_type_done_today)
            composeRule.onNodeWithTag(ReviewTags.typeCard(ItemType.KANJI)).performScrollTo()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(hasText("2") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(ReviewTags.typeCard(ItemType.KANJI))))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            waitForText(done)
            assertEquals(2, progressOf(ItemType.KANJI))
            assertEquals(2, db.countRows("review_records"))

            // Grammar alone: the answer shows the pattern's example.
            composeRule.onNodeWithTag(ReviewTags.startType(ItemType.GRAMMAR)).performScrollTo().performClick()
            answerAll(1) { waitForText("Tôi vừa mới đến Nhật.") }
            assertEquals(1, progressOf(ItemType.GRAMMAR))
            assertEquals(2, progressOf(ItemType.KANJI))
            assertEquals(0, progressOf(ItemType.VOCABULARY))
        }
    }
}
