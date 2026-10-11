package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Result of one answer: the new progress and the history record, written together. */
data class ScheduledReview(val progress: LearningProgress, val record: ReviewRecord)

/**
 * Step-ladder scheduler (Leitner-style). The item's step selects the next interval from the
 * [ladder]:
 *
 * | Answer | New item       | Item on step s                  |
 * |--------|----------------|---------------------------------|
 * | Quên   | step 0         | step 0 (a lapse if it was in REVIEW/MASTERED) |
 * | Khó    | step 0         | stays on s                       |
 * | Tốt    | step 1         | s + 1                            |
 * | Dễ     | step 2         | s + 2                            |
 *
 * Steps are capped at the last step. The next due time is `ladder[step]` added to the answer
 * time (see [ReviewInterval.addTo] for the calendar rule). State: MASTERED on the last step,
 * REVIEW from the first step of a day or more, otherwise LEARNING.
 *
 * A stored step beyond a shortened ladder is clamped; already-scheduled due times are never
 * rewritten, so a settings change applies from each item's next answer.
 */
class SpacedRepetitionScheduler(private val ladder: ReviewLadder) {

    /** Step the item moves to; [currentStep] is null for a new item. */
    fun nextStep(currentStep: Int?, rating: ReviewRating): Int {
        val step = when (currentStep) {
            null -> when (rating) {
                ReviewRating.AGAIN, ReviewRating.HARD -> 0
                ReviewRating.GOOD -> 1
                ReviewRating.EASY -> 2
            }
            else -> {
                val s = ladder.clampStep(currentStep)
                when (rating) {
                    ReviewRating.AGAIN -> 0
                    ReviewRating.HARD -> s
                    ReviewRating.GOOD -> s + 1
                    ReviewRating.EASY -> s + 2
                }
            }
        }
        return ladder.clampStep(step)
    }

    /** Interval the item would get for [rating]; used to label the answer buttons. */
    fun preview(current: LearningProgress?, rating: ReviewRating): ReviewInterval =
        ladder[nextStep(current?.stepIndex, rating)]

    fun stateFor(step: Int): ProgressState = when {
        step >= ladder.lastIndex -> ProgressState.MASTERED
        step >= ladder.reviewStartIndex -> ProgressState.REVIEW
        else -> ProgressState.LEARNING
    }

    fun schedule(
        item: ItemRef,
        current: LearningProgress?,
        rating: ReviewRating,
        now: Instant,
        zone: ZoneId,
        requestId: String,
    ): ScheduledReview {
        require(current == null || current.item == item) { "Progress belongs to another item" }
        val step = nextStep(current?.stepIndex, rating)
        val dueAt = ladder[step].addTo(now, zone)
        val intervalMinutes = Duration.between(now, dueAt).toMinutes()
        val wasLongTerm = current?.state == ProgressState.REVIEW || current?.state == ProgressState.MASTERED
        val lapse = rating == ReviewRating.AGAIN && wasLongTerm

        val progress = LearningProgress(
            item = item,
            state = stateFor(step),
            stepIndex = step,
            intervalMinutes = intervalMinutes,
            dueAt = dueAt,
            lastReviewedAt = now,
            reviewCount = (current?.reviewCount ?: 0) + 1,
            lapseCount = (current?.lapseCount ?: 0) + if (lapse) 1 else 0,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
        )
        val record = ReviewRecord(
            item = item,
            rating = rating,
            reviewedAt = now,
            intervalBeforeMinutes = current?.intervalMinutes ?: 0,
            intervalAfterMinutes = intervalMinutes,
            dueAtAfter = dueAt,
            wasNew = current == null,
            requestId = requestId,
        )
        return ScheduledReview(progress, record)
    }
}
