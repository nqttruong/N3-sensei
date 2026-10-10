package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences,
    val sources: List<ContentSource>,
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    contentRepository: ContentRepository,
) : ViewModel() {
    val state: StateFlow<UiState<SettingsUiState>> = combine(
        preferencesRepository.preferences,
        contentRepository.observeSources(),
    ) { prefs, sources -> UiState.Success(SettingsUiState(prefs, sources)) }
        .onStart<UiState<SettingsUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setShowFurigana(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowFurigana(show) }
    }

    /** The app root shows onboarding again as soon as this is saved. */
    fun replayOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(false) }
    }
}
