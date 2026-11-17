package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.contrast.Accuracy
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Which stored answers an aggregate covers. Null = no restriction. A [contentType] restricts to
 * groups currently published with that type (retired groups have no type any more, so they only
 * appear unfiltered: same rule as AnalyzeContrastMistakes).
 */
data class AttemptFilter(
    val contentType: ContrastContentType? = null,
    val questionType: ContrastQuestionType? = null,
    val groupId: String? = null,
)

/** Counts of stored answers. PENDING / UNAVAILABLE positions have no attempt, so they are never here. */
data class OutcomeCounts(val correct: Int, val incorrect: Int, val skipped: Int) {
    val total: Int get() = correct + incorrect + skipped

    /** correct / (correct + incorrect): skipped answers are not in the denominator. */
    val accuracy: Accuracy get() = Accuracy(correct, correct + incorrect)

    operator fun minus(other: OutcomeCounts) = OutcomeCounts(correct - other.correct, incorrect - other.incorrect, skipped - other.skipped)

    companion object {
        val NONE = OutcomeCounts(0, 0, 0)
    }
}

data class GroupOutcomes(val groupId: String, val counts: OutcomeCounts, val lastWrongAt: Instant?)

data class TypeOutcomes(val type: ContrastQuestionType, val counts: OutcomeCounts)

/**
 * One directed confusion (expected item → chosen item, stable item ids) inside one group.
 * [sessions] = distinct sessions in which it happened; [sessionsSince] = later sessions of the
 * same group with at least one non-skipped answer, after its last occurrence.
 */
data class DirectionCount(
    val groupId: String,
    val expectedItemId: String,
    val chosenItemId: String,
    val count: Int,
    val sessions: Int,
    val lastAt: Instant,
    val sessionsSince: Int,
)

/** Answers of one session (in the filter), dated by its first answer. */
data class SessionOutcomes(val sessionId: String, val firstAnsweredAt: Instant, val counts: OutcomeCounts)

/** One stored wrong answer as it was recorded (its own text snapshot, never re-graded). */
data class MistakeSnapshot(
    val groupId: String,
    val expectedItemId: String?,
    val chosenItemId: String?,
    val questionId: String,
    val questionRevision: Int,
    val contentVersion: Int,
    val questionType: ContrastQuestionType,
    val chosenText: String?,
    val correctText: String,
    val answeredAt: Instant,
)

/**
 * Read-only aggregates over stored Contrast answers, computed by the database (no full attempt
 * list in memory). Every count is over the immutable attempt rows: one row per request id, so an
 * idempotent retry is never counted twice.
 */
interface ContrastAnalyticsRepository {
    /** Emits whenever answers are stored (progress and reviews are only written with an answer). */
    fun observeChanges(): Flow<Int>

    suspend fun totals(filter: AttemptFilter = AttemptFilter()): OutcomeCounts

    /** Most incorrect first. */
    suspend fun byGroup(filter: AttemptFilter = AttemptFilter()): List<GroupOutcomes>

    suspend fun byQuestionType(filter: AttemptFilter = AttemptFilter()): List<TypeOutcomes>

    /** Most frequent first. A → B and B → A are separate rows. */
    suspend fun directions(filter: AttemptFilter = AttemptFilter()): List<DirectionCount>

    /** The latest [limit] sessions having answers in the filter, newest first. */
    suspend fun recentSessions(filter: AttemptFilter = AttemptFilter(), limit: Int): List<SessionOutcomes>

    suspend fun latestExample(groupId: String, expectedItemId: String, chosenItemId: String): MistakeSnapshot?

    /** The latest [limit] wrong answers in the filter, newest first. */
    suspend fun recentMistakes(filter: AttemptFilter = AttemptFilter(), limit: Int): List<MistakeSnapshot>

    /** The group of the most recent answer among the published groups (retired groups are skipped). */
    suspend fun lastAnsweredGroup(): String?
}
