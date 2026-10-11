package com.n3sensei.app.core.ui.state

import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemUserState
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.DueState
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZonedDateTime

/** The learner's schedule for one item, ready to display. */
data class ItemScheduleInfo(
    val state: ItemUserState,
    val dueState: DueState,
    /** Next review in the device's current time zone. */
    val dueAt: ZonedDateTime?,
    val ladderSize: Int,
)

/** Detail screen of a learnable item: content plus the learner's status, schedule and mark. */
abstract class TrackedDetailViewModel<T : Any>(
    private val ref: ItemRef,
    private val progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
) : DetailViewModel<T>() {

    /** Null until loaded; refreshed every minute so "due" / "overdue" stay correct. */
    val scheduleInfo: StateFlow<ItemScheduleInfo?> = combine(
        progressRepository.observeItemState(ref),
        clock.ticks(),
        preferencesRepository.preferences.map { it.reviewSettings.ladder.size },
    ) { state, now, ladderSize ->
        val zone = clock.zone
        ItemScheduleInfo(state, ReviewTime.dueState(state.dueAt, now, zone), state.dueAt?.atZone(zone), ladderSize)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun toggleMark() {
        val current = scheduleInfo.value?.state ?: return
        viewModelScope.launch { progressRepository.setMarked(ref, !current.isMarked) }
    }
}
