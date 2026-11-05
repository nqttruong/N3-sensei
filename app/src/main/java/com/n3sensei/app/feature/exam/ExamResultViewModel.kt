package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.ExamResultManager
import com.n3sensei.app.domain.exam.ExamResultOverview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The result of one submitted attempt, read from what was stored at its submit. */
class ExamResultViewModel(
    private val attemptId: String,
    private val results: ExamResultManager,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ExamResultOverview>>(UiState.Loading)
    val state: StateFlow<UiState<ExamResultOverview>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                results.overview(attemptId)?.let { UiState.Success(it) } ?: UiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }
}
