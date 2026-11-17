package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.srs.SpacedRepetitionScheduler
import java.time.Instant
import java.time.ZoneId

/** Counts of one completed block. */
data class BlockTally(val correct: Int, val incorrect: Int, val skipped: Int) {
    val total: Int get() = correct + incorrect + skipped
    val answered: Int get() = correct + incorrect

    companion object {
        fun of(outcomes: List<AnswerOutcome>) = BlockTally(
            correct = outcomes.count { it == AnswerOutcome.CORRECT },
            incorrect = outcomes.count { it == AnswerOutcome.INCORRECT },
            skipped = outcomes.count { it == AnswerOutcome.SKIPPED },
        )
    }
}

/**
 * Contrast's own scheduling (ARCHITECTURE §8). Reuses only the pure parts of the existing SRS:
 * [SpacedRepetitionScheduler.nextStep] on Contrast's ladder and [com.n3sensei.app.domain.srs.ReviewInterval.addTo].
 * It never calls `SpacedRepetitionScheduler.schedule()` (bound to ItemType / LearningProgress)
 * and never reads or writes the learner's ReviewSettings.
 */
class ContrastScheduler(private val config: ContrastConfig = ContrastConfig()) {

    private val steps = SpacedRepetitionScheduler(config.ladder)
    private val ladder = config.ladder

    /** Rating of a completed block, or null when too much was skipped to judge (schedule unchanged). */
    fun ratingOf(tally: BlockTally): ReviewRating? {
        require(tally.total > 0) { "Empty block" }
        if (tally.skipped >= config.rating.maxSkippedShare * tally.total) return null
        if (tally.correct == tally.total) return ReviewRating.GOOD
        val accuracy = tally.correct.toDouble() / tally.answered
        return if (accuracy >= config.rating.hardMinAccuracy) ReviewRating.HARD else ReviewRating.AGAIN
    }

    /** True when [step] is on an interval of [ContrastConfig.delayedGap] or more (state REVIEWING). */
    fun isLongTerm(step: Int): Boolean =
        ladder[ladder.clampStep(step)].nominalMinutes >= config.delayedGap.toMinutes()

    /**
     * The review and new progress for a completed block of [groupId], or null when the block gives
     * no rating. A stored step beyond a shorter ladder is clamped, like the existing SRS.
     */
    fun schedule(
        groupId: String,
        sessionId: String,
        current: ContrastProgress?,
        tally: BlockTally,
        now: Instant,
        zone: ZoneId,
        contentVersion: Int,
    ): PendingReview? {
        require(current == null || current.groupId == groupId) { "Progress belongs to another group" }
        val rating = ratingOf(tally) ?: return null
        val stepBefore = current?.stepIndex?.let(ladder::clampStep)
        val stepAfter = steps.nextStep(stepBefore, rating)
        val dueAt = ladder[stepAfter].addTo(now, zone)
        val lapse = rating == ReviewRating.AGAIN && stepBefore != null && isLongTerm(stepBefore)
        val progress = ContrastProgress(
            groupId = groupId,
            stepIndex = stepAfter,
            dueAt = dueAt,
            lastReviewedAt = now,
            reviewCount = (current?.reviewCount ?: 0) + 1,
            lapseCount = (current?.lapseCount ?: 0) + if (lapse) 1 else 0,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
        )
        val review = ContrastReview(
            groupId = groupId,
            sessionId = sessionId,
            reviewedAt = now,
            answered = tally.answered,
            correct = tally.correct,
            incorrect = tally.incorrect,
            skipped = tally.skipped,
            rating = rating,
            stepBefore = stepBefore,
            stepAfter = stepAfter,
            dueAtAfter = dueAt,
            contentVersion = contentVersion,
        )
        return PendingReview(review, progress)
    }

    fun isDue(progress: ContrastProgress, now: Instant): Boolean = !progress.dueAt.isAfter(now)
}
