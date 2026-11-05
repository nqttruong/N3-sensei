package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.mistakes.ErrorReviewSessionManager
import com.n3sensei.app.domain.mistakes.ReviewCard
import com.n3sensei.app.domain.repository.ReviewResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ErrorReviewSessionUiState(
    val cards: List<ReviewCard>,
    /** True for today's due errors, false for one error reviewed on demand. */
    val dueSession: Boolean,
    val index: Int = 0,
    /** The answer being given to the current card. */
    val answer: AnswerInput? = null,
    /** Results by card index, once checked. */
    val results: Map<Int, ReviewResult> = emptyMap(),
    val finished: Boolean = false,
) {
    val card: ReviewCard get() = cards[index]
    val result: ReviewResult? get() = results[index]
}

/**
 * A review session: today's due errors (priority first), or the one error of [errorId]. Each
 * answer is graded and saved at once; the schedule and the question picking are in the domain.
 */
class ErrorReviewSessionViewModel(
    private val errorId: Long?,
    private val sessions: ErrorReviewSessionManager,
    private val skill: ExamSkill? = null,
    private val limit: Int? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ErrorReviewSessionUiState>>(UiState.Loading)
    val state: StateFlow<UiState<ErrorReviewSessionUiState>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val cards = errorId?.let { sessions.singleSession(it) } ?: sessions.dueSession(skill, limit)
                if (cards.isEmpty()) UiState.Empty else UiState.Success(ErrorReviewSessionUiState(cards, dueSession = errorId == null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    fun select(answer: AnswerInput) = update { if (it.result == null) it.copy(answer = answer) else it }

    /** Grades the current answer once; tapping again changes nothing. */
    fun check() {
        val s = current() ?: return
        val answer = s.answer ?: return
        if (s.result != null) return
        val index = s.index
        viewModelScope.launch {
            val result = sessions.answer(s.card, answer) ?: return@launch
            update { if (it.index == index) it.copy(results = it.results + (index to result)) else it }
        }
    }

    fun next() = update {
        when {
            it.result == null -> it
            it.index < it.cards.lastIndex -> it.copy(index = it.index + 1, answer = null)
            else -> it.copy(finished = true)
        }
    }

    private fun current() = (_state.value as? UiState.Success)?.data

    private fun update(change: (ErrorReviewSessionUiState) -> ErrorReviewSessionUiState) {
        _state.update { s -> if (s is UiState.Success) UiState.Success(change(s.data)) else s }
    }
}
