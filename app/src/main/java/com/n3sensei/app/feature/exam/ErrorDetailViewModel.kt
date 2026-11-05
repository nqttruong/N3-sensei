package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.mistakes.ErrorReviewEntry
import com.n3sensei.app.domain.mistakes.MistakeAnalysisManager
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.QuestionMistakes
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.repository.ErrorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ErrorDetailUiState(
    val error: NotebookError,
    val reviews: List<ErrorReviewEntry>,
    /** Null while loading. */
    val mistakes: QuestionMistakes?,
)

/** One error of the notebook: its analysis, history, reviews, and the learner's flags and note. */
class ErrorDetailViewModel(
    private val errorId: Long,
    private val errors: ErrorRepository,
    private val mistakes: MistakeAnalysisManager,
) : ViewModel() {

    private val analysis = MutableStateFlow<QuestionMistakes?>(null)

    val state: StateFlow<UiState<ErrorDetailUiState>> =
        combine(errors.observeError(errorId), errors.observeReviewLog(errorId), analysis) { error, reviews, mistakes ->
            if (error == null) UiState.NotFound else UiState.Success(ErrorDetailUiState(error, reviews, mistakes))
        }
            .catch { emit(UiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch {
            val error = errors.observeError(errorId).first() ?: return@launch
            analysis.value = mistakes.questionMistakes(error.record.questionId)
        }
    }

    fun setImportant(important: Boolean) {
        viewModelScope.launch { errors.setImportant(errorId, important) }
    }

    fun saveNote(note: String) {
        viewModelScope.launch { errors.setNote(errorId, note) }
    }

    fun setUnderstood(understood: Boolean) {
        viewModelScope.launch { errors.setUnderstood(errorId, understood) }
    }

    fun practiceWords(refs: List<ItemRef>, then: () -> Unit) {
        viewModelScope.launch {
            mistakes.markForPractice(refs)
            then()
        }
    }
}
