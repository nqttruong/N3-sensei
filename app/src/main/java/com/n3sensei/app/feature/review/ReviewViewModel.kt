package com.n3sensei.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.asUiState
import com.n3sensei.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

class ReviewViewModel(
    progressRepository: ProgressRepository,
    clock: Clock,
) : ViewModel() {
    /** Real count of due items from the database (zero until the scheduler creates any). */
    val dueCount: StateFlow<UiState<Int>> = progressRepository.observeDueCount(clock.instant())
        .asUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    /** Items marked as needing attention whose content still exists. */
    val markedCount: StateFlow<Int> = progressRepository.observeMarkedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)
}
