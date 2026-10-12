package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.content.FlashcardSession
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningUxTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun homeButton_startsTheSessionInOneTap_andTabsAreHiddenWhileStudying() {
        AppTestState.reset(seedContent = true, seedCountsAsRealContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            // 7 new seed items, default limit 20 → the button says how many cards.
            waitFor(string(R.string.review_start, 7))
            composeRule.onNode(hasText(string(R.string.review_start, 7)) and hasClickAction()).performClick()
            waitFor(string(R.string.practice_progress, 1, 7))
            waitFor(string(R.string.review_reason_new))
            assertEquals(0, composeRule.onAllNodes(hasText(string(R.string.nav_home))).fetchSemanticsNodes().size)
            composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
            composeRule.onNode(hasText(string(R.string.rating_good), substring = true) and hasClickAction()).performClick()
            waitFor(string(R.string.practice_progress, 2, 7))
        }
    }

    private fun ratingRows(fontScale: Float): Pair<Float, Float> {
        val item = PracticeItem.VocabularyItem(Vocabulary("v", "l", "経験", "けいけん", null, "kinh nghiệm", null, "N3", null))
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                N3SenseiTheme {
                    FlashcardSession(
                        banner = null, position = 0, total = 3, item = item, isAnswerRevealed = true,
                        onReveal = {}, onRate = {},
                        ratingHints = ReviewRating.entries.associateWith { "20 phút" },
                    )
                }
            }
        }
        fun top(label: Int) = composeRule.onNode(hasText(string(label), substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot.top
        return top(R.string.rating_again) to top(R.string.rating_good)
    }

    /** Regression: at large font scale the four buttons in one row cut their labels. */
    @Test
    fun ratingButtons_wrapToTwoRows_atLargeFontScale() {
        val (again, good) = ratingRows(fontScale = 2f)
        assertNotEquals(again, good)
    }

    @Test
    fun ratingButtons_stayInOneRow_atNormalFontScale() {
        val (again, good) = ratingRows(fontScale = 1f)
        assertEquals(again, good)
    }
}
