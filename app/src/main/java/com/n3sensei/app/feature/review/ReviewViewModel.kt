package com.n3sensei.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.ticks
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.repository.ErrorRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewQueuePlanner
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/** One content type on the Review screen: its own counts and the session it would start. */
data class TypeReview(
    val type: ItemType,
    val due: Int,
    /** Part of [due] that fell due before today's local midnight. */
    val overdue: Int,
    /** Distinct items of this type answered today. */
    val doneToday: Int,
    val newAvailable: Int,
    /** Items of this type introduced today, and this type's own daily cap. */
    val newToday: Int = 0,
    val newLimit: Int = 0,
    /** Cards a session of this type alone would contain now (today's limits applied). */
    val sessionSize: Int,
)

data class ReviewUiState(
    val overview: ReviewOverview,
    val limits: DailyLimits,
    /** New items a session would include now (limit applied). */
    val newToday: Int,
    /** Cards a mixed session started now would contain. */
    val sessionSize: Int,
    val types: List<TypeReview> = emptyList(),
    /** Cards of a mixed session of due items only. */
    val dueOnlySize: Int = 0,
    /** Cards of a session of the items last forgotten. */
    val forgottenSize: Int = 0,
) {
    val limitReached: Boolean get() = sessionSize == 0 && (overview.due > 0 || overview.newAvailable > 0)

    companion object {
        /** The screen's numbers from the overview and today's limits (pure: unit-tested). */
        fun of(overview: ReviewOverview, limits: DailyLimits): ReviewUiState {
            val reviews = ReviewQueuePlanner.remainingReviews(limits, overview.today)
            // Each type's own allowance: what one type uses or leaves never changes another's.
            val newByType = ItemType.entries.associateWith { minOf(overview.newByType[it] ?: 0, ReviewQueuePlanner.remainingNew(limits, overview.today, it)) }
            val newToday = newByType.values.sum()
            val types = ItemType.entries.map { type ->
                val due = overview.dueByType[type] ?: 0
                TypeReview(
                    type = type,
                    due = due,
                    overdue = overview.overdueByType[type] ?: 0,
                    doneToday = overview.doneTodayByType[type] ?: 0,
                    newAvailable = overview.newByType[type] ?: 0,
                    newToday = overview.today.newByType[type] ?: 0,
                    newLimit = limits.newPerDay(type),
                    sessionSize = minOf(due, reviews) + newByType.getValue(type),
                )
            }
            return ReviewUiState(
                overview = overview,
                limits = limits,
                newToday = newToday,
                sessionSize = minOf(overview.due, reviews) + newToday,
                types = types,
                dueOnlySize = minOf(overview.due, reviews),
                forgottenSize = minOf(overview.forgotten, reviews),
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(
    progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
    /** Exam errors due for review, shown as their own card; null leaves the card out. */
    errors: ErrorRepository? = null,
) : ViewModel() {

    /** Recomputed every minute and whenever the data or the daily limits change. */
    val state: StateFlow<UiState<ReviewUiState>> = combine(
        clock.ticks(),
        preferencesRepository.preferences.map { it.reviewSettings.limits }.distinctUntilChanged(),
    ) { now, limits -> now to limits }
        .flatMapLatest { (now, limits) ->
            val zone = clock.zone
            progressRepository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
                .map { overview -> UiState.Success(ReviewUiState.of(overview, limits)) }
        }
        .onStart<UiState<ReviewUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    /** Items marked as needing attention whose content still exists. */
    val markedCount: StateFlow<Int> = progressRepository.observeMarkedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /** Exam errors due now, recounted every minute (errors fall due with time, not with app use). */
    val errorsDue: StateFlow<Int> = (errors?.let { repo -> clock.ticks().flatMapLatest { repo.observeDueCount(it.toEpochMilli()) } } ?: flowOf(0))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)
}
