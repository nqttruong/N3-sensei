package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.model.ContentPackInfo
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.ContentImportRepository
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences,
    val sources: List<ContentSource>,
    val packs: List<ContentPackInfo>,
)

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Running : ImportUiState
    data class Finished(val report: ImportReport) : ImportUiState
}

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    contentRepository: ContentRepository,
    private val importRepository: ContentImportRepository,
) : ViewModel() {
    val state: StateFlow<UiState<SettingsUiState>> = combine(
        preferencesRepository.preferences,
        contentRepository.observeSources(),
        contentRepository.observePacks(),
    ) { prefs, sources, packs -> UiState.Success(SettingsUiState(prefs, sources, packs)) }
        .onStart<UiState<SettingsUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setShowFurigana(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowFurigana(show) }
    }

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    /** Imports the document the user picked; the report stays until [dismissImportReport]. */
    fun importDocument(documentUri: String) {
        if (_importState.value == ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            _importState.value = ImportUiState.Finished(importRepository.importDocument(documentUri))
        }
    }

    fun dismissImportReport() {
        _importState.value = ImportUiState.Idle
    }

    /** The app root shows onboarding again as soon as this is saved. */
    fun replayOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(false) }
    }
}
