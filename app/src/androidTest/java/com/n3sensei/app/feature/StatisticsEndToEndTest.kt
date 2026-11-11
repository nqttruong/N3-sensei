package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.AnalyticsFixture
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Statistics screen shows exactly the numbers counted by hand in [AnalyticsFixture]
 * (fixture day D = today on the device).
 */
@RunWith(AndroidJUnit4::class)
class StatisticsEndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitFor(vararg texts: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(texts.map { hasText(it) }.reduce { a, b -> a and b }).fetchSemanticsNodes().isNotEmpty()
    }

    private fun stat(value: String, label: String) {
        waitFor(value, label)
        composeRule.onNode(hasText(value) and hasText(label)).performScrollTo().assertIsDisplayed()
    }

    private fun launchWithFixture(): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = true, seedCountsAsRealContent = true)
        runBlocking { AnalyticsFixture(ZoneId.systemDefault(), LocalDate.now()).insert(AppTestState.appContainer.database) }
        return ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun todayNumbersMatchHandCount() {
        launchWithFixture().use {
            composeRule.onNodeWithTag(TopLevelDestination.STATS.testTag).performClick()
            composeRule.onNode(hasText(string(R.string.stats_period_today)) and hasClickAction()).performClick()

            stat("4", string(R.string.stats_items_studied))
            stat("2", string(R.string.stats_new_items))
            stat("3", string(R.string.stats_review_answers))
            stat("2", string(R.string.stats_items_reviewed))
            waitFor(string(R.string.stats_total_answers, 5))
            stat("2", string(R.string.stats_due))
            stat("1", string(R.string.stats_overdue))
            waitFor(string(R.string.stats_recall_value, 67))
            composeRule.onNodeWithText(string(R.string.stats_recall_detail, 2, 3)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.stats_streak_current, 2)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("経").performScrollTo().assertIsDisplayed() // top trouble item
        }
    }

    @Test
    fun sevenDaysNumbersMatchHandCount() {
        launchWithFixture().use {
            composeRule.onNodeWithTag(TopLevelDestination.STATS.testTag).performClick()
            composeRule.onNode(hasText(string(R.string.stats_period_7)) and hasClickAction()).performClick()
            waitFor(string(R.string.stats_total_answers, 7))
            stat("4", string(R.string.stats_new_items))
        }
    }

    @Test
    fun statusChip_opensFilteredList() {
        launchWithFixture().use {
            composeRule.onNodeWithTag(TopLevelDestination.STATS.testTag).performClick()
            val learningChip = string(R.string.stats_status_count, string(R.string.status_learning), 1)
            waitFor(learningChip)
            // First "Đang học 1" chip belongs to vocabulary (増える).
            composeRule.onAllNodes(hasText(learningChip) and hasClickAction())[0].performScrollTo().performClick()
            waitFor("増える")
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("経験")).fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun emptyHistory_showsNoDataStates() {
        AppTestState.reset(seedContent = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(TopLevelDestination.STATS.testTag).performClick()
            waitFor(string(R.string.common_no_content))
            composeRule.onNodeWithText(string(R.string.stats_no_activity)).performScrollTo().assertIsDisplayed()
            // "—" for the recall rate, and again for each type (no review answer of any type yet).
            val none = composeRule.onAllNodesWithText(string(R.string.stats_recall_none))
            none.onFirst().performScrollTo().assertIsDisplayed()
            assertEquals(1 + ItemType.entries.size, none.fetchSemanticsNodes().size)
            composeRule.onNodeWithText(string(R.string.stats_chart_empty, 7)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.stats_trouble_empty)).performScrollTo().assertIsDisplayed()
        }
    }
}
