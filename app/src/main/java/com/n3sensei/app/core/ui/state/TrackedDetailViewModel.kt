package com.n3sensei.app.core.ui.state

import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemUserState
import com.n3sensei.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Detail screen of a learnable item: content plus the learner's status and mark. */
abstract class TrackedDetailViewModel<T : Any>(
    private val ref: ItemRef,
    private val progressRepository: ProgressRepository,
) : DetailViewModel<T>() {

    /** Null until loaded. */
    val itemState: StateFlow<ItemUserState?> = progressRepository.observeItemState(ref)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun toggleMark() {
        val current = itemState.value ?: return
        viewModelScope.launch { progressRepository.setMarked(ref, !current.isMarked) }
    }
}
