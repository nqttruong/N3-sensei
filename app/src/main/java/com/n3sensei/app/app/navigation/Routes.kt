package com.n3sensei.app.app.navigation

import kotlinx.serialization.Serializable

// Every navigation route in the app is declared here, and only here.
// Each leaf route must have an AppScreen entry (enforced by tests).

@Serializable data object HomeRoute

/** Nested graph for the Learn tab; its start destination is [LearnHubRoute]. */
@Serializable data object LearnGraphRoute

@Serializable data object LearnHubRoute

@Serializable data object LessonListRoute

/** One "Bài học" lesson: [number] is its position in the course (1-based). */
@Serializable data class LessonDetailRoute(val number: Int)

/**
 * [status] pre-selects the learning-status filter (a LearningStatus name), [lessonId] the
 * lesson filter (e.g. "Từ của tôi").
 */
@Serializable data class VocabularyRoute(val status: String? = null, val lessonId: String? = null)

/** "Học → Từ vựng": the vocabulary in groups of at most 30 words. */
@Serializable data object VocabularyGroupsRoute

/** One vocabulary group, by [VocabularyGroup.key][com.n3sensei.app.domain.course.VocabularyGroup.key]. */
@Serializable data class VocabularyGroupRoute(val key: String)

/** "Thi thử JLPT": the exam sets in the app, with filters. */
@Serializable data object ExamListRoute

/** One exam set before it is taken: facts, rules, start or continue. */
@Serializable data class ExamIntroRoute(val examSetId: String)

/** Taking an exam: a new attempt, or [attemptId] continued. */
@Serializable data class ExamSessionRoute(val examSetId: String, val attemptId: String? = null)

/** Result of a submitted attempt (read from Room: opens again after the app was closed). */
@Serializable data class ExamResultRoute(val attemptId: String)

/** Every question of a submitted attempt; [filter] a ReviewFilter name, [position] the question to open at. */
@Serializable data class ExamReviewRoute(val attemptId: String, val filter: String = "ALL", val position: Int? = null)

/** The missed questions of a submitted attempt, analysed (category, evidence, fixes). */
@Serializable data class MistakeAnalysisRoute(val attemptId: String)

/** "Sổ lỗi sai": every question missed in submitted exams, with filters and search; [skill] pre-selects one. */
@Serializable data class ErrorNotebookRoute(val skill: String? = null)

/** "Phân tích JLPT": exam figures, error analysis, progress and today's plan. */
@Serializable data object ExamDashboardRoute

/** One skill of the exams in detail (an ExamSkill name). */
@Serializable data class SkillDetailRoute(val skill: String)

/** One error of the notebook. */
@Serializable data class ErrorDetailRoute(val errorId: Long)

/**
 * Review of errors: today's due ones (only [skill]'s, at most [limit], for a task of the daily
 * plan), or only [errorId] ("Ôn ngay").
 */
@Serializable data class ErrorReviewRoute(val errorId: Long? = null, val skill: String? = null, val limit: Int? = null)

/** Weak points over all submitted exams. */
@Serializable data object WeaknessRoute

/** "Từ của tôi": only the words the learner added. */
@Serializable data object MyWordsRoute

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

/** The learner's edit of a grammar point (kept apart from its pack). */
@Serializable data class GrammarEditRoute(val grammarId: String)

/** Practice one lesson's items, or marked items (optionally within [lessonId]). */
/** [courseLesson]: the items of a "Bài học" lesson (by number) instead of a pack lesson. */
@Serializable data class PracticeRoute(
    val lessonId: String? = null,
    val markedOnly: Boolean = false,
    val courseLesson: Int? = null,
    /** Practice one vocabulary group of "Học → Từ vựng". */
    val vocabularyGroup: String? = null,
)

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
    val courseLesson: Int? = null,
    val vocabularyGroup: String? = null,
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
@Serializable data object WidgetSettingsRoute
