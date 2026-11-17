package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ReviewRating
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Which answers are delayed reviews. Within one group, a session is *delayed* when its first answer
 * comes at least [gap] after the last answer of any earlier session of that group; every answer
 * of a delayed session is delayed. A delayed answer is *fresh* when its question was never
 * answered in an earlier session (the preferred measure: a new question of the same difficulty,
 * not the same question again).
 */
object DelayedAnswers {

    data class Classified(val delayed: Set<String>, val fresh: Set<String>, val delayedSessions: Set<String>)

    /** [attempts] of one group; result holds request ids. */
    fun classify(attempts: List<ContrastAttempt>, gap: Duration): Classified {
        val sessions = attempts.groupBy { it.sessionId }.values
            .map { list -> list.sortedBy { it.answeredAt } }
            .sortedBy { it.first().answeredAt }
        val delayed = mutableSetOf<String>()
        val fresh = mutableSetOf<String>()
        val delayedSessions = mutableSetOf<String>()
        var lastAnswer: Instant? = null
        val seenQuestions = mutableSetOf<String>()
        for (session in sessions) {
            val previous = lastAnswer
            if (previous != null && Duration.between(previous, session.first().answeredAt) >= gap) {
                delayedSessions += session.first().sessionId
                session.forEach { attempt ->
                    delayed += attempt.requestId
                    if (attempt.questionId !in seenQuestions) fresh += attempt.requestId
                }
            }
            session.forEach { seenQuestions += it.questionId }
            lastAnswer = maxOf(previous ?: session.last().answeredAt, session.last().answeredAt)
        }
        return Classified(delayed, fresh, delayedSessions)
    }
}

/** Accuracy over answers that were not skipped; null when nothing was answered. */
data class Accuracy(val correct: Int, val answered: Int) {
    val value: Double? get() = if (answered == 0) null else correct.toDouble() / answered

    companion object {
        fun of(attempts: Collection<ContrastAttempt>) = Accuracy(
            correct = attempts.count { it.outcome == AnswerOutcome.CORRECT },
            answered = attempts.count { it.outcome != AnswerOutcome.SKIPPED },
        )
    }
}

/** Mastery of one group and the evidence behind it, kept apart (content studied ≠ accuracy ≠ delayed results ≠ mastery). */
data class GroupMastery(
    val groupId: String,
    val mastery: ContrastMastery,
    val accuracy: Accuracy,
    val skipped: Int,
    val sessionDays: Int,
    val delayedAccuracy: Accuracy,
    /** The preferred measure: delayed answers on questions never answered before. */
    val freshDelayedAccuracy: Accuracy,
    val delayedQuestionTypes: Int,
    val lastDelayedRating: ReviewRating?,
    val dueAt: Instant?,
)

/**
 * Mastery from what was answered (rule R11; thresholds in [MasteryPolicy], EXPERIMENTAL). Reading
 * the theory is not an input at all, and one correct answer can never make a group MASTERED.
 */
class ContrastMasteryEvaluator(private val config: ContrastConfig = ContrastConfig()) {

    private val scheduler = ContrastScheduler(config)

    fun evaluate(
        groupId: String,
        progress: ContrastProgress?,
        attempts: List<ContrastAttempt>,
        reviews: List<ContrastReview>,
        zone: ZoneId,
    ): GroupMastery {
        require(attempts.all { it.groupId == groupId } && reviews.all { it.groupId == groupId }) { "Data of another group" }
        val classified = DelayedAnswers.classify(attempts, config.delayedGap)
        val delayed = attempts.filter { it.requestId in classified.delayed }
        val delayedAnswered = delayed.filter { it.outcome != AnswerOutcome.SKIPPED }
        val sessionDays = attempts.filter { it.outcome != AnswerOutcome.SKIPPED }
            .groupBy { it.sessionId }.values
            .map { it.minOf { a -> a.answeredAt }.atZone(zone).toLocalDate() }
            .toSet().size
        val lastDelayedRating = reviews.filter { it.sessionId in classified.delayedSessions }.maxByOrNull { it.reviewedAt }?.rating
        val accuracy = Accuracy.of(attempts)
        val delayedAccuracy = Accuracy.of(delayed)
        val delayedTypes = delayedAnswered.map { it.questionType }.toSet().size
        val policy = config.mastery
        val mastered = progress != null &&
            accuracy.answered >= policy.minAnswered &&
            sessionDays >= policy.minSessionDays &&
            delayedAccuracy.answered >= policy.minDelayedAnswers &&
            (delayedAccuracy.value ?: 0.0) >= policy.minDelayedAccuracy &&
            delayedTypes >= policy.minDelayedQuestionTypes &&
            lastDelayedRating != null && lastDelayedRating != ReviewRating.AGAIN
        val mastery = when {
            progress == null -> ContrastMastery.NEW
            mastered -> ContrastMastery.MASTERED
            scheduler.isLongTerm(progress.stepIndex) -> ContrastMastery.REVIEWING
            else -> ContrastMastery.LEARNING
        }
        return GroupMastery(
            groupId = groupId,
            mastery = mastery,
            accuracy = accuracy,
            skipped = attempts.count { it.outcome == AnswerOutcome.SKIPPED },
            sessionDays = sessionDays,
            delayedAccuracy = delayedAccuracy,
            freshDelayedAccuracy = Accuracy.of(attempts.filter { it.requestId in classified.fresh }),
            delayedQuestionTypes = delayedTypes,
            lastDelayedRating = lastDelayedRating,
            dueAt = progress?.dueAt,
        )
    }
}
