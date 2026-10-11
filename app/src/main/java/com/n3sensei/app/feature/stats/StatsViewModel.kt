package com.n3sensei.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ContentTotals
import com.n3sensei.app.domain.model.ProgressSummary
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.core.ui.state.ticks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

data class StatsUiState(
    val totals: ContentTotals,
    val progress: ProgressSummary,
    val ratingCounts: Map<ReviewRating, Int>,
)

/** Every figure comes from the database; nothing is estimated. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    contentRepository: ContentRepository,
    progressRepository: ProgressRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<UiState<StatsUiState>> = combine(
        contentRepository.observeContentTotals(),
        clock.ticks().flatMapLatest { progressRepository.observeSummary(it) },
        progressRepository.observeRatingCounts(),
    ) { totals, progress, ratings -> UiState.Success(StatsUiState(totals, progress, ratings)) }
        .onStart<UiState<StatsUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
}
