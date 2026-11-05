package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.mistakes.AttemptMistakes
import com.n3sensei.app.domain.mistakes.MistakeAnalysisManager
import com.n3sensei.app.domain.mistakes.WeaknessOverview
import com.n3sensei.app.domain.model.ItemRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads [load] into a [UiState]; shared by the two mistake screens. */
abstract class MistakeScreenViewModel<T>(protected val mistakes: MistakeAnalysisManager) : ViewModel() {

    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    protected abstract suspend fun fetch(): T?

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                fetch()?.let { UiState.Success(it) } ?: UiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    /** Marks the words, then [then] (opening the practice of marked items). */
    fun practiceWords(refs: List<ItemRef>, then: () -> Unit) {
        viewModelScope.launch {
            mistakes.markForPractice(refs)
            then()
        }
    }
}

/** The missed questions of one submitted attempt, analysed. */
class MistakeAnalysisViewModel(private val attemptId: String, mistakes: MistakeAnalysisManager) :
    MistakeScreenViewModel<AttemptMistakes>(mistakes) {
    init {
        load()
    }

    override suspend fun fetch(): AttemptMistakes? = mistakes.analyzeAttempt(attemptId)
}

/** Weak points over all submitted exams. */
class WeaknessViewModel(mistakes: MistakeAnalysisManager) : MistakeScreenViewModel<WeaknessOverview>(mistakes) {
    init {
        load()
    }

    override suspend fun fetch(): WeaknessOverview = mistakes.weakness()
}
