package com.n3sensei.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide settings needed before any screen is shown: theme, onboarding, furigana. */
class MainViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    /** Null until the first read from disk completes. */
    val preferences: StateFlow<UserPreferences?> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(true) }
    }
}
