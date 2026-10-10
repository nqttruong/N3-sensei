package com.n3sensei.app.feature.settings

import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
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
    // Lazy: Dispatchers.Main is only replaced once the rule runs, after field initialisation.
    private val viewModel by lazy { SettingsViewModel(preferences, FakeContentRepository()) }

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
}
