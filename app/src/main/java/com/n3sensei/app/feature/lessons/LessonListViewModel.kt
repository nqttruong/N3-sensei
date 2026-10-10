package com.n3sensei.app.feature.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.asListUiState
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LessonListViewModel(
    contentRepository: ContentRepository,
) : ViewModel() {
    val lessons: StateFlow<UiState<List<LessonSummary>>> = contentRepository.observeLessons()
        .asListUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
}
