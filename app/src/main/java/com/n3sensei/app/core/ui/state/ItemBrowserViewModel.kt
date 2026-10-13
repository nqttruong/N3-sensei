package com.n3sensei.app.core.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Tag
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

const val SEARCH_DEBOUNCE_MILLIS = 300L

/** Keeps upstream flows alive across configuration changes. */
const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Shared state for the vocabulary / kanji / grammar lists: search text plus lesson, status,
 * "marked" and topic filters. Results are live, so marking an item updates the list.
 */
abstract class ItemBrowserViewModel<T>(
    contentRepository: ContentRepository,
    /** The kind of item listed; lesson chips only offer lessons that contain some. */
    itemType: ItemType,
    initialStatus: LearningStatus? = null,
) : ViewModel() {

    private val _filters = MutableStateFlow(ItemQuery(status = initialStatus))

    /** Current filters; [ItemQuery.text] is what the user typed (not yet debounced). */
    val filters: StateFlow<ItemQuery> = _filters.asStateFlow()

    val lessons: StateFlow<List<LessonSummary>> = contentRepository.observeLessons()
        .map { all -> all.filter { it.countOf(itemType) > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Topics available for filtering; empty for item types without topics. */
    open val tags: StateFlow<List<Tag>> = MutableStateFlow(emptyList())

    val items: StateFlow<UiState<List<TrackedItem<T>>>> by lazy {
        browseItems(_filters, ::observe)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
    }

    protected abstract fun observe(query: ItemQuery): Flow<List<TrackedItem<T>>>

    fun onQueryChange(text: String) = _filters.update { it.copy(text = text) }
    fun onLessonSelected(lessonId: String?) = _filters.update { it.copy(lessonId = lessonId) }
    fun onStatusSelected(status: LearningStatus?) = _filters.update { it.copy(status = status) }
    fun onMarkedOnlyChange(markedOnly: Boolean) = _filters.update { it.copy(markedOnly = markedOnly) }
    fun onTagSelected(tagId: String?) = _filters.update { it.copy(tagId = tagId) }
}

/**
 * Debounces typing (filter changes apply at once) and re-queries whenever the effective query
 * changes. Only the trimmed text matters, so trailing spaces do not trigger a new query.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun <T> browseItems(
    filters: Flow<ItemQuery>,
    observe: (ItemQuery) -> Flow<List<T>>,
): Flow<UiState<List<T>>> =
    filters
        .map { it.copy(text = it.text.trim()) }
        .distinctUntilChanged()
        .debounceTextChanges()
        .flatMapLatest(observe)
        .asListUiState()

/** Waits for typing to pause; filter changes and clearing the text apply immediately. */
@OptIn(FlowPreview::class)
private fun Flow<ItemQuery>.debounceTextChanges(): Flow<ItemQuery> =
    scan<ItemQuery, Pair<ItemQuery?, ItemQuery>?>(null) { previous, query -> previous?.second to query }
        .filterNotNull()
        .debounce { (previous, query) ->
            val typing = previous != null && previous.text != query.text && query.text.isNotEmpty()
            if (typing) SEARCH_DEBOUNCE_MILLIS else 0L
        }
        .map { it.second }

private fun LessonSummary.countOf(type: ItemType): Int = when (type) {
    ItemType.VOCABULARY -> vocabularyCount
    ItemType.KANJI -> kanjiCount
    ItemType.GRAMMAR -> grammarCount
}
