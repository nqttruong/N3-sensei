package com.n3sensei.app.core.ui.state

import com.n3sensei.app.domain.analytics.ActivityTotals
import com.n3sensei.app.domain.analytics.DueCounts
import com.n3sensei.app.domain.analytics.StatusCounts
import com.n3sensei.app.domain.analytics.TimeBucket
import com.n3sensei.app.domain.forecast.ForecastHorizon
import com.n3sensei.app.domain.forecast.GoalPart
import com.n3sensei.app.domain.forecast.ProgressEstimate
import com.n3sensei.app.domain.forecast.ScheduledItem
import com.n3sensei.app.domain.forecast.TrendUnit
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.testing.FakeAnalyticsRepository
import com.n3sensei.app.testing.FakeReviewSessionRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private val clock = MutableClock(at("2026-10-11T10:00"), zone)
    private val analytics = FakeAnalyticsRepository()
    private val sessions = FakeReviewSessionRepository()
    private val prefs = FakeUserPreferencesRepository(
        UserPreferences(onboardingCompleted = true, reviewSettings = ReviewSettings(limits = DailyLimits(newVocabulary = 10, newKanji = 0, newGrammar = 0, reviewsPerDay = 100))),
    )
    private val viewModel by lazy { DashboardViewModel(analytics, sessions, prefs, clock) }
    private fun ui() = (viewModel.state.value as UiState.Success).data

    @Test
    fun emptyDatabase_hasNoContent_zeroForecasts_andNotEnoughData() = runTest {
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val ui = ui()
        assertFalse(ui.hasContent)
        assertTrue(ui.forecasts.all { it.scheduledItems == 0 && it.estimatedReviews == 0 && it.estimatedNewItems == 0 })
        assertEquals(ProgressEstimate.AllStarted, ui.progress) // nothing left to start
        assertTrue(ui.sessions.isEmpty())
        assertEquals(GoalPart(0, 0), ui.goal.newItems)
    }

    @Test
    fun goalForecastAndProgress_comeFromRealDataAndSettings() = runTest {
        analytics.status = mapOf(
            ItemType.VOCABULARY to StatusCounts(new = 25, learning = 2, review = 0, mastered = 0),
            ItemType.KANJI to StatusCounts(new = 5, learning = 0, review = 1, mastered = 0),
            ItemType.GRAMMAR to StatusCounts.EMPTY,
        )
        analytics.activity = ActivityTotals(answers = 6, distinctItems = 6, newItems = 4, reviewAnswers = 2, distinctReviewedItems = 2, reviewRatings = emptyMap())
        analytics.due = DueCounts(due = 3, overdue = 1)
        analytics.schedule = listOf(ScheduledItem(2, at("2026-10-12T08:00")), ScheduledItem(3, at("2026-10-20T08:00")))
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val ui = ui()
        assertTrue(ui.hasContent)
        assertEquals(GoalPart(done = 4, target = 10), ui.goal.newItems) // limit 10, 30 left
        assertEquals(GoalPart(done = 2, target = 5), ui.goal.reviews) // 2 done + 3 due
        assertEquals(1, ui.forecasts.single { it.horizon == ForecastHorizon.TOMORROW }.scheduledItems)
        assertEquals(2, ui.forecasts.single { it.horizon == ForecastHorizon.NEXT_14_DAYS }.scheduledItems)
        assertEquals(10, ui.forecasts.single { it.horizon == ForecastHorizon.TOMORROW }.estimatedNewItems)
        assertEquals(30, ui.assumptions.newAvailable)
        // No history yet: not enough data, but the settings-based figure is available (30 / 10 = 3 days).
        assertEquals(ProgressEstimate.NotEnoughData(remaining = 30, studyDays = 0, newItems = 0, settingsDays = 3), ui.progress)
    }

    @Test
    fun enoughHistory_givesAnEstimate() = runTest {
        analytics.status = mapOf(ItemType.VOCABULARY to StatusCounts(new = 40, 0, 0, 0), ItemType.KANJI to StatusCounts.EMPTY, ItemType.GRAMMAR to StatusCounts.EMPTY)
        // 1 new item every day for the last 28 days → 1/day → 40 days.
        analytics.buckets = (0 until 28).map { TimeBucket(at("2026-10-11T08:00").minusSeconds(86_400L * it), answers = 1, newItems = 1) }
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val estimate = ui().progress as ProgressEstimate.Estimate
        assertEquals(40, estimate.expectedDays)
        assertEquals(1.0, estimate.itemsPerDay, 1e-9)
    }

    @Test
    fun trendUnitSelection_regroupsTheSameHistory() = runTest {
        analytics.buckets = listOf(TimeBucket(at("2026-10-05T08:00"), 3, 1), TimeBucket(at("2026-10-11T08:00"), 2, 0))
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf(2), ui().trend.takeLast(1).map { it.answers }) // per day: today
        viewModel.selectTrendUnit(TrendUnit.WEEK)
        runCurrent()
        assertEquals(TrendUnit.WEEK, ui().trendUnit)
        assertEquals(5, ui().trend.last().answers) // both in the week starting Monday 05/10
    }
}
