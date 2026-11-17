package com.n3sensei.app.core.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.course.ItemLocations
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
import kotlinx.coroutines.flow.combine
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
    initialLessonId: String? = null,
    /** "Từ của tôi": only the learner's own words, with no lesson to switch to. */
    userWordsOnly: Boolean = false,
) : ViewModel() {

    private val _filters = MutableStateFlow(ItemQuery(status = initialStatus, lessonId = initialLessonId, userWordsOnly = userWordsOnly))

    /** Current filters; [ItemQuery.text] is what the user typed (not yet debounced). */
    val filters: StateFlow<ItemQuery> = _filters.asStateFlow()

    private val lessonsWithItems: Flow<List<LessonSummary>> = contentRepository.observeLessons()
        .map { all -> if (userWordsOnly) emptyList() else all.filter { it.countOf(itemType) > 0 } }

    /**
     * JLPT levels of the lessons listed, lower level first ("N3", "N2"). With more than one, the
     * list shows one level at a time (Học → Kanji: N3 / N2), never both mixed.
     */
    val levels: StateFlow<List<String>> = lessonsWithItems
        .map { all -> all.map { it.jlptLevel }.distinct().sortedDescending() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** The filters as applied: with several levels, the chosen one (or the lesson's, or the first). */
    private val applied: Flow<ItemQuery> = combine(_filters, lessonsWithItems) { query, lessons ->
        val levels = lessons.map { it.jlptLevel }.distinct().sortedDescending()
        if (levels.size < 2) {
            query
        } else {
            val level = query.jlptLevel ?: lessons.firstOrNull { it.id == query.lessonId }?.jlptLevel ?: levels.first()
            query.copy(jlptLevel = level)
        }
    }

    /** The level shown when there are several; null with a single level. */
    val level: StateFlow<String?> = combine(applied, levels) { query, levels -> query.jlptLevel.takeIf { levels.size > 1 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Lesson chips: the lessons of the level shown. */
    val lessons: StateFlow<List<LessonSummary>> = combine(lessonsWithItems, level) { all, level ->
        if (level == null) all else all.filter { it.jlptLevel == level }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Where every item is studied, for the location tag on each card. */
    val locations: StateFlow<ItemLocations> = itemLocations(viewModelScope, contentRepository)

    /** Topics available for filtering; empty for item types without topics. */
    open val tags: StateFlow<List<Tag>> = MutableStateFlow(emptyList())

    val items: StateFlow<UiState<List<TrackedItem<T>>>> by lazy {
        browseItems(applied, ::observe)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)
    }

    protected abstract fun observe(query: ItemQuery): Flow<List<TrackedItem<T>>>

    fun onQueryChange(text: String) = _filters.update { it.copy(text = text) }
    fun onLessonSelected(lessonId: String?) = _filters.update { it.copy(lessonId = lessonId) }

    /** Shows another level; the lesson filter is cleared (its lessons are of the other level). */
    fun onLevelSelected(level: String) = _filters.update {
        if ((it.jlptLevel ?: this.level.value) == level) it else it.copy(jlptLevel = level, lessonId = null)
    }
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
