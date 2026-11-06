package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.importing.ImportCheck
import com.n3sensei.app.domain.importing.ImportPreview
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.model.ContentPackInfo
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.ContentImportRepository
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.TranslationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val preferences: UserPreferences,
    /** Installed packs, without the learner's own "Từ của tôi" (not a pack they installed). */
    val packs: List<ContentPackInfo>,
)

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Running : ImportUiState

    /** A valid pack that would replace the installed version: waits for the user's confirmation. */
    data class Confirm(val preview: ImportPreview) : ImportUiState
    data class Finished(val report: ImportReport) : ImportUiState
}

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    contentRepository: ContentRepository,
    private val importRepository: ContentImportRepository,
) : ViewModel() {
    val state: StateFlow<UiState<SettingsUiState>> = combine(
        preferencesRepository.preferences,
        contentRepository.observePacks(),
    ) { prefs, packs -> UiState.Success(SettingsUiState(prefs, packs.filter { it.id != UserWords.PACK_ID })) }
        .onStart<UiState<SettingsUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setShowFurigana(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowFurigana(show) }
    }

    /** One write at a time, so two quick taps never undo each other. */
    private val quickTranslateWrites = Mutex()

    private fun updateQuickTranslate(change: (QuickTranslateSettings) -> QuickTranslateSettings) {
        viewModelScope.launch {
            quickTranslateWrites.withLock {
                val current = preferencesRepository.preferences.first().quickTranslate
                preferencesRepository.setQuickTranslateSettings(change(current))
            }
        }
    }

    /** Also shows or hides "Dịch nhanh" in other apps' text selection menu (applied by the app). */
    fun setQuickTranslateEnabled(enabled: Boolean) = updateQuickTranslate { it.copy(enabled = enabled) }

    fun setQuickTranslateMode(mode: TranslationMode) = updateQuickTranslate { it.copy(mode = mode) }

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    /**
     * Checks the document the user picked. A new pack is imported straight away; a pack that
     * replaces the installed version (and may remove items) waits for [confirmImport].
     * The report stays until [dismissImportReport].
     */
    fun importDocument(documentUri: String) {
        if (_importState.value == ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            _importState.value = when (val check = importRepository.checkDocument(documentUri)) {
                is ImportCheck.Failed -> ImportUiState.Finished(check.report)
                is ImportCheck.Ready ->
                    if (check.preview.replacesInstalled) {
                        ImportUiState.Confirm(check.preview)
                    } else {
                        ImportUiState.Finished(importRepository.importChecked(check.preview))
                    }
            }
        }
    }

    fun confirmImport() {
        val confirm = _importState.value as? ImportUiState.Confirm ?: return
        _importState.value = ImportUiState.Running
        viewModelScope.launch { _importState.value = ImportUiState.Finished(importRepository.importChecked(confirm.preview)) }
    }

    /** Closes the report, or cancels a pending replacement (nothing is written). */
    fun dismissImportReport() {
        if (_importState.value != ImportUiState.Running) _importState.value = ImportUiState.Idle
    }

    /** The app root shows onboarding again as soon as this is saved. */
    fun replayOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(false) }
    }
}
