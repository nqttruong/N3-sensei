package com.n3sensei.app.feature.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.course.CourseBuilder
import com.n3sensei.app.domain.course.CourseLesson
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Học → Bài học: the course lessons (24 kanji, 30 words, 7 grammar points each). */
class LessonListViewModel(
    contentRepository: ContentRepository,
) : ViewModel() {
    val lessons: StateFlow<UiState<List<CourseLesson>>> = CourseBuilder(contentRepository).observe()
        .map<List<CourseLesson>, UiState<List<CourseLesson>>> { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
}
