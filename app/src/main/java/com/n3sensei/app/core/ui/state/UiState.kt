package com.n3sensei.app.core.ui.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * State of a screen section backed by asynchronous data.
 *
 * ViewModels expose `StateFlow<UiState<T>>`; Composables render it with [UiStateContent].
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    /** Data loaded successfully but there is nothing to show (e.g. no due reviews, no search results). */
    data object Empty : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>

    /** The requested item does not exist (e.g. removed by a content update or bad link). */
    data object NotFound : UiState<Nothing>

    data class Error(val cause: Throwable) : UiState<Nothing>
}

/**
 * Maps a data stream to [UiState]: emits [UiState.Loading] first, then [UiState.Empty] or
 * [UiState.Success] per value, and [UiState.Error] if the upstream fails.
 */
fun <T> Flow<T>.asUiState(isEmpty: (T) -> Boolean = { false }): Flow<UiState<T>> =
    map<T, UiState<T>> { value -> if (isEmpty(value)) UiState.Empty else UiState.Success(value) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }

/** [asUiState] for list streams, treating an empty list as [UiState.Empty]. */
fun <T> Flow<List<T>>.asListUiState(): Flow<UiState<List<T>>> = asUiState { it.isEmpty() }
