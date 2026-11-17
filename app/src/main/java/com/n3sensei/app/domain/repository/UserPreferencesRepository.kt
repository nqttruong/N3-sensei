package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.widget.WidgetSettings
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setShowFurigana(show: Boolean)

    /** Applies to each item's next answer; due times already scheduled are kept. */
    suspend fun setReviewSettings(settings: ReviewSettings)

    /**
     * The kanji lessons review sessions take new kanji from. Saved apart from [setReviewSettings],
     * so saving the intervals and limits never changes the lessons chosen.
     */
    suspend fun setKanjiReviewLessons(lessons: KanjiReviewLessons)

    suspend fun setReminderSettings(settings: ReminderSettings)

    suspend fun setWidgetSettings(settings: WidgetSettings)

    suspend fun setDailyStudyMinutes(minutes: Int)

    suspend fun setQuickTranslateSettings(settings: QuickTranslateSettings)
}
