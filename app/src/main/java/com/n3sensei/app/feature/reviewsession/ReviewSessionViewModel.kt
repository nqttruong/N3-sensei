package com.n3sensei.app.feature.reviewsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.forecast.ReviewSessionRepository
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewMode
import com.n3sensei.app.domain.srs.ReviewQueuePlanner
import com.n3sensei.app.domain.srs.ReviewScope
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.ReviewTime
import com.n3sensei.app.domain.srs.SpacedRepetitionScheduler
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** One card of the session. [requestId] is fixed per card, so a retry cannot double-record. */
data class SessionCard(
    val ref: ItemRef,
    val item: PracticeItem,
    val progress: LearningProgress?,
    val requestId: String,
    /** Interval each answer would give, for the button labels. */
    val hints: Map<ReviewRating, ReviewInterval>,
    /** Why this card is in the session, shown above the card. */
    val reason: CardReason,
)

/** Why a card is in today's session. */
sealed interface CardReason {
    /** Never answered; part of today's new-item limit. */
    data object New : CardReason

    /**
     * Its stored due time has passed.
     *
     * @param step 1-based ladder step, of [steps].
     * @param overdue due before local midnight today.
     */
    data class Due(val dueAt: Instant, val step: Int, val steps: Int, val lapses: Int, val overdue: Boolean) : CardReason

    /** Asked again because its last answer was "Quên" (it may not be due yet). */
    data class Forgotten(val lapses: Int) : CardReason

    companion object {
        fun of(progress: LearningProgress?, steps: Int, startOfToday: Instant): CardReason {
            val due = progress?.dueAt ?: return New
            return Due(due, minOf(progress.stepIndex, steps - 1) + 1, steps, progress.lapseCount, due.isBefore(startOfToday))
        }
    }
}

data class ReviewSessionUiState(
    val cards: List<SessionCard>,
    val index: Int = 0,
    val isAnswerRevealed: Boolean = false,
    /** True while an answer is being written; further taps are ignored. */
    val isSubmitting: Boolean = false,
    val saveFailed: Boolean = false,
    val ratings: List<ReviewRating> = emptyList(),
    /** Due or new items that no longer exist in the content. */
    val skippedMissing: Int = 0,
    /** Cards not saved because their type reached today's new-item limit meanwhile (another session). */
    val skippedLimit: Int = 0,
    val result: PracticeResult? = null,
) {
    val current: SessionCard? get() = cards.getOrNull(index)
}

/**
 * Scheduled review session over [scope]: one type or all three, due and new items, due only, or
 * the items last forgotten. The queue is built once at the start, within today's limits; a mixed
 * queue takes the types in turn ([ReviewQueuePlanner.interleave]). Every answer is saved
 * immediately through [SubmitReviewUseCase], on the item's own schedule (type and id).
 */
class ReviewSessionViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val submitReview: SubmitReviewUseCase,
    private val sessions: ReviewSessionRepository,
    private val clock: Clock,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
    val scope: ReviewScope = ReviewScope.ALL,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ReviewSessionUiState>>(UiState.Loading)
    val state: StateFlow<UiState<ReviewSessionUiState>> = _state.asStateFlow()

    private var settings = ReviewSettings()
    private var sessionId: String? = null
    private var startedAt = clock.instant()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                buildSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    private suspend fun buildSession(): UiState<ReviewSessionUiState> {
        settings = preferencesRepository.preferences.first().reviewSettings
        val now = clock.instant()
        val zone = clock.zone
        startedAt = now
        val overview = progressRepository
            .observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
            .first()
        val limits = settings.limits
        val reviews = ReviewQueuePlanner.remainingReviews(limits, overview.today)
        val type = scope.type
        // Due (or last forgotten) items: one type's own queue, or all types in their usual
        // order (oldest due first), within today's review allowance.
        val asked = when {
            reviews == 0 -> emptyList()
            scope.mode == ReviewMode.FORGOTTEN && type == null -> progressRepository.getForgottenItems(reviews)
            scope.mode == ReviewMode.FORGOTTEN -> progressRepository.getForgottenItemsOfType(type!!, reviews)
            type == null -> progressRepository.getDueItems(now, reviews)
            else -> progressRepository.getDueItemsOfType(now, type, reviews)
        }
        // New items: each type up to its own allowance left today, in its own course order;
        // one type's allowance never moves to another.
        val newItems = if (scope.mode != ReviewMode.SCHEDULED) {
            emptyList()
        } else {
            ItemType.entries.filter(scope::includes).flatMap { t ->
                val left = ReviewQueuePlanner.remainingNew(limits, overview.today, t)
                if (left == 0) emptyList() else progressRepository.getNewItemsOfType(t, left)
            }
        }
        val queue = ReviewQueuePlanner.plan(asked, newItems, limits, overview.today)

        val scheduler = SpacedRepetitionScheduler(settings.ladder)
        var missing = 0
        val cards = queue.mapNotNull { ref ->
            val item = contentRepository.getPracticeItem(ref) ?: run { missing++; return@mapNotNull null }
            val progress = progressRepository.getProgress(ref)
            SessionCard(
                ref, item, progress, newRequestId(),
                hints = ReviewRating.entries.associateWith { scheduler.preview(progress, it) },
                reason = if (scope.mode == ReviewMode.FORGOTTEN) {
                    CardReason.Forgotten(progress?.lapseCount ?: 0)
                } else {
                    CardReason.of(progress, settings.ladder.size, ReviewTime.startOfDay(now, zone))
                },
            )
        }
        if (cards.isEmpty()) return UiState.Empty
        // Recorded for the dashboard's session history; a reload starts a new session.
        val id = newRequestId()
        sessionId = if (recordSafely { sessions.start(id, cards.size, now) }) id else null
        return UiState.Success(ReviewSessionUiState(cards, skippedMissing = missing))
    }

    fun revealAnswer() = _state.update { s ->
        if (s is UiState.Success && s.data.result == null) UiState.Success(s.data.copy(isAnswerRevealed = true)) else s
    }

    fun rate(rating: ReviewRating) {
        val ui = (_state.value as? UiState.Success)?.data ?: return
        val card = ui.current ?: return
        if (!ui.isAnswerRevealed || ui.isSubmitting || ui.result != null) return
        _state.value = UiState.Success(ui.copy(isSubmitting = true, saveFailed = false))

        viewModelScope.launch {
            try {
                // APPLIED or DUPLICATE: either way this answer is stored exactly once.
                // NEW_LIMIT_REACHED: another session introduced this type's last new item of
                // today in the meantime; nothing was saved, the card is left for another day.
                val outcome = submitReview(card.ref, card.requestId, rating, settings, clock.instant(), clock.zone)
                advance(rating.takeIf { outcome != SubmitOutcome.NEW_LIMIT_REACHED })
                recordSessionProgress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> if (s is UiState.Success) UiState.Success(s.data.copy(isSubmitting = false, saveFailed = true)) else s }
            }
        }
    }

    private suspend fun recordSessionProgress() {
        val id = sessionId ?: return
        val ui = (_state.value as? UiState.Success)?.data ?: return
        val now = clock.instant()
        recordSafely {
            sessions.updateAnswered(id, ui.index, now)
            if (ui.result != null) sessions.finish(id, now)
        }
    }

    /** Session history is secondary: a failure here must not block the review itself. */
    private suspend fun recordSafely(block: suspend () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    /** Next card; [rating] null when the card was not saved (its type's new-item limit was reached). */
    private fun advance(rating: ReviewRating?) = _state.update { s ->
        if (s !is UiState.Success) return@update s
        val ui = s.data
        val ratings = ui.ratings + listOfNotNull(rating)
        val skipped = ui.skippedLimit + if (rating == null) 1 else 0
        val next = ui.index + 1
        val result = if (next >= ui.cards.size) {
            PracticeResult(
                total = ui.cards.size - skipped,
                again = ratings.count { it == ReviewRating.AGAIN },
                hard = ratings.count { it == ReviewRating.HARD },
                good = ratings.count { it == ReviewRating.GOOD },
                easy = ratings.count { it == ReviewRating.EASY },
                durationSeconds = Duration.between(startedAt, clock.instant()).seconds.coerceAtLeast(0),
            )
        } else {
            null
        }
        UiState.Success(ui.copy(index = next, isAnswerRevealed = false, isSubmitting = false, ratings = ratings, result = result, skippedLimit = skipped))
    }
}
