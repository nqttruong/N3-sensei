package com.n3sensei.app.core.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/** Shared state for the vocabulary / kanji / grammar lists: search box + lesson filter. */
abstract class LessonFilteredListViewModel<T>(
    contentRepository: ContentRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lessonId = MutableStateFlow<String?>(null)
    val lessonId: StateFlow<String?> = _lessonId.asStateFlow()

    val lessons: StateFlow<List<LessonSummary>> = contentRepository.observeLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val items: StateFlow<UiState<List<T>>> by lazy {
        searchableItems(_query, _lessonId, ::observeAll, ::search)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
    }

    protected abstract fun observeAll(lessonId: String?): Flow<List<T>>
    protected abstract suspend fun search(query: String, lessonId: String?): List<T>

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onLessonSelected(lessonId: String?) {
        _lessonId.value = lessonId
    }
}

/** Keeps upstream flows alive across configuration changes. */
const val STOP_TIMEOUT_MILLIS = 5_000L
