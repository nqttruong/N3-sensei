package com.n3sensei.app.feature.stats

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.analytics.ActivityTotals
import com.n3sensei.app.domain.analytics.DueCounts
import com.n3sensei.app.domain.analytics.StatsPeriod
import com.n3sensei.app.domain.analytics.StatusByType
import com.n3sensei.app.domain.analytics.StatusCounts
import com.n3sensei.app.domain.analytics.TimeBucket
import com.n3sensei.app.domain.analytics.TroubleItem
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.repository.AnalyticsRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = MutableClock(Instant.parse("2026-10-11T05:00:00Z"), zone) // 12:00 local

    private class FakeAnalytics : AnalyticsRepository {
        val activityRequests = mutableListOf<Pair<Instant?, Instant>>()
        var activity = MutableStateFlow(ActivityTotals.EMPTY)
        var status: StatusByType = ItemType.entries.associateWith { StatusCounts.EMPTY }
        var studyBuckets: List<Instant> = emptyList()

        override fun observeActivity(from: Instant?, to: Instant): Flow<ActivityTotals> {
            activityRequests += from to to
            return activity
        }
        override fun observeTimeBuckets(from: Instant, to: Instant) = flowOf(emptyList<TimeBucket>())
        override fun observeStudyBuckets() = flowOf(studyBuckets)
        override fun observeStatusByType() = flowOf(status)
        override fun observeDue(now: Instant, dayStart: Instant) = flowOf(DueCounts(3, 1))
        override fun observeDueBuckets(from: Instant, to: Instant) = flowOf(emptyList<TimeBucket>())
        override fun observeTroubleItems(from: Instant?, to: Instant, limit: Int) = flowOf(emptyList<TroubleItem>())
        override fun observeAnswersByType(from: Instant?, to: Instant) = flowOf(ItemType.entries.associateWith { 0 })
        override fun observePriorityItems(now: Instant, limit: Int) = flowOf(emptyList<com.n3sensei.app.domain.analytics.PriorityItem>())
        override fun observeSchedule() = flowOf(emptyList<com.n3sensei.app.domain.forecast.ScheduledItem>())
    }

    private val analytics = FakeAnalytics()
    private val viewModel by lazy { StatsViewModel(analytics, clock) }
    private fun ui() = (viewModel.state.value as UiState.Success).data

    @Test
    fun emptyDatabase_hasNoContentNoHistoryAndNoRecallRate() = runTest {
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val ui = ui()
        assertFalse(ui.hasContent)
        assertFalse(ui.hasHistory)
        assertNull(ui.recallRate)
        assertEquals(0, ui.activity.answers)
        assertEquals(7, ui.daily.size)
        assertTrue(ui.daily.all { it.answers == 0 })
        assertEquals(listOf(3, 0, 0, 0, 0, 0, 0), ui.forecast.map { it.answers }) // due now counted today
    }

    @Test
    fun periodSelection_requeriesWithLocalDayRange() = runTest {
        viewModel.state.launchIn(backgroundScope)
        viewModel.selectPeriod(StatsPeriod.TODAY)
        runCurrent()
        val (from, to) = analytics.activityRequests.last()
        assertEquals(Instant.parse("2026-10-10T17:00:00Z"), from) // 00:00 local
        assertEquals(Instant.parse("2026-10-11T17:00:00Z"), to)
        assertEquals(StatsPeriod.TODAY, ui().period)

        viewModel.selectPeriod(StatsPeriod.ALL_TIME)
        runCurrent()
        assertNull(analytics.activityRequests.last().first)
    }

    @Test
    fun recallRate_streakAndDueAreDerived() = runTest {
        analytics.activity.value = ActivityTotals(5, 4, 2, 3, 2, mapOf(ReviewRating.AGAIN to 1, ReviewRating.GOOD to 1, ReviewRating.HARD to 1))
        analytics.studyBuckets = listOf(Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-10-11T03:00:00Z"))
        analytics.status = mapOf(ItemType.VOCABULARY to StatusCounts(2, 1, 1, 0), ItemType.KANJI to StatusCounts.EMPTY, ItemType.GRAMMAR to StatusCounts.EMPTY)
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val ui = ui()
        assertEquals(2.0 / 3, ui.recallRate!!, 1e-9)
        assertEquals(2, ui.streak.current)
        assertTrue(ui.hasContent)
        assertEquals(3, ui.due)
        assertEquals(1, ui.overdue)
    }
}
