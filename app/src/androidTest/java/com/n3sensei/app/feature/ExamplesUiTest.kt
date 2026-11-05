package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.core.ui.content.ExamplesSectionTags
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Học → Từ vựng → a word: its examples under the theory, folded and unfolded; none shown when it has none (SEED TEST DATA). */
@RunWith(AndroidJUnit4::class)
class ExamplesUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitClickable(text: String): SemanticsNodeInteraction {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        return composeRule.onNode(hasText(text) and hasClickAction())
    }

    private fun gone(text: String) = composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

    @Test
    fun aWordsExamples_foldAndUnfold_andAWordWithoutExamplesShowsNoSection() {
        AppTestState.reset(seedContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            awaitClickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText("経験")
            composeRule.onNodeWithText("経験").performClick()
            waitForText("kinh nghiệm")

            val translation = "Tôi có kinh nghiệm làm việc ở Nhật."
            composeRule.onNodeWithText(string(R.string.examples_header, string(R.string.detail_examples), 1)).performScrollTo()
            composeRule.onNodeWithText(translation).performScrollTo()
            // Folded away, then back.
            composeRule.onNodeWithTag(ExamplesSectionTags.HEADER).performClick()
            gone(translation)
            composeRule.onNodeWithTag(ExamplesSectionTags.HEADER).performClick()
            waitForText(translation)
            Espresso.pressBack()

            // しっかり has no example: no empty "Ví dụ" section.
            waitForText("しっかり")
            composeRule.onNodeWithText("しっかり").performClick()
            waitForText("chắc chắn, vững vàng")
            assertTrue(composeRule.onAllNodesWithTag(ExamplesSectionTags.HEADER).fetchSemanticsNodes().isEmpty())
        }
    }
}
