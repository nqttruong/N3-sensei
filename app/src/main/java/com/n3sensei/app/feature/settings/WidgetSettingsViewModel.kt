package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.domain.course.CourseBuilder
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.widget.WidgetMode
import com.n3sensei.app.domain.widget.WidgetSettings
import com.n3sensei.app.domain.widget.WidgetTap
import com.n3sensei.app.domain.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** "Cài đặt → Widget". Every change is saved and the widgets on the home screen are refreshed. */
class WidgetSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    contentRepository: ContentRepository,
    private val updater: WidgetUpdater,
) : ViewModel() {

    val settings: StateFlow<WidgetSettings?> = preferencesRepository.preferences
        .map { it.widgetSettings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Lessons of Học → Bài học (1…n) the widget can follow. */
    val lessonCount: StateFlow<Int> = CourseBuilder(contentRepository).observe()
        .map { it.size }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private val writeLock = Mutex()

    fun setMode(mode: WidgetMode) = update { it.copy(mode = mode) }
    fun setLesson(lesson: Int) = update { it.copy(lesson = lesson.coerceAtLeast(1), mode = WidgetMode.LESSON) }
    fun setShowFurigana(show: Boolean) = update { it.copy(showFurigana = show) }
    fun setShowMeaning(show: Boolean) = update { it.copy(showMeaning = show) }
    fun setTap(tap: WidgetTap) = update { it.copy(tap = tap) }

    private fun update(transform: (WidgetSettings) -> WidgetSettings) {
        viewModelScope.launch {
            writeLock.withLock {
                val current = preferencesRepository.preferences.first().widgetSettings
                val next = transform(current)
                if (next == current) return@withLock
                preferencesRepository.setWidgetSettings(next)
                updater.refresh()
            }
        }
    }
}
