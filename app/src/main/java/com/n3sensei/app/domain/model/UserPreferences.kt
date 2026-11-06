package com.n3sensei.app.domain.model

import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.widget.WidgetSettings
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
    val widgetSettings: WidgetSettings = WidgetSettings(),
    /** Time the learner plans to study a day, in minutes: the daily plan never goes over it. */
    val dailyStudyMinutes: Int = DEFAULT_DAILY_STUDY_MINUTES,
    val quickTranslate: QuickTranslateSettings = QuickTranslateSettings(),
) {
    companion object {
        const val DEFAULT_DAILY_STUDY_MINUTES = 20
    }
}
