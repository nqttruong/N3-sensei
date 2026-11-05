package com.n3sensei.app.feature.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.course.VocabularyGroup
import com.n3sensei.app.domain.course.VocabularyGroups
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Every vocabulary group of "Học → Từ vựng", live: progress bars follow the words' progress. */
class VocabularyGroupsViewModel(contentRepository: ContentRepository) : ViewModel() {
    val state: StateFlow<UiState<List<VocabularyGroup>>> = VocabularyGroups.observe(contentRepository)
        .map<List<VocabularyGroup>, UiState<List<VocabularyGroup>>> { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
}

/** One group, found again by its [key] (e.g. after the app was closed). */
class VocabularyGroupViewModel(private val key: String, contentRepository: ContentRepository) : ViewModel() {
    val state: StateFlow<UiState<VocabularyGroup>> = VocabularyGroups.observe(contentRepository)
        .map<List<VocabularyGroup>, UiState<VocabularyGroup>> { groups ->
            groups.firstOrNull { it.key == key }?.let { UiState.Success(it) } ?: UiState.NotFound
        }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
}
