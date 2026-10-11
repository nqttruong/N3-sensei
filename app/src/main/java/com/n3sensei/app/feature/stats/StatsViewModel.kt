package com.n3sensei.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.ticks
import com.n3sensei.app.domain.analytics.ActivityTotals
import com.n3sensei.app.domain.analytics.DayActivity
import com.n3sensei.app.domain.analytics.LearningAnalytics
import com.n3sensei.app.domain.analytics.StatsPeriod
import com.n3sensei.app.domain.analytics.StatusByType
import com.n3sensei.app.domain.analytics.Streak
import com.n3sensei.app.domain.analytics.TroubleItem
import com.n3sensei.app.domain.repository.AnalyticsRepository
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant

data class StatsUiState(
    val period: StatsPeriod,
    val activity: ActivityTotals,
    /** Null when there were no review answers in the period. */
    val recallRate: Double?,
    val daily: List<DayActivity>,
    val streak: Streak,
    val statusByType: StatusByType,
    val troubleItems: List<TroubleItem>,
    val due: Int,
    val overdue: Int,
    /** Estimated items due on each of the next 7 days, today first. */
    val forecast: List<DayActivity>,
) {
    val hasContent: Boolean get() = statusByType.values.any { it.total > 0 }
    val hasHistory: Boolean get() = streak.longest > 0
}

/**
 * Statistics from Room; re-queried when the period changes, every minute (day boundaries,
 * due counts) and whenever the underlying tables change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val analytics: AnalyticsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.LAST_7_DAYS)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    fun selectPeriod(period: StatsPeriod) {
        _period.value = period
    }

    val state: StateFlow<UiState<StatsUiState>> =
        combine(_period, clock.ticks()) { period, now -> period to now }
            .flatMapLatest { (period, now) -> statsFor(period, now) }
            .onStart<UiState<StatsUiState>> { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private fun statsFor(period: StatsPeriod, now: Instant) = run {
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()
        val (from, to) = period.range(now, zone)
        val chartFrom = today.minusDays((period.chartDays - 1).toLong()).atStartOfDay(zone).toInstant()
        val dayStart = ReviewTime.startOfDay(now, zone)
        val forecastEnd = today.plusDays(FORECAST_DAYS.toLong()).atStartOfDay(zone).toInstant()

        val history = combine(
            analytics.observeActivity(from, to),
            analytics.observeTimeBuckets(chartFrom, to),
            analytics.observeStudyBuckets(),
        ) { activity, buckets, studyBuckets ->
            Triple(
                activity,
                LearningAnalytics.dailySeries(buckets, zone, today, period.chartDays),
                LearningAnalytics.streak(LearningAnalytics.studyDays(studyBuckets, zone), today),
            )
        }
        combine(
            history,
            analytics.observeStatusByType(),
            analytics.observeTroubleItems(from, to, TROUBLE_LIMIT),
            combine(analytics.observeDue(now, dayStart), analytics.observeDueBuckets(now.plusMillis(1), forecastEnd)) { due, buckets ->
                due to LearningAnalytics.forecast(buckets, due.due, zone, today, FORECAST_DAYS)
            },
        ) { (activity, daily, streak), status, trouble, (due, forecast) ->
            UiState.Success(
                StatsUiState(
                    period = period,
                    activity = activity,
                    recallRate = LearningAnalytics.recallRate(activity.reviewRatings),
                    daily = daily,
                    streak = streak,
                    statusByType = status,
                    troubleItems = trouble,
                    due = due.due,
                    overdue = due.overdue,
                    forecast = forecast,
                ),
            )
        }
    }

    companion object {
        const val TROUBLE_LIMIT = 10
        const val FORECAST_DAYS = 7
    }
}
