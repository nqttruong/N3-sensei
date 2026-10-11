package com.n3sensei.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.repository.ContentImportRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide settings needed before any screen is shown: theme, onboarding, furigana. */
class MainViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    contentImportRepository: ContentImportRepository,
    reminderScheduler: ReminderScheduler,
) : ViewModel() {

    init {
        // Installs or upgrades the pack shipped with the app; a no-op when already current.
        viewModelScope.launch { contentImportRepository.installBundledContent() }
        // Re-applies the reminder schedule (e.g. after an app update or restored data).
        viewModelScope.launch { reminderScheduler.apply(preferencesRepository.preferences.first().reminderSettings) }
    }

    /** Null until the first read from disk completes. */
    val preferences: StateFlow<UserPreferences?> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(true) }
    }
}
