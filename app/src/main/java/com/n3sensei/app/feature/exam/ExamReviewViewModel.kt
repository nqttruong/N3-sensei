package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.ExamReport
import com.n3sensei.app.domain.exam.ExamResultManager
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.ResultItem
import com.n3sensei.app.domain.repository.ErrorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExamReviewUiState(
    val report: ExamReport,
    val filter: ReviewFilter,
    val items: List<ResultItem>,
)

/** Every question of a submitted attempt with the answer given, the right answer and the explanation. */
class ExamReviewViewModel(
    private val attemptId: String,
    initialFilter: ReviewFilter,
    private val results: ExamResultManager,
    private val errors: ErrorRepository,
) : ViewModel() {

    private val report = MutableStateFlow<UiState<ExamReport>>(UiState.Loading)
    private val filter = MutableStateFlow(initialFilter)

    val state: StateFlow<UiState<ExamReviewUiState>> = combine(report, filter) { report, filter ->
        when (report) {
            is UiState.Success -> UiState.Success(ExamReviewUiState(report.data, filter, report.data.items.filter { it.matches(filter) }))
            UiState.Loading -> UiState.Loading
            UiState.Empty -> UiState.Empty
            UiState.NotFound -> UiState.NotFound
            is UiState.Error -> UiState.Error(report.cause)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            report.value = try {
                results.report(attemptId)?.let { UiState.Success(it) } ?: UiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    /** Opens the notebook entry of a missed question (every missed question of a submitted attempt has one). */
    fun openError(questionId: String, open: (Long) -> Unit) {
        viewModelScope.launch { errors.findByQuestion(questionId)?.let(open) }
    }

    fun selectFilter(value: ReviewFilter) {
        filter.value = value
    }

    private fun ResultItem.matches(filter: ReviewFilter) = when (filter) {
        ReviewFilter.ALL -> true
        ReviewFilter.WRONG -> outcome == ItemOutcome.WRONG
        ReviewFilter.BLANK -> outcome == ItemOutcome.UNANSWERED
    }
}
