package com.n3sensei.app.core.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

const val SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * Items for a list screen with a search box and a lesson filter: the live list of the selected
 * lesson (or all lessons) while the query is blank, otherwise one-shot search results.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun <T> searchableItems(
    query: Flow<String>,
    lessonId: Flow<String?>,
    observeAll: (lessonId: String?) -> Flow<List<T>>,
    search: suspend (query: String, lessonId: String?) -> List<T>,
): Flow<UiState<List<T>>> {
    val debouncedQuery = query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .distinctUntilChanged { old, new -> old.trim() == new.trim() }
    return combine(debouncedQuery, lessonId) { q, lesson -> q.trim() to lesson }
        .distinctUntilChanged()
        .flatMapLatest { (q, lesson) ->
            if (q.isEmpty()) observeAll(lesson) else flow { emit(search(q, lesson)) }
        }
        .asListUiState()
}
