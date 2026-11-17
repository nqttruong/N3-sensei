package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewLadder
import java.time.Duration

/**
 * EXPERIMENTAL settings of Contrast Lab. Every value here is a proposal awaiting the project
 * owner's decision, not a scientific standard (ARCHITECTURE §8, DOMAIN_CONTRACT §4 R8–R11).
 * The algorithms read them from here only, so changing a value never needs new code.
 */
data class ContrastConfig(
    /** Contrast's own ladder; the learner's ReviewSettings ladder is never read or changed. */
    val ladder: ReviewLadder = DEFAULT_LADDER,
    val rating: RatingPolicy = RatingPolicy(),
    val mastery: MasteryPolicy = MasteryPolicy(),
    /** Questions per group block (k). */
    val questionsPerBlock: Int = 3,
    /** New groups a day (Contrast's own limit, independent of DailyLimits). */
    val newGroupsPerDay: Int = 3,
    /**
     * Groups per session. Not in the contract: a session needs a bound and none was specified,
     * so this is an added experimental value.
     */
    val maxGroupsPerSession: Int = 10,
    /** An ACTIVE session older than this (since its last activity) is not resumed. */
    val resumeWindow: Duration = Duration.ofHours(24),
    /** Gap after which an answer counts as a delayed review. */
    val delayedGap: Duration = Duration.ofDays(7),
) {
    init {
        require(questionsPerBlock >= 1) { "questionsPerBlock must be ≥ 1" }
        require(newGroupsPerDay >= 0) { "newGroupsPerDay must be ≥ 0" }
        require(maxGroupsPerSession >= 1) { "maxGroupsPerSession must be ≥ 1" }
        require(!resumeWindow.isNegative && !delayedGap.isNegative) { "Durations must not be negative" }
    }

    companion object {
        /** 1 ngày, 3 ngày, 7 ngày, 14 ngày, 30 ngày, 60 ngày (no minute/hour steps: see ARCHITECTURE §8). */
        val DEFAULT_LADDER: ReviewLadder = ReviewLadder.of(
            listOf(1, 3, 7, 14, 30, 60).map { ReviewInterval(it, IntervalUnit.DAYS) },
        )
    }
}

/**
 * How a completed block becomes a rating (EXPERIMENTAL). Accuracy = correct / (correct + incorrect).
 * - every question correct and none skipped → GOOD;
 * - accuracy ≥ [hardMinAccuracy] → HARD;
 * - otherwise → AGAIN;
 * - skipped ≥ [maxSkippedShare] of the block → no rating (not enough evidence, schedule unchanged).
 */
data class RatingPolicy(
    val hardMinAccuracy: Double = 0.6,
    val maxSkippedShare: Double = 0.5,
) {
    init {
        require(hardMinAccuracy in 0.0..1.0 && maxSkippedShare in 0.0..1.0) { "Shares must be within 0..1" }
    }
}

/**
 * When a group counts as MASTERED (EXPERIMENTAL, rule R11). All must hold:
 * answered (not skipped) ≥ [minAnswered]; sessions on ≥ [minSessionDays] different days;
 * delayed answers ≥ [minDelayedAnswers] with accuracy ≥ [minDelayedAccuracy] over ≥
 * [minDelayedQuestionTypes] question types; the latest delayed block was not AGAIN.
 */
data class MasteryPolicy(
    val minAnswered: Int = 12,
    val minSessionDays: Int = 3,
    val minDelayedAnswers: Int = 4,
    val minDelayedAccuracy: Double = 0.85,
    val minDelayedQuestionTypes: Int = 2,
) {
    init {
        require(minAnswered >= 1 && minSessionDays >= 1 && minDelayedAnswers >= 1 && minDelayedQuestionTypes >= 1) { "Minimums must be ≥ 1" }
        require(minDelayedAccuracy in 0.0..1.0) { "minDelayedAccuracy must be within 0..1" }
    }
}
