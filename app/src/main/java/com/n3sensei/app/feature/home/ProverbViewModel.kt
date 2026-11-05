package com.n3sensei.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.proverb.Proverb
import com.n3sensei.app.domain.proverb.ProverbOfTheDay
import com.n3sensei.app.domain.proverb.ProverbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class ProverbUiState(val proverb: Proverb, val source: String)

/** The proverb of the day on Trang chủ; "Câu khác" moves to the next one (not saved). */
class ProverbViewModel(
    private val repository: ProverbRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProverbUiState>>(UiState.Loading)
    val state: StateFlow<UiState<ProverbUiState>> = _state.asStateFlow()

    private var skip = 0

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _state.value = compute() }
    }

    fun next() {
        skip++
        load()
    }

    private suspend fun compute(): UiState<ProverbUiState> = try {
        val set = repository.load()
        val today = LocalDate.now(clock)
        ProverbOfTheDay.pick(set.proverbs, today, skip)?.let { UiState.Success(ProverbUiState(it, set.source)) } ?: UiState.Empty
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UiState.Error(e)
    }
}
