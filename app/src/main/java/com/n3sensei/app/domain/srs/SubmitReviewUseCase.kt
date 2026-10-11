package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.repository.ProgressRepository
import java.time.Instant
import java.time.ZoneId

/**
 * Records one answer: the scheduler runs on the progress read inside the repository's
 * transaction, so concurrent or repeated submits cannot schedule from stale data.
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
        return progressRepository.submitReview(item, requestId) { current ->
            scheduler.schedule(item, current, rating, now, zone, requestId)
        }
    }
}
