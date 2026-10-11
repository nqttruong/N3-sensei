package com.n3sensei.app.domain.model

import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.ReviewSettings

/** Names are persisted; never rename an entry. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showFurigana: Boolean = true,
    val reviewSettings: ReviewSettings = ReviewSettings(),
    val reminderSettings: ReminderSettings = ReminderSettings(),
)
