package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.repository.AttemptFilter
import com.n3sensei.app.domain.repository.ContrastAnalyticsRepository
import com.n3sensei.app.domain.repository.DirectionCount
import com.n3sensei.app.domain.repository.GroupOutcomes
import com.n3sensei.app.domain.repository.MistakeSnapshot
import com.n3sensei.app.domain.repository.OutcomeCounts
import com.n3sensei.app.domain.repository.SessionOutcomes
import com.n3sensei.app.domain.repository.TypeOutcomes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory reference of [ContrastAnalyticsRepository] over the fake store's attempts: the same
 * definitions as the Room SQL (an instrumented test checks Room against the M1 analyzer).
 */
class FakeContrastAnalytics(private val content: FakeContrastContent, private val store: FakeContrastStore) : ContrastAnalyticsRepository {

    val changes = MutableStateFlow(0)

    override fun observeChanges(): Flow<Int> = changes

    private fun of(filter: AttemptFilter): List<ContrastAttempt> {
        val ofType = filter.contentType?.let { t -> content.pack.groups.map { it.detail.group }.filter { it.contentType == t }.map { it.id }.toSet() }
        return store.attempts.filter { a ->
            (filter.groupId == null || a.groupId == filter.groupId) &&
                (filter.questionType == null || a.questionType == filter.questionType) &&
                (ofType == null || a.groupId in ofType)
        }
    }

    private fun counts(list: List<ContrastAttempt>) = OutcomeCounts(
        list.count { it.outcome == AnswerOutcome.CORRECT },
        list.count { it.outcome == AnswerOutcome.INCORRECT },
        list.count { it.outcome == AnswerOutcome.SKIPPED },
    )

    override suspend fun totals(filter: AttemptFilter) = counts(of(filter))

    override suspend fun byGroup(filter: AttemptFilter) = of(filter).groupBy { it.groupId }.map { (g, list) ->
        GroupOutcomes(g, counts(list), list.filter { it.outcome == AnswerOutcome.INCORRECT }.maxOfOrNull { it.answeredAt })
    }.sortedWith(compareByDescending<GroupOutcomes> { it.counts.incorrect }.thenBy { it.groupId })

    override suspend fun byQuestionType(filter: AttemptFilter) = of(filter).groupBy { it.questionType }.map { (t, list) -> TypeOutcomes(t, counts(list)) }
        .sortedWith(compareByDescending<TypeOutcomes> { it.counts.incorrect }.thenBy { it.type })

    override suspend fun directions(filter: AttemptFilter): List<DirectionCount> =
        of(filter).filter { it.confusion != null }
            .groupBy { Triple(it.groupId, it.expectedItemId!!, it.chosenItemId!!) }
            .map { (k, list) ->
                val lastAt = list.maxOf { it.answeredAt }
                val lastSessions = list.filter { it.answeredAt == lastAt }.map { it.sessionId }.toSet()
                val since = store.attempts.filter { it.groupId == k.first && it.answeredAt.isAfter(lastAt) && it.outcome != AnswerOutcome.SKIPPED && it.sessionId !in lastSessions }
                    .map { it.sessionId }.toSet().size
                DirectionCount(k.first, k.second, k.third, list.size, list.map { it.sessionId }.toSet().size, lastAt, since)
            }
            .sortedWith(compareByDescending<DirectionCount> { it.count }.thenByDescending { it.lastAt }.thenBy { it.groupId }.thenBy { it.expectedItemId }.thenBy { it.chosenItemId })

    override suspend fun recentSessions(filter: AttemptFilter, limit: Int) = of(filter).groupBy { it.sessionId }
        .map { (s, list) -> SessionOutcomes(s, list.minOf { it.answeredAt }, counts(list)) }
        .sortedWith(compareByDescending<SessionOutcomes> { it.firstAnsweredAt }.thenByDescending { it.sessionId })
        .take(limit)

    private fun ContrastAttempt.snapshot() =
        MistakeSnapshot(groupId, expectedItemId, chosenItemId, questionId, questionRevision, contentVersion, questionType, chosenText, correctText, answeredAt)

    override suspend fun latestExample(groupId: String, expectedItemId: String, chosenItemId: String) =
        store.attempts.filter { it.groupId == groupId && it.outcome == AnswerOutcome.INCORRECT && it.expectedItemId == expectedItemId && it.chosenItemId == chosenItemId }
            .maxByOrNull { it.answeredAt }?.snapshot()

    override suspend fun recentMistakes(filter: AttemptFilter, limit: Int) =
        of(filter).filter { it.outcome == AnswerOutcome.INCORRECT }.sortedByDescending { it.answeredAt }.take(limit).map { it.snapshot() }

    override suspend fun lastAnsweredGroup(): String? {
        val published = content.pack.groups.map { it.detail.group.id }.toSet()
        return store.attempts.filter { it.groupId in published }.maxByOrNull { it.answeredAt }?.groupId
    }
}
