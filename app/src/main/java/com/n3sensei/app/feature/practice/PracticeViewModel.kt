package com.n3sensei.app.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.course.CourseBuilder
import com.n3sensei.app.domain.course.VocabularyGroups
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.practice.PracticeSession
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.SpacedRepetitionScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class PracticeUiState(
    /** Null when practising marked items across lessons. */
    val lessonTitle: String?,
    val session: PracticeSession,
    /** Set once the last card is rated; the screen then navigates to the result. */
    val result: PracticeResult? = null,
)

/**
 * Practice over one lesson's items, a "Bài học" lesson's items ([courseLesson]), or items
 * marked as needing attention (optionally within one lesson), in lesson order. Ratings are
 * tallied for the session result only and are not written to the review schedule.
 *
 * With [progressRepository] and [preferencesRepository], each answer button shows the
 * interval the scheduled review would give the item for that answer: the same
 * [SpacedRepetitionScheduler.preview] the review session uses, from the item's stored progress
 * and the current ladder. Computing it never writes anything.
 */
class PracticeViewModel(
    private val lessonId: String?,
    private val markedOnly: Boolean,
    private val contentRepository: ContentRepository,
    private val clock: Clock,
    private val progressRepository: ProgressRepository? = null,
    preferencesRepository: UserPreferencesRepository? = null,
    private val courseLesson: Int? = null,
    /** A vocabulary group of "Học → Từ vựng" ([VocabularyGroup.key][com.n3sensei.app.domain.course.VocabularyGroup.key]). */
    private val vocabularyGroup: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PracticeUiState>>(UiState.Loading)
    val state: StateFlow<UiState<PracticeUiState>> = _state.asStateFlow()

    /** Stored progress of the session's items (null = new item), read once when the session loads. */
    private val progress = MutableStateFlow<Map<ItemRef, LearningProgress?>>(emptyMap())

    /** Next review interval per answer for the current card; empty when it cannot be computed. */
    val hints: StateFlow<Map<ReviewRating, ReviewInterval>> = combine(
        _state,
        progress,
        preferencesRepository?.preferences ?: flowOf(null),
    ) { state, stored, prefs ->
        val item = (state as? UiState.Success)?.data?.session?.current
        if (item == null || prefs == null || progressRepository == null || item.ref !in stored) {
            emptyMap()
        } else {
            val scheduler = SpacedRepetitionScheduler(prefs.reviewSettings.ladder)
            ReviewRating.entries.associateWith { scheduler.preview(stored[item.ref], it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    private var startedAt: Instant = clock.instant()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                when {
                    courseLesson != null -> loadCourseLesson(courseLesson)
                    vocabularyGroup != null -> loadVocabularyGroup(vocabularyGroup)
                    else -> loadLesson()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    private suspend fun loadLesson(): UiState<PracticeUiState> {
        val lesson = lessonId?.let { contentRepository.observeLesson(it).first() }
        if (lessonId != null && lesson == null) return UiState.NotFound
        // "Từ của tôi" practises the learner's own words only, as its list shows them.
        val query = ItemQuery(lessonId = lessonId, markedOnly = markedOnly, userWordsOnly = lessonId == UserWords.LESSON_ID)
        val items = contentRepository.observeVocabularyItems(query).first().map { PracticeItem.VocabularyItem(it.item) } +
            contentRepository.observeKanjiItems(query).first().map { kanjiItem(it.item) } +
            contentRepository.observeGrammarItems(query).first().map { PracticeItem.GrammarItem(it.item) }
        return start(lesson?.title, items)
    }

    /** Kanji, then vocabulary, then grammar of the lesson, as on its screen. */
    private suspend fun loadCourseLesson(number: Int): UiState<PracticeUiState> {
        val lesson = CourseBuilder(contentRepository).observe().first().getOrNull(number - 1) ?: return UiState.NotFound
        val items = lesson.kanji.map { kanjiItem(it.item) } +
            lesson.words.map { PracticeItem.VocabularyItem(it.item) } +
            lesson.grammar.map { PracticeItem.GrammarItem(it.item) }
        return start(null, items)
    }

    /** The group's words in their order. */
    private suspend fun loadVocabularyGroup(key: String): UiState<PracticeUiState> {
        val group = VocabularyGroups.observe(contentRepository).first().firstOrNull { it.key == key } ?: return UiState.NotFound
        return start(null, group.words.map { PracticeItem.VocabularyItem(it.item) })
    }

    private suspend fun kanjiItem(kanji: Kanji) =
        PracticeItem.KanjiItem(kanji, contentRepository.getKanjiWords(kanji.id))

    private suspend fun start(title: String?, items: List<PracticeItem>): UiState<PracticeUiState> {
        startedAt = clock.instant()
        if (items.isEmpty()) return UiState.Empty
        progressRepository?.let { repo -> progress.value = items.associate { it.ref to repo.getProgress(it.ref) } }
        return UiState.Success(PracticeUiState(title, PracticeSession(items)))
    }

    fun revealAnswer() = updateSession { it.revealAnswer() }

    fun rate(rating: ReviewRating) = updateSession { it.rate(rating) }

    private fun updateSession(transform: (PracticeSession) -> PracticeSession) {
        _state.update { current ->
            if (current !is UiState.Success || current.data.result != null) return@update current
            val session = transform(current.data.session)
            val result = if (session.isFinished) {
                session.result(Duration.between(startedAt, clock.instant()).seconds)
            } else {
                null
            }
            UiState.Success(current.data.copy(session = session, result = result))
        }
    }
}
