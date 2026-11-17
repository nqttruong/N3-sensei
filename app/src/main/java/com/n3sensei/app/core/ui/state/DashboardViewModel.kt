package com.n3sensei.app.core.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.analytics.DueCounts
import com.n3sensei.app.domain.analytics.LearningAnalytics
import com.n3sensei.app.domain.analytics.PriorityItem
import com.n3sensei.app.domain.analytics.StatsPeriod
import com.n3sensei.app.domain.analytics.StatusByType
import com.n3sensei.app.domain.forecast.DailyGoal
import com.n3sensei.app.domain.forecast.DailyGoalCalculator
import com.n3sensei.app.domain.forecast.ForecastAssumptions
import com.n3sensei.app.domain.forecast.HorizonForecast
import com.n3sensei.app.domain.forecast.ProgressEstimate
import com.n3sensei.app.domain.forecast.ProgressEstimator
import com.n3sensei.app.domain.forecast.ReviewSessionRepository
import com.n3sensei.app.domain.forecast.ReviewSessionSummary
import com.n3sensei.app.domain.forecast.TrendAggregator
import com.n3sensei.app.domain.forecast.TrendPoint
import com.n3sensei.app.domain.forecast.TrendUnit
import com.n3sensei.app.domain.forecast.WorkloadForecaster
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.repository.AnalyticsRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/**
 * Study dashboard shared by Trang chủ (goal, due) and Thống kê (forecasts, trend, volume,
 * progress estimate, sessions). Fields from the database are REAL; [forecasts] estimates and [progress] are ESTIMATES. */
data class DashboardUiState(
    val goal: DailyGoal,
    val due: DueCounts,
    val priority: List<PriorityItem>,
    val answersByType7Days: Map<ItemType, Int>,
    val statusByType: StatusByType,
    val trendUnit: TrendUnit,
    val trend: List<TrendPoint>,
    val forecasts: List<HorizonForecast>,
    val assumptions: ForecastAssumptions,
    val progress: ProgressEstimate,
    val sessions: List<ReviewSessionSummary>,
) {
    val hasContent: Boolean get() = statusByType.values.any { it.total > 0 }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val analytics: AnalyticsRepository,
    private val sessions: ReviewSessionRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
    /**
     * When given, new kanji count for the goal and forecasts only in the lessons chosen for
     * review (as in Ôn tập); otherwise every new kanji counts.
     */
    private val progress: ProgressRepository? = null,
) : ViewModel() {

    private val _trendUnit = MutableStateFlow(TrendUnit.DAY)
    val trendUnit: StateFlow<TrendUnit> = _trendUnit.asStateFlow()

    fun selectTrendUnit(unit: TrendUnit) {
        _trendUnit.value = unit
    }

    val state: StateFlow<UiState<DashboardUiState>> =
        combine(clock.ticks(), _trendUnit, preferences.preferences) { now, unit, prefs -> Triple(now, unit, prefs.reviewSettings) }
            .flatMapLatest { (now, unit, settings) ->
                val zone = clock.zone
                val today = now.atZone(zone).toLocalDate()
                val (todayFrom, todayTo) = StatsPeriod.TODAY.range(now, zone)
                val (weekFrom, _) = StatsPeriod.LAST_7_DAYS.range(now, zone)
                val paceFrom = today.minusDays((ProgressEstimator.WINDOW_DAYS - 1).toLong())
                val bucketsFrom = minOf(paceFrom, TrendAggregator.firstDay(unit, today)).atStartOfDay(zone).toInstant()

                val real = combine(
                    analytics.observeActivity(todayFrom, todayTo),
                    analytics.observeDue(now, ReviewTime.startOfDay(now, zone)),
                    analytics.observeStatusByType(),
                    analytics.observeAnswersByType(weekFrom, todayTo),
                    analytics.observePriorityItems(now, PRIORITY_LIMIT),
                ) { activity, due, status, byType, priority -> Real(activity.newItems, activity.reviewAnswers, due, status, byType, priority) }

                val newKanjiForReview = progress
                    ?.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
                    ?.map<ReviewOverview, Int?> { it.newByType[ItemType.KANJI] ?: 0 }
                    ?: flowOf(null)

                combine(
                    real,
                    analytics.observeTimeBuckets(bucketsFrom, todayTo),
                    analytics.observeSchedule(),
                    sessions.observeRecent(SESSION_LIMIT),
                    newKanjiForReview,
                ) { r, buckets, schedule, recent, newKanji ->
                    // New items review can still introduce: kanji only from the lessons chosen for review.
                    val newAvailable = r.status.entries.sumOf { (type, s) -> if (type == ItemType.KANJI && newKanji != null) newKanji else s.new }
                    val assumptions = ForecastAssumptions(settings.limits.newPerDayTotal, newAvailable)
                    val pace = LearningAnalytics.dailySeries(buckets, zone, today, ProgressEstimator.WINDOW_DAYS)
                    UiState.Success(
                        DashboardUiState(
                            goal = DailyGoalCalculator.goal(settings.limits, r.newToday, newAvailable, r.reviewsToday, r.due.due),
                            due = r.due,
                            priority = r.priority,
                            answersByType7Days = r.byType,
                            statusByType = r.status,
                            trendUnit = unit,
                            trend = TrendAggregator.aggregate(buckets, zone, today, unit),
                            forecasts = WorkloadForecaster(settings.ladder).forecast(schedule, assumptions, now, zone),
                            assumptions = assumptions,
                            progress = ProgressEstimator.estimate(
                                remaining = newAvailable,
                                newItemsByDay = pace.map { it.newItems },
                                studyDays = pace.count { it.answers > 0 },
                                newPerDaySetting = settings.limits.newPerDayTotal,
                            ),
                            sessions = recent,
                        ),
                    )
                }
            }
            .onStart<UiState<DashboardUiState>> { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private data class Real(
        val newToday: Int,
        val reviewsToday: Int,
        val due: DueCounts,
        val status: StatusByType,
        val byType: Map<ItemType, Int>,
        val priority: List<PriorityItem>,
    )

    companion object {
        const val PRIORITY_LIMIT = 5
        const val SESSION_LIMIT = 5
    }
}
