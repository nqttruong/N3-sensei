package com.n3sensei.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.ReminderContent
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.reminder.ReminderLessons
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.widget.WidgetSettings
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.translate.QuickTranslateSettings
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
        /** The single shared cap of older versions: read once as the vocabulary cap, never written now. */
        val NEW_PER_DAY = intPreferencesKey("new_per_day")
        val NEW_VOCABULARY_PER_DAY = intPreferencesKey("new_vocabulary_per_day")
        val NEW_KANJI_PER_DAY = intPreferencesKey("new_kanji_per_day")
        val NEW_GRAMMAR_PER_DAY = intPreferencesKey("new_grammar_per_day")
        val REVIEWS_PER_DAY = intPreferencesKey("reviews_per_day")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_START_HOUR = intPreferencesKey("reminder_start_hour")
        val REMINDER_END_HOUR = intPreferencesKey("reminder_end_hour")
        val REMINDER_FREQUENCY = stringPreferencesKey("reminder_frequency")
        val REMINDER_SHOW_READING = booleanPreferencesKey("reminder_show_reading")
        val REMINDER_SHOW_MEANING = booleanPreferencesKey("reminder_show_meaning")
        val REMINDER_MAX_PER_DAY = intPreferencesKey("reminder_max_per_day")
        val REMINDER_CONTENT = stringPreferencesKey("reminder_content")
        val REMINDER_REVIEW_ONLY = booleanPreferencesKey("reminder_review_only")
        val REMINDER_ERROR_REVIEWS = booleanPreferencesKey("reminder_error_reviews")

        /** Lesson keys per kind; absent = every lesson (LessonSelection.All), present (maybe empty) = only these. */
        val REMINDER_LESSONS_VOCABULARY = stringSetPreferencesKey("reminder_lessons_vocabulary")
        val REMINDER_LESSONS_KANJI = stringSetPreferencesKey("reminder_lessons_kanji")
        val REMINDER_LESSONS_GRAMMAR = stringSetPreferencesKey("reminder_lessons_grammar")
        val WIDGET_MODE = stringPreferencesKey("widget_mode")
        val DAILY_STUDY_MINUTES = intPreferencesKey("daily_study_minutes")
        val WIDGET_LESSON = intPreferencesKey("widget_lesson")
        val WIDGET_SHOW_FURIGANA = booleanPreferencesKey("widget_show_furigana")
        val WIDGET_SHOW_MEANING = booleanPreferencesKey("widget_show_meaning")
        val WIDGET_TAP = stringPreferencesKey("widget_tap")
        val REMINDER_LOCK_SCREEN = stringPreferencesKey("reminder_lock_screen")
        val QUICK_TRANSLATE_ENABLED = booleanPreferencesKey("quick_translate_enabled")
        val QUICK_TRANSLATE_MODE = stringPreferencesKey("quick_translate_mode")
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
                kanjiReviewLessons = readKanjiReviewLessons(prefs),
                reminderSettings = readReminder(prefs),
                widgetSettings = readWidget(prefs),
                dailyStudyMinutes = prefs[Keys.DAILY_STUDY_MINUTES]?.takeIf { it in 1..600 } ?: UserPreferences.DEFAULT_DAILY_STUDY_MINUTES,
                quickTranslate = readQuickTranslate(prefs),
            )
        }

    /**
     * Out-of-range stored values (e.g. edited by hand) fall back to the defaults. Settings saved
     * before per-type caps have only the shared cap: it becomes the vocabulary cap (clamped to the
     * new range), and kanji and grammar start at their defaults.
     */
    private fun readLimits(prefs: Preferences): DailyLimits {
        val perType = 0..DailyLimits.MAX_NEW_PER_TYPE
        val legacy = prefs[Keys.NEW_PER_DAY]?.takeIf { it in 0..DailyLimits.LEGACY_MAX_NEW }?.coerceAtMost(DailyLimits.MAX_NEW_PER_TYPE)
        val vocabulary = prefs[Keys.NEW_VOCABULARY_PER_DAY]?.takeIf { it in perType } ?: legacy ?: DailyLimits.DEFAULT_NEW_VOCABULARY
        val kanji = prefs[Keys.NEW_KANJI_PER_DAY]?.takeIf { it in perType } ?: DailyLimits.DEFAULT_NEW_KANJI
        val grammar = prefs[Keys.NEW_GRAMMAR_PER_DAY]?.takeIf { it in perType } ?: DailyLimits.DEFAULT_NEW_GRAMMAR
        val reviews = prefs[Keys.REVIEWS_PER_DAY]?.takeIf { it in 1..DailyLimits.MAX_REVIEWS } ?: DailyLimits.DEFAULT_REVIEWS
        return DailyLimits(vocabulary, kanji, grammar, reviews)
    }

    /**
     * One key per JLPT level (`review_kanji_lessons_N2`…): absent = that level's default
     * ([KanjiReviewLessons.default]), [ALL_LESSONS] = every lesson, otherwise the lesson ids.
     */
    private fun readKanjiReviewLessons(prefs: Preferences): KanjiReviewLessons = KanjiReviewLessons(
        prefs.asMap().entries.mapNotNull { (key, value) ->
            val level = key.name.removePrefix(KANJI_REVIEW_LESSONS_PREFIX).takeIf { key.name.startsWith(KANJI_REVIEW_LESSONS_PREFIX) && it.isNotEmpty() }
            val keys = (value as? Set<*>)?.filterIsInstance<String>()?.toSet()
            if (level == null || keys == null) null else level to (if (ALL_LESSONS in keys) LessonSelection.All else LessonSelection.Only(keys))
        }.toMap(),
    )

    override suspend fun setKanjiReviewLessons(lessons: KanjiReviewLessons) {
        dataStore.edit { prefs ->
            lessons.byLevel.forEach { (level, selection) ->
                val key = stringSetPreferencesKey(KANJI_REVIEW_LESSONS_PREFIX + level)
                when {
                    selection == KanjiReviewLessons.default(level) -> prefs.remove(key)
                    selection is LessonSelection.Only -> prefs[key] = selection.keys
                    else -> prefs[key] = setOf(ALL_LESSONS)
                }
            }
        }
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
            maxPerDay = prefs[Keys.REMINDER_MAX_PER_DAY]?.takeIf { it in 0..MAX_REMINDERS_PER_DAY } ?: d.maxPerDay,
            content = prefs[Keys.REMINDER_CONTENT]?.split(',')
                ?.mapNotNull { name -> ReminderContent.entries.firstOrNull { it.name == name } }
                ?.toSet()?.ifEmpty { null } ?: d.content,
            reviewOnly = prefs[Keys.REMINDER_REVIEW_ONLY] ?: d.reviewOnly,
            errorReviews = prefs[Keys.REMINDER_ERROR_REVIEWS] ?: d.errorReviews,
            lessons = ReminderLessons(
                vocabulary = selection(prefs[Keys.REMINDER_LESSONS_VOCABULARY]),
                kanji = selection(prefs[Keys.REMINDER_LESSONS_KANJI]),
                grammar = selection(prefs[Keys.REMINDER_LESSONS_GRAMMAR]),
            ),
        )
    }

    private fun selection(keys: Set<String>?): LessonSelection = keys?.let { LessonSelection.Only(it) } ?: LessonSelection.All

    private fun write(prefs: MutablePreferences, key: Preferences.Key<Set<String>>, selection: LessonSelection) {
        when (selection) {
            LessonSelection.All -> prefs.remove(key)
            is LessonSelection.Only -> prefs[key] = selection.keys
        }
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
            it[Keys.REMINDER_MAX_PER_DAY] = settings.maxPerDay
            it[Keys.REMINDER_CONTENT] = settings.content.sortedBy { c -> c.ordinal }.joinToString(",") { c -> c.name }
            it[Keys.REMINDER_REVIEW_ONLY] = settings.reviewOnly
            it[Keys.REMINDER_ERROR_REVIEWS] = settings.errorReviews
            write(it, Keys.REMINDER_LESSONS_VOCABULARY, settings.lessons.vocabulary)
            write(it, Keys.REMINDER_LESSONS_KANJI, settings.lessons.kanji)
            write(it, Keys.REMINDER_LESSONS_GRAMMAR, settings.lessons.grammar)
        }
    }

    private fun readWidget(prefs: Preferences): WidgetSettings {
        val d = WidgetSettings()
        return WidgetSettings(
            mode = enumOrDefault(prefs[Keys.WIDGET_MODE], d.mode),
            lesson = prefs[Keys.WIDGET_LESSON]?.takeIf { it >= 1 } ?: d.lesson,
            showFurigana = prefs[Keys.WIDGET_SHOW_FURIGANA] ?: d.showFurigana,
            showMeaning = prefs[Keys.WIDGET_SHOW_MEANING] ?: d.showMeaning,
            tap = enumOrDefault(prefs[Keys.WIDGET_TAP], d.tap),
        )
    }

    override suspend fun setDailyStudyMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DAILY_STUDY_MINUTES] = minutes.coerceIn(1, 600) }
    }

    override suspend fun setQuickTranslateSettings(settings: QuickTranslateSettings) {
        dataStore.edit {
            it[Keys.QUICK_TRANSLATE_ENABLED] = settings.enabled
            it[Keys.QUICK_TRANSLATE_MODE] = settings.mode.name
        }
    }

    /** Absent or unknown values (an older app, a hand-edited file): the defaults (on, both languages). */
    private fun readQuickTranslate(prefs: Preferences): QuickTranslateSettings {
        val d = QuickTranslateSettings()
        return QuickTranslateSettings(
            enabled = prefs[Keys.QUICK_TRANSLATE_ENABLED] ?: d.enabled,
            mode = enumOrDefault(prefs[Keys.QUICK_TRANSLATE_MODE], d.mode),
        )
    }

    override suspend fun setWidgetSettings(settings: WidgetSettings) {
        dataStore.edit {
            it[Keys.WIDGET_MODE] = settings.mode.name
            it[Keys.WIDGET_LESSON] = settings.lesson
            it[Keys.WIDGET_SHOW_FURIGANA] = settings.showFurigana
            it[Keys.WIDGET_SHOW_MEANING] = settings.showMeaning
            it[Keys.WIDGET_TAP] = settings.tap.name
        }
    }

    override suspend fun setReviewSettings(settings: ReviewSettings) {
        dataStore.edit {
            it[Keys.REVIEW_INTERVALS] = settings.ladder.encode()
            it[Keys.NEW_VOCABULARY_PER_DAY] = settings.limits.newVocabulary
            it[Keys.NEW_KANJI_PER_DAY] = settings.limits.newKanji
            it[Keys.NEW_GRAMMAR_PER_DAY] = settings.limits.newGrammar
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

/** Stored values above this (e.g. edited by hand) fall back to the default. */
private const val MAX_REMINDERS_PER_DAY = 100

/** Key of a JLPT level's kanji review lessons: this prefix + the level ("N2"). */
private const val KANJI_REVIEW_LESSONS_PREFIX = "review_kanji_lessons_"

/** Stored instead of lesson ids for "every lesson of the level" (ids never contain it). */
private const val ALL_LESSONS = "*"
