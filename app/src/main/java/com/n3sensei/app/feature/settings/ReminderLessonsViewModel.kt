package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.reminder.LessonOption
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.reminder.ReminderLessonCatalog
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReminderLessonsUiState(
    val type: ItemType,
    val options: List<LessonOption>,
    /** The choice as it applies to the current lessons (keys of removed lessons dropped). */
    val selection: LessonSelection,
) {
    fun isChecked(key: String): Boolean = selection.allows(key)
}

/**
 * The lessons one kind of reminder is taken from. The list follows the content (Học); the
 * choice is saved at once with the other reminder settings.
 */
class ReminderLessonsViewModel(
    private val type: ItemType,
    private val preferences: UserPreferencesRepository,
    content: ContentRepository,
) : ViewModel() {

    private val catalog = ReminderLessonCatalog.observe(content)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<UiState<ReminderLessonsUiState>> = combine(catalog, preferences.preferences) { catalog, prefs ->
        if (catalog == null) {
            UiState.Loading
        } else {
            val options = catalog.optionsOf(type)
            if (options.isEmpty()) {
                UiState.Empty
            } else {
                UiState.Success(ReminderLessonsUiState(type, options, catalog.current(type, prefs.reminderSettings.lessons.of(type))))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val writes = Mutex()

    fun toggle(key: String) = change { catalog, now -> catalog.toggle(type, now, key) }

    fun selectAll() = change { _, _ -> LessonSelection.All }

    fun clearAll() = change { _, _ -> LessonSelection.Only(emptySet()) }

    private fun change(next: (ReminderLessonCatalog, LessonSelection) -> LessonSelection) {
        viewModelScope.launch {
            writes.withLock {
                val catalog = catalog.value ?: return@withLock
                val settings = preferences.preferences.first().reminderSettings
                val updated = settings.copy(lessons = settings.lessons.with(type, next(catalog, settings.lessons.of(type))))
                if (updated != settings) preferences.setReminderSettings(updated)
            }
        }
    }
}
