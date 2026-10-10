package com.n3sensei.app.testing

import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    override val preferences = MutableStateFlow(initial)

    override suspend fun setOnboardingCompleted(completed: Boolean) = preferences.update { it.copy(onboardingCompleted = completed) }
    override suspend fun setThemeMode(mode: ThemeMode) = preferences.update { it.copy(themeMode = mode) }
    override suspend fun setShowFurigana(show: Boolean) = preferences.update { it.copy(showFurigana = show) }
}
