package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.NewItemLimit
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.repository.ProgressRepository
import java.time.Instant
import java.time.ZoneId

/**
 * Records one answer: the scheduler runs on the progress read inside the repository's
 * transaction, so concurrent or repeated submits cannot schedule from stale data. A first answer
 * to a new item is saved only within its type's daily new-item cap ([DailyLimits.newPerDay]),
 * counted in the local day of [now] inside that same transaction; otherwise nothing is saved
 * and the outcome is [SubmitOutcome.NEW_LIMIT_REACHED].
 */
class SubmitReviewUseCase(private val progressRepository: ProgressRepository) {
    suspend operator fun invoke(
        item: ItemRef,
        requestId: String,
        rating: ReviewRating,
        settings: ReviewSettings,
        now: Instant,
        zone: ZoneId,
    ): SubmitOutcome {
        val scheduler = SpacedRepetitionScheduler(settings.ladder)
        val limit = NewItemLimit(settings.limits.newPerDay(item.type), ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
        return progressRepository.submitReview(item, requestId, limit) { current ->
            scheduler.schedule(item, current, rating, now, zone, requestId)
        }
    }
}
