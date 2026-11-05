package com.n3sensei.app.feature.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.NotebookFilter
import com.n3sensei.app.domain.mistakes.NotebookOverview
import com.n3sensei.app.domain.mistakes.NotebookStateFilter
import com.n3sensei.app.domain.repository.ErrorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

data class ErrorNotebookUiState(
    /** Over every error, whatever the filter. */
    val overview: NotebookOverview,
    val filter: NotebookFilter,
    /** The errors the filter and search keep, priority first. */
    val errors: List<NotebookError>,
    /** Open errors due for review now. */
    val dueNow: Int = 0,
)

/** The error notebook. Filters and search are kept in [savedState], so they survive the process. */
class ErrorNotebookViewModel(
    errors: ErrorRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val level = savedState.getStateFlow<String?>(KEY_LEVEL, null)
    // The route may pre-select a skill (from a skill's details).
    private val skill = savedState.getStateFlow(KEY_SKILL, savedState.get<String>(ROUTE_SKILL))
    private val stateFilter = savedState.getStateFlow(KEY_STATE, NotebookStateFilter.ALL.name)
    /**
     * What is typed in the search field, given to the field directly: the filtered list is
     * computed off the main thread, and a field fed from that would lag and lose keystrokes.
     */
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    private val filter = combine(level, skill, stateFilter, query) { level, skill, state, query ->
        NotebookFilter(
            level = level,
            skill = ExamSkill.entries.firstOrNull { it.name == skill },
            state = NotebookStateFilter.entries.firstOrNull { it.name == state } ?: NotebookStateFilter.ALL,
            query = query,
        )
    }

    val state: StateFlow<UiState<ErrorNotebookUiState>> = combine(errors.observeErrors(), filter) { all, filter ->
        if (all.isEmpty()) {
            UiState.Empty
        } else {
            val now = clock.millis()
            UiState.Success(
                ErrorNotebookUiState(
                    NotebookOverview.of(all), filter, all.filter(filter::matches).sortedWith(NotebookFilter.ORDER),
                    dueNow = all.count { it.isOpen && it.review != null && it.review.nextReviewAt <= now },
                ),
            )
        }
    }
        // Search folds every entry's text: off the main thread.
        .flowOn(Dispatchers.Default)
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun selectLevel(value: String?) {
        savedState[KEY_LEVEL] = value
    }

    fun selectSkill(value: ExamSkill?) {
        savedState[KEY_SKILL] = value?.name
    }

    fun selectState(value: NotebookStateFilter) {
        savedState[KEY_STATE] = value.name
    }

    fun search(value: String) {
        savedState[KEY_QUERY] = value
    }

    private companion object {
        const val KEY_LEVEL = "errors_level"
        const val KEY_SKILL = "errors_skill"
        const val KEY_STATE = "errors_state"
        const val KEY_QUERY = "errors_query"

        /** Argument of the notebook's route. */
        const val ROUTE_SKILL = "skill"
    }
}
