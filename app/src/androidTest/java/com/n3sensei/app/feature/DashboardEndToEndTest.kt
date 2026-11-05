package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import com.n3sensei.app.domain.forecast.ProgressEstimator
import com.n3sensei.app.testing.AnalyticsFixture
import com.n3sensei.app.testing.AppTestState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dashboard shows the numbers counted by hand in [AnalyticsFixture] (day D = today on the
 * device; every value below holds at any time of day D).
 */
@RunWith(AndroidJUnit4::class)
class DashboardEndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitFor(text: String, substring: Boolean = false) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shown(text: String, substring: Boolean = false) {
        waitFor(text, substring)
        composeRule.onNode(hasText(text, substring = substring)).performScrollTo().assertIsDisplayed()
    }

    private fun openStats() = composeRule.onNodeWithTag(TopLevelDestination.STATS.testTag).performClick()

    private fun launch(fixture: Boolean): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = true, seedCountsAsRealContent = true)
        if (fixture) runBlocking { AnalyticsFixture(ZoneId.systemDefault(), LocalDate.now()).insert(AppTestState.appContainer.database) }
        return ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun realNumbers_matchHandCount() {
        launch(fixture = true).use {
            // Seed pack: 7 items, 4 started → 3 new left; default limits 20 new / 200 reviews.
            shown(string(R.string.dashboard_goal_new, 2, 5)) // r4, r5 today
            shown(string(R.string.dashboard_goal_reviews, 3, 5)) // r2 r3 r9 + 2 due
            shown("増える") // overdue, listed first
            shown("経験")
            // Trang chủ keeps the proverb, the goal and what is due; the rest is in Thống kê.
            shown(string(R.string.home_proverb_title))
            assertTrue(composeRule.onAllNodes(hasText(string(R.string.dashboard_forecast_rule_scheduled, 2))).fetchSemanticsNodes().isEmpty())
            openStats()
            shown(string(R.string.dashboard_forecast_rule_scheduled, 2))
            shown(string(R.string.dashboard_forecast_rule_estimate, 20, 3))
            // 3 study days and 4 new items in 28 days: not enough for a pace estimate.
            shown(string(R.string.dashboard_progress_not_enough, ProgressEstimator.MIN_STUDY_DAYS, ProgressEstimator.MIN_NEW_ITEMS, 3, 4))
            shown(string(R.string.dashboard_progress_settings, 20, 1, 3))
            shown(string(R.string.dashboard_sessions_empty))
        }
    }

    @Test
    fun trendUnit_switchKeepsTheSameTotal() {
        launch(fixture = true).use {
            openStats()
            // In Thống kê the trend starts by week (day by day is the daily chart there).
            val week = string(R.string.dashboard_trend_description, string(R.string.dashboard_trend_week).lowercase(), 7, 0).substringBefore(", ")
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasContentDescription(week, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            // performScrollTo on a chip inside the LazyRow does not scroll the page: bring the chip row in first.
            composeRule.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText(string(R.string.dashboard_trend_month)))).performScrollTo()
            composeRule.onNode(hasText(string(R.string.dashboard_trend_month)) and hasClickAction()).performClick()
            val month = string(R.string.dashboard_trend_description, string(R.string.dashboard_trend_month).lowercase(), 7, 0).substringBefore(", ")
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasContentDescription(month, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun sessionHistory_showsCompletion() {
        launch(fixture = false).use {
            runBlocking {
                with(AppTestState.appContainer.reviewSessionRepository) {
                    start("e2e-session", planned = 4, at = Instant.now())
                    updateAnswered("e2e-session", 3, Instant.now())
                }
            }
            openStats()
            shown("3 / 4 thẻ (75%)", substring = true)
            shown(string(R.string.dashboard_session_unfinished))
        }
    }

    @Test
    fun noHistory_showsZerosWithoutFakeEstimates() {
        launch(fixture = false).use {
            shown(string(R.string.dashboard_goal_new, 0, 7))
            shown(string(R.string.dashboard_goal_reviews, 0, 0))
            shown(string(R.string.dashboard_priority_empty))
            openStats()
            shown(string(R.string.dashboard_trend_empty))
            shown(string(R.string.dashboard_progress_not_enough, ProgressEstimator.MIN_STUDY_DAYS, ProgressEstimator.MIN_NEW_ITEMS, 0, 0))
        }
    }
}
