package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The kanji lessons review sessions take NEW kanji from, chosen per JLPT level of the lesson
 * (Học → Kanji lists them by level, each pack lesson as it is).
 *
 * Only new kanji are concerned: a kanji that already has progress keeps its schedule, falls due
 * and is reviewed whatever lessons are chosen, and leaving a lesson out never deletes or resets
 * anything. Defaults: the course level (N3) takes every lesson, as before lesson choice existed;
 * any other level (N2) takes none until lessons are chosen.
 */
data class KanjiReviewLessons(
    /** Choices made; a level absent here has its [default]. */
    val byLevel: Map<String, LessonSelection> = emptyMap(),
) {
    fun of(level: String): LessonSelection = byLevel[level] ?: default(level)

    fun with(level: String, selection: LessonSelection): KanjiReviewLessons = copy(byLevel = byLevel + (level to selection))

    /** Ids of the lessons (of [lessons], those with kanji) whose new kanji may be introduced. */
    fun allowedLessons(lessons: List<LessonSummary>): Set<String> =
        lessons.filter { it.kanjiCount > 0 && of(it.jlptLevel).allows(it.id) }.mapTo(LinkedHashSet()) { it.id }

    companion object {
        /** Level of Học → Bài học and of the kanji the app had before other levels. */
        const val COURSE_LEVEL = "N3"

        fun default(level: String): LessonSelection =
            if (level == COURSE_LEVEL) LessonSelection.All else LessonSelection.Only(emptySet())

        /** The lessons review takes new kanji from, following the content and the learner's choice. */
        fun observeAllowedLessons(content: ContentRepository, preferences: UserPreferencesRepository): Flow<Set<String>> =
            combine(content.observeLessons(), preferences.preferences) { lessons, prefs ->
                prefs.kanjiReviewLessons.allowedLessons(lessons)
            }.distinctUntilChanged()
    }
}
