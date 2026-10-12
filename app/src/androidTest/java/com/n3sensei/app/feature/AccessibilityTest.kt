package com.n3sensei.app.feature

import android.text.Spanned
import android.text.style.LocaleSpan
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What TalkBack receives, read from the real accessibility tree of the running app. */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun string(@StringRes id: Int) = context.getString(id)

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Walks the tree TalkBack sees (Compose nodes are not found by findAccessibilityNodeInfosByText). */
    private fun findNode(text: String): AccessibilityNodeInfo? {
        var root: AccessibilityNodeInfo? = null
        repeat(50) { if (root == null) root = instrumentation.uiAutomation.rootInActiveWindow ?: run { Thread.sleep(100); null } }
        fun walk(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.text?.toString() == text) return n
            for (i in 0 until n.childCount) walk(n.getChild(i))?.let { return it }
            return null
        }
        return walk(root)
    }

    private fun localeOf(node: AccessibilityNodeInfo): String? =
        (node.text as? Spanned)?.let { s -> s.getSpans(0, s.length, LocaleSpan::class.java).firstOrNull()?.locale?.language }

    /**
     * Regression: Japanese words carried their language only in the text style, which does
     * not reach accessibility, so TalkBack read 経験 with the Vietnamese voice.
     */
    @Test
    fun japaneseWords_areMarkedJapaneseForTalkBack_vietnameseIsNot() {
        AppTestState.reset(seedContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            composeRule.onNode(hasText(string(R.string.vocabulary_title)) and hasClickAction()).performClick()
            waitFor("経験")
            composeRule.waitForIdle()
            val word = findNode("経験")
            assertNotNull(word)
            assertEquals("ja", localeOf(word!!))
            val meaning = findNode("kinh nghiệm")
            assertTrue(meaning != null && localeOf(meaning) == null)
        }
    }

    @Test
    fun furiganaText_isReadAsPlainJapaneseWithoutReadingsInBetween() {
        AppTestState.reset(seedContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.LEARN.testTag).performClick()
            composeRule.onNode(hasText(string(R.string.vocabulary_title)) and hasClickAction()).performClick()
            waitFor("経験")
            composeRule.onNode(hasText("経験") and hasClickAction()).performClick()
            // Example sentence with ruby: one node, the base text only, marked Japanese.
            val sentence = "日本で働いた経験があります。"
            waitFor(sentence)
            val node = composeRule.onNode(hasText(sentence)).fetchSemanticsNode()
            val text = node.config[SemanticsProperties.Text].single()
            assertEquals(sentence, text.text)
            assertEquals("ja", text.spanStyles.single().item.localeList?.first()?.language)
        }
    }
}
