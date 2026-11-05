package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.widget.WidgetSettings
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setShowFurigana(show: Boolean)

    /** Applies to each item's next answer; due times already scheduled are kept. */
    suspend fun setReviewSettings(settings: ReviewSettings)

    suspend fun setReminderSettings(settings: ReminderSettings)

    suspend fun setWidgetSettings(settings: WidgetSettings)
}
