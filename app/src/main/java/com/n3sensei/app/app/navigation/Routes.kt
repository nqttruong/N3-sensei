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

@Serializable data object VocabularyRoute

@Serializable data class VocabularyDetailRoute(val vocabularyId: String)

@Serializable data object KanjiRoute

@Serializable data class KanjiDetailRoute(val kanjiId: String)

@Serializable data object GrammarRoute

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

@Serializable data object StatsRoute

@Serializable data object SettingsRoute
