package com.n3sensei.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.ticks
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewQueuePlanner
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

data class ReviewUiState(
    val overview: ReviewOverview,
    val limits: DailyLimits,
    /** New items a session would include now (limit applied). */
    val newToday: Int,
    /** Cards a session started now would contain. */
    val sessionSize: Int,
) {
    val limitReached: Boolean get() = sessionSize == 0 && (overview.due > 0 || overview.newAvailable > 0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(
    progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Recomputed every minute and whenever the data or the daily limits change. */
    val state: StateFlow<UiState<ReviewUiState>> = combine(
        clock.ticks(),
        preferencesRepository.preferences.map { it.reviewSettings.limits }.distinctUntilChanged(),
    ) { now, limits -> now to limits }
        .flatMapLatest { (now, limits) ->
            val zone = clock.zone
            progressRepository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
                .map { overview ->
                    val reviews = minOf(overview.due, ReviewQueuePlanner.remainingReviews(limits, overview.today))
                    val newToday = minOf(overview.newAvailable, ReviewQueuePlanner.remainingNew(limits, overview.today))
                    UiState.Success(ReviewUiState(overview, limits, newToday, reviews + newToday))
                }
        }
        .onStart<UiState<ReviewUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    /** Items marked as needing attention whose content still exists. */
    val markedCount: StateFlow<Int> = progressRepository.observeMarkedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)
}
