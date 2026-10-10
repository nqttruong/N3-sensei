package com.n3sensei.app.feature.settings

import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.FakeContentImportRepository
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private val importRepository = FakeContentImportRepository()
    // Lazy: Dispatchers.Main is only replaced once the rule runs, after field initialisation.
    private val viewModel by lazy { SettingsViewModel(preferences, FakeContentRepository(), importRepository) }

    @Test
    fun changesArePersistedThroughRepository() {
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setShowFurigana(false)
        viewModel.replayOnboarding()

        assertEquals(
            UserPreferences(onboardingCompleted = false, themeMode = ThemeMode.DARK, showFurigana = false),
            preferences.preferences.value,
        )
    }

    @Test
    fun importDocument_showsReportUntilDismissed() {
        viewModel.importDocument("content://pack.json")

        assertEquals(listOf("content://pack.json"), importRepository.importedUris)
        assertEquals(ImportUiState.Finished(importRepository.nextReport), viewModel.importState.value)

        viewModel.dismissImportReport()
        assertEquals(ImportUiState.Idle, viewModel.importState.value)
    }
}
