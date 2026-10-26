package com.n3sensei.app.app.navigation

import kotlinx.serialization.Serializable

// Every navigation route in the app is declared here, and only here.
// Each leaf route must have an AppScreen entry (enforced by tests).

@Serializable data object HomeRoute

/** Nested graph for the Learn tab; its start destination is [LearnHubRoute]. */
@Serializable data object LearnGraphRoute

@Serializable data object LearnHubRoute

@Serializable data object LessonListRoute

@Serializable data class LessonDetailRoute(val lessonId: String)

/** [status] pre-selects the learning-status filter (a LearningStatus name). */
@Serializable data class VocabularyRoute(val status: String? = null)

@Serializable data class VocabularyDetailRoute(val vocabularyId: String)

/** [status] pre-selects the learning-status filter (a LearningStatus name). */
@Serializable data class KanjiRoute(val status: String? = null)

@Serializable data class KanjiDetailRoute(val kanjiId: String)

/** The learner's edit of a kanji (kept apart from its pack). */
@Serializable data class KanjiEditRoute(val kanjiId: String)

/** Edit of a pack word (kept apart from its pack) or of one of the learner's own words. */
@Serializable data class VocabularyEditRoute(val vocabularyId: String)

/** New word for "Từ của tôi". */
@Serializable data object AddWordRoute

/** [status] pre-selects the learning-status filter (a LearningStatus name). */
@Serializable data class GrammarRoute(val status: String? = null)

@Serializable data class GrammarDetailRoute(val grammarId: String)

/** Practice one lesson's items, or marked items (optionally within [lessonId]). */
@Serializable data class PracticeRoute(val lessonId: String? = null, val markedOnly: Boolean = false)

/** Session result travels as arguments: it is not persisted anywhere. */
@Serializable
data class PracticeResultRoute(
    val lessonId: String?,
    val markedOnly: Boolean,
    val total: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
    val durationSeconds: Long,
)

@Serializable data object ReviewRoute

/** Scheduled review session; answers are saved to the schedule. */
@Serializable data object ReviewSessionRoute

@Serializable
data class ReviewResultRoute(
    val total: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
    val durationSeconds: Long,
)

@Serializable data object StatsRoute

@Serializable data object SettingsRoute

@Serializable data object ReviewSettingsRoute

@Serializable data object ReminderSettingsRoute
