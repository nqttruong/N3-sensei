package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The kanji lessons of one JLPT level and which of them review takes new kanji from. */
data class KanjiLevelLessons(
    val level: String,
    val lessons: List<LessonSummary>,
    val selection: LessonSelection,
) {
    fun isChecked(lessonId: String): Boolean = selection.allows(lessonId)
    val checkedCount: Int get() = lessons.count { isChecked(it.id) }
}

data class KanjiReviewLessonsUiState(val levels: List<KanjiLevelLessons>)

/**
 * Cài đặt ôn tập → Bài Kanji: per JLPT level (N3, N2), the kanji lessons whose new kanji review
 * sessions introduce. Lessons come from the content (Học → Kanji); the choice is saved at once.
 * Leaving a lesson out only stops new kanji of it: progress and history are never touched.
 */
class KanjiReviewLessonsViewModel(
    private val preferences: UserPreferencesRepository,
    content: ContentRepository,
) : ViewModel() {

    private val lessons = content.observeLessons()

    val state: StateFlow<UiState<KanjiReviewLessonsUiState>> = combine(lessons, preferences.preferences) { lessons, prefs ->
        val groups = levelsOf(lessons).map { (level, list) -> KanjiLevelLessons(level, list, prefs.kanjiReviewLessons.of(level)) }
        if (groups.isEmpty()) UiState.Empty else UiState.Success(KanjiReviewLessonsUiState(groups))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val writes = Mutex()

    /** Checks or unchecks one lesson; every lesson of the level checked again becomes "all" (new lessons follow). */
    fun toggle(level: String, lessonId: String) = change(level) { ids, now ->
        val chosen = ids.filterTo(LinkedHashSet()) { now.allows(it) }
        val next = if (lessonId in chosen) chosen - lessonId else chosen + lessonId
        if (next == ids.toSet()) LessonSelection.All else LessonSelection.Only(next)
    }

    fun selectAll(level: String) = change(level) { _, _ -> LessonSelection.All }

    fun clearAll(level: String) = change(level) { _, _ -> LessonSelection.Only(emptySet()) }

    private fun change(level: String, next: (lessonIds: List<String>, now: LessonSelection) -> LessonSelection) {
        viewModelScope.launch {
            writes.withLock {
                val ids = levelsOf(lessons.first())[level].orEmpty().map { it.id }
                val current = preferences.preferences.first().kanjiReviewLessons
                val updated = current.with(level, next(ids, current.of(level)))
                if (updated != current) preferences.setKanjiReviewLessons(updated)
            }
        }
    }

    private companion object {
        /** Lessons with kanji by level, the course level first (N3, then N2), each in pack order. */
        fun levelsOf(lessons: List<LessonSummary>): Map<String, List<LessonSummary>> =
            lessons.filter { it.kanjiCount > 0 }
                .sortedWith(compareBy({ it.packId }, { it.number }))
                .groupBy { it.jlptLevel }
                .toSortedMap(compareBy<String> { it != KanjiReviewLessons.COURSE_LEVEL }.thenByDescending { it })
    }
}
