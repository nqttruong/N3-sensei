package com.n3sensei.app.feature.contrast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastFilter
import com.n3sensei.app.domain.contrast.GetContrastProgress
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

data class ContrastLibraryUiState(
    val query: String,
    /** null = every type. */
    val type: ContrastContentType?,
    /** null = every status. */
    val status: LibraryStatus?,
    val rows: List<ContrastGroupRow>,
    /** Published groups before search and filters: tells "no content" from "nothing matches". */
    val totalGroups: Int,
)

/**
 * Contrast Lab library: search (title, labels, readings, meanings, accent-insensitive, done by the
 * repository's search text) and filters by type and learning status. Query and filters live in the
 * SavedStateHandle, so they are still there when the learner comes back from a group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContrastLibraryViewModel(
    private val content: ContrastContentRepository,
    practice: ContrastPracticeRepository,
    progress: ContrastProgressRepository,
    private val clock: Clock,
    private val saved: SavedStateHandle = SavedStateHandle(),
    config: ContrastConfig = ContrastConfig(),
) : ViewModel() {

    private val getProgress = GetContrastProgress(content, practice, progress, config)
    private val query = MutableStateFlow(saved.get<String>(KEY_QUERY).orEmpty())
    // The filter kept across process death wins; otherwise the category the screen was opened with (route argument).
    private val type = MutableStateFlow((saved.get<String>(KEY_TYPE) ?: saved.get<String>(ROUTE_TYPE))?.let(ContrastContentType::fromCode))
    private val status = MutableStateFlow(saved.get<String>(KEY_STATUS)?.let { s -> LibraryStatus.entries.firstOrNull { it.name == s } })
    private val reload = MutableStateFlow(0)

    /**
     * The search text as typed, updated synchronously: the search field shows this, not the query
     * inside [state] (computed asynchronously), so fast typing never loses characters.
     */
    val queryText: StateFlow<String> = query.asStateFlow()

    private val matching = combine(query, type, reload) { q, t, _ -> ContrastFilter(query = q.trim().ifEmpty { null }, contentType = t) }
        .flatMapLatest { filter -> content.observeGroups(filter) }

    val state: StateFlow<UiState<ContrastLibraryUiState>> =
        combine(content.observeGroups(), matching, query, type, status) { all, groups, q, t, s -> Inputs(all.size, groups, q, t, s) }
            .mapLatest { input ->
                if (input.total == 0) {
                    UiState.Empty
                } else {
                    val now = clock.instant()
                    val rows = input.groups.map { ContrastGroupRow.of(it, getProgress.ofGroup(it.id, clock.zone), now) }
                        .filter { input.status == null || input.status in it.statuses }
                    UiState.Success(ContrastLibraryUiState(input.query, input.type, input.status, rows, input.total))
                }
            }
            .onStart<UiState<ContrastLibraryUiState>> { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private data class Inputs(
        val total: Int,
        val groups: List<com.n3sensei.app.domain.contrast.ContrastGroup>,
        val query: String,
        val type: ContrastContentType?,
        val status: LibraryStatus?,
    )

    fun setQuery(value: String) {
        query.value = value
        saved[KEY_QUERY] = value
    }

    fun selectType(value: ContrastContentType?) {
        type.value = value
        saved[KEY_TYPE] = value?.name
    }

    fun selectStatus(value: LibraryStatus?) {
        status.value = value
        saved[KEY_STATUS] = value?.name
    }

    fun retry() {
        reload.value++
    }

    private companion object {
        const val KEY_QUERY = "contrast_library_query"
        const val KEY_TYPE = "contrast_library_type"

        /** Navigation argument of ContrastLibraryRoute(type). */
        const val ROUTE_TYPE = "type"
        const val KEY_STATUS = "contrast_library_status"
    }
}
