package com.n3sensei.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.integrity.DatabaseRecoveryRepository
import com.n3sensei.app.domain.integrity.DatabaseResetNotice
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.repository.ContentImportRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide settings needed before any screen is shown: theme, onboarding, furigana. */
class MainViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    contentImportRepository: ContentImportRepository,
    reminderScheduler: ReminderScheduler,
    private val databaseRecovery: DatabaseRecoveryRepository,
) : ViewModel() {

    private val importRepository = contentImportRepository
    private val _bundledContentFailures = MutableStateFlow<List<String>>(emptyList())

    /**
     * Bundled packs that could not be installed (title, or file when unreadable). Empty when
     * everything is in place. Learner data is never touched by a failed install.
     */
    val bundledContentFailures: StateFlow<List<String>> = _bundledContentFailures.asStateFlow()

    /** Installs or upgrades the packs shipped with the app; a no-op when they are current. */
    fun installBundledContent() {
        viewModelScope.launch {
            val reports = try {
                importRepository.installBundledContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listOf(ImportReport(ImportOutcome.FAILED, packTitle = e.message))
            }
            _bundledContentFailures.value = reports.filter { it.outcome == ImportOutcome.FAILED }
                .map { it.packTitle ?: it.issues.firstOrNull()?.location ?: "?" }
        }
    }

    fun dismissBundledContentFailures() {
        _bundledContentFailures.value = emptyList()
    }

    /** Shown once after a corrupt database had to be recreated; see [DatabaseRecoveryRepository]. */
    val databaseResetNotice: StateFlow<DatabaseResetNotice?> = databaseRecovery.notice

    fun dismissDatabaseResetNotice() = databaseRecovery.dismiss()

    init {
        installBundledContent()
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
