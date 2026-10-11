package com.n3sensei.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ContentTotals
import com.n3sensei.app.domain.model.ProgressSummary
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

data class HomeUiState(val totals: ContentTotals, val progress: ProgressSummary)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    contentRepository: ContentRepository,
    progressRepository: ProgressRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<UiState<HomeUiState>> = combine(
        contentRepository.observeContentTotals(),
        clock.ticks().flatMapLatest { progressRepository.observeSummary(it) },
    ) { totals, progress -> UiState.Success(HomeUiState(totals, progress)) }
        .onStart<UiState<HomeUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
}
