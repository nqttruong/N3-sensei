package com.n3sensei.app.feature

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.feature.vocabulary.VocabularyDetailTags
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.OverlayTesting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The popup's bookmark saves the word into "Từ của tôi" as "Đã lưu", where it is listed with
 * that label and offers neither "Cần học lại" nor practice (SEED TEST DATA).
 */
@RunWith(AndroidJUnit4::class)
class BookmarkUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun waitForText(text: String) = composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private val db get() = AppTestState.appContainer.database

    @After
    fun tearDown() {
        OverlayTesting.close()
        AppTestState.reset(seedContent = false)
    }

    @Test
    fun bookmark_savesOnce_asSaved_listedInMyWords_withoutStudyActions() {
        AppTestState.reset(seedContent = true)
        OverlayTesting.permission(true)
        assertTrue(OverlayTesting.show("経験"))
        waitForText("kinh nghiệm")
        composeRule.onNodeWithTag(QuickTranslateTags.BOOKMARK).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasContentDescription(string(R.string.quick_translate_bookmarked))).fetchSemanticsNodes().isNotEmpty()
        }
        // Saving keeps the popup open; tapping the saved bookmark again makes no second entry.
        assertTrue(OverlayTesting.isShowing)
        composeRule.onNodeWithTag(QuickTranslateTags.BOOKMARK).performClick()
        composeRule.waitForIdle()
        OverlayTesting.close()
        assertEquals(1, db.countRows("user_words"))
        db.openHelper.readableDatabase.query("SELECT study_state FROM user_words").use { it.moveToFirst(); assertEquals("SAVED", it.getString(0)) }
        // Bookmarking scheduled nothing.
        assertEquals(0, db.countRows("learning_progress"))
        assertEquals(0, db.countRows("item_marks"))

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            composeRule.onNode(hasText(string(R.string.learn_my_words)) and hasClickAction()).performScrollTo().performClick()
            waitForText(string(R.string.status_saved))
            composeRule.onNodeWithText("経験").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(VocabularyDetailTags.SAVED_NOTE)).fetchSemanticsNodes().isNotEmpty()
            }
            // No "Cần học lại", no practice from a saved word.
            assertTrue(composeRule.onAllNodesWithText(string(R.string.mark_add)).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText(string(R.string.practice_this_lesson)).fetchSemanticsNodes().isEmpty())
            // Its meaning from the dictionary, kept with the saved word (no translation was available yet).
            composeRule.onNodeWithText("kinh nghiệm").performScrollTo()
        }
    }
}
