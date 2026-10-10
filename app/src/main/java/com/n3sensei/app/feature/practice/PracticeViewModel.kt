package com.n3sensei.app.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.practice.PracticeSession
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class PracticeUiState(
    val lessonTitle: String,
    val session: PracticeSession,
    /** Set once the last card is rated; the screen then navigates to the result. */
    val result: PracticeResult? = null,
)

/**
 * Practice over one lesson's items in lesson order. Ratings are tallied for the session result
 * only and are not written to the review schedule.
 */
class PracticeViewModel(
    private val lessonId: String,
    private val contentRepository: ContentRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PracticeUiState>>(UiState.Loading)
    val state: StateFlow<UiState<PracticeUiState>> = _state.asStateFlow()

    private var startedAt: Instant = clock.instant()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                val lesson = contentRepository.observeLesson(lessonId).first()
                if (lesson == null) {
                    UiState.NotFound
                } else {
                    val items = contentRepository.observeVocabulary(lessonId).first().map { PracticeItem.VocabularyItem(it) } +
                        contentRepository.observeKanji(lessonId).first().map { PracticeItem.KanjiItem(it) } +
                        contentRepository.observeGrammar(lessonId).first().map { PracticeItem.GrammarItem(it) }
                    startedAt = clock.instant()
                    if (items.isEmpty()) UiState.Empty else UiState.Success(PracticeUiState(lesson.title, PracticeSession(items)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
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
