package com.n3sensei.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** App settings in Preferences DataStore (small key-value data; not stored in Room). */
class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_FURIGANA = booleanPreferencesKey("show_furigana")
        val REVIEW_INTERVALS = stringPreferencesKey("review_intervals")
        val NEW_PER_DAY = intPreferencesKey("new_per_day")
        val REVIEWS_PER_DAY = intPreferencesKey("reviews_per_day")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_START_HOUR = intPreferencesKey("reminder_start_hour")
        val REMINDER_END_HOUR = intPreferencesKey("reminder_end_hour")
        val REMINDER_FREQUENCY = stringPreferencesKey("reminder_frequency")
        val REMINDER_SHOW_READING = booleanPreferencesKey("reminder_show_reading")
        val REMINDER_SHOW_MEANING = booleanPreferencesKey("reminder_show_meaning")
        val REMINDER_LOCK_SCREEN = stringPreferencesKey("reminder_lock_screen")
    }

    private val defaults = UserPreferences()

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserPreferences(
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: defaults.themeMode,
                showFurigana = prefs[Keys.SHOW_FURIGANA] ?: defaults.showFurigana,
                reviewSettings = ReviewSettings(
                    ladder = ReviewLadder.decodeOrDefault(prefs[Keys.REVIEW_INTERVALS]),
                    limits = readLimits(prefs),
                ),
                reminderSettings = readReminder(prefs),
            )
        }

    /** Out-of-range stored values (e.g. edited by hand) fall back to the defaults. */
    private fun readLimits(prefs: Preferences): DailyLimits {
        val newPerDay = prefs[Keys.NEW_PER_DAY]?.takeIf { it in 0..DailyLimits.MAX_NEW } ?: DailyLimits.DEFAULT_NEW
        val reviews = prefs[Keys.REVIEWS_PER_DAY]?.takeIf { it in 1..DailyLimits.MAX_REVIEWS } ?: DailyLimits.DEFAULT_REVIEWS
        return DailyLimits(newPerDay, reviews)
    }

    private fun readReminder(prefs: Preferences): ReminderSettings {
        val d = ReminderSettings()
        return ReminderSettings(
            enabled = prefs[Keys.REMINDER_ENABLED] ?: d.enabled,
            startHour = prefs[Keys.REMINDER_START_HOUR]?.takeIf { it in 0..23 } ?: d.startHour,
            endHour = prefs[Keys.REMINDER_END_HOUR]?.takeIf { it in 0..23 } ?: d.endHour,
            frequency = enumOrDefault(prefs[Keys.REMINDER_FREQUENCY], d.frequency),
            showReading = prefs[Keys.REMINDER_SHOW_READING] ?: d.showReading,
            showMeaning = prefs[Keys.REMINDER_SHOW_MEANING] ?: d.showMeaning,
            lockScreen = enumOrDefault(prefs[Keys.REMINDER_LOCK_SCREEN], d.lockScreen),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    override suspend fun setReminderSettings(settings: ReminderSettings) {
        dataStore.edit {
            it[Keys.REMINDER_ENABLED] = settings.enabled
            it[Keys.REMINDER_START_HOUR] = settings.startHour
            it[Keys.REMINDER_END_HOUR] = settings.endHour
            it[Keys.REMINDER_FREQUENCY] = settings.frequency.name
            it[Keys.REMINDER_SHOW_READING] = settings.showReading
            it[Keys.REMINDER_SHOW_MEANING] = settings.showMeaning
            it[Keys.REMINDER_LOCK_SCREEN] = settings.lockScreen.name
        }
    }

    override suspend fun setReviewSettings(settings: ReviewSettings) {
        dataStore.edit {
            it[Keys.REVIEW_INTERVALS] = settings.ladder.encode()
            it[Keys.NEW_PER_DAY] = settings.limits.newPerDay
            it[Keys.REVIEWS_PER_DAY] = settings.limits.reviewsPerDay
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setShowFurigana(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_FURIGANA] = show }
    }
}
