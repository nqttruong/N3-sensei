package com.n3sensei.app.core.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads one item for a detail screen: Loading → Success, NotFound (null result) or Error.
 * Subclasses call [refresh] from their `init` block.
 */
abstract class DetailViewModel<T : Any> : ViewModel() {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    protected abstract suspend fun load(): T?

    /** Loads again without showing Loading (e.g. back from the edit screen). */
    fun reload() {
        viewModelScope.launch {
            try {
                _state.value = load()?.let { UiState.Success(it) } ?: UiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState.Error(e)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                load()?.let { UiState.Success(it) } ?: UiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }
}
