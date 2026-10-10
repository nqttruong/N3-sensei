package com.n3sensei.app.domain.model

/** Names are persisted; never rename an entry. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showFurigana: Boolean = true,
)
