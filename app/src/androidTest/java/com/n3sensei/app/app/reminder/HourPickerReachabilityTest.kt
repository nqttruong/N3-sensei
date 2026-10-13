package com.n3sensei.app.app.reminder

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression (acceptance M1): the hour menu was laid out for the full window height under
 * edge-to-edge, the window manager clamped it between the system bars, and "23:00" stayed
 * below the screen edge however far the menu was scrolled. The menu is scrolled to the end,
 * then the accessibility tree (what the screen really shows) must have 23:00 visible.
 */
@RunWith(AndroidJUnit4::class)
class HourPickerReachabilityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            out += n
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        instrumentation.uiAutomation.windows.forEach { walk(it.root) }
        return out
    }

    private fun waitForNode(text: String): AccessibilityNodeInfo? {
        repeat(30) {
            allNodes().firstOrNull { it.text?.toString() == text }?.let { return it }
            Thread.sleep(100)
        }
        return null
    }

    private fun visible(node: AccessibilityNodeInfo): Boolean {
        val r = Rect().also { node.getBoundsInScreen(it) }
        return node.isVisibleToUser && !r.isEmpty
    }

    @Test
    fun everyHourOfTheEndMenu_canBeReachedOnScreen() {
        AppTestState.reset(seedContent = false)
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.SETTINGS.testTag).performClick()
            composeRule.onNode(hasText(context.getString(R.string.reminder_settings_title)) and hasClickAction()).performClick()
            // Default window 08:00–21:00; open the end-hour menu.
            composeRule.onNode(hasText(context.getString(R.string.reminder_hour, 21)) and hasClickAction()).performClick()
            composeRule.waitForIdle()
            val last = context.getString(R.string.reminder_hour, 23)
            assertTrue("menu did not open", waitForNode(context.getString(R.string.reminder_hour, 0)) != null)
            // Scroll the menu as a user would (as far as it goes), then ask the real screen.
            composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText(context.getString(R.string.reminder_hour, 0))))
                .performScrollToNode(hasText(last))
            composeRule.waitForIdle()
            val node = waitForNode(last)
            val bounds = Rect().also { r -> node?.getBoundsInScreen(r) }
            assertTrue("$last never became visible (visibleToUser=${node?.isVisibleToUser}, bounds=$bounds)", node != null && visible(node))
        }
    }
}
