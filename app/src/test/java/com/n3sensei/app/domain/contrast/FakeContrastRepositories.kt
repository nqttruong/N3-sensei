package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.contrast.validation.ValidatedContrastPack
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastOverview
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.repository.ContrastSourceResolver
import com.n3sensei.app.domain.repository.RecordAnswerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/** In-memory content from a validated pack. [pack] can be swapped to simulate a pack update. */
class FakeContrastContent(var pack: ValidatedContrastPack) : ContrastContentRepository {
    private val packs = MutableStateFlow(pack)

    fun update(newPack: ValidatedContrastPack) {
        pack = newPack
        packs.value = newPack
    }

    override fun observeGroups(filter: ContrastFilter): Flow<List<ContrastGroup>> = packs.map { p ->
        p.groups.map { it.detail.group }.filter { g ->
            (filter.contentType == null || g.contentType == filter.contentType) &&
                (filter.jlptLevel == null || g.jlptLevel == filter.jlptLevel) &&
                (filter.query == null || g.title.contains(filter.query) || g.itemLabels.any { it.contains(filter.query) })
        }
    }

    override suspend fun getDetail(groupId: String) = pack.groups.firstOrNull { it.detail.group.id == groupId }?.detail
    override suspend fun getQuestions(groupId: String) = pack.groups.firstOrNull { it.detail.group.id == groupId }?.questions.orEmpty()
    override suspend fun getQuestion(questionId: String) = pack.groups.flatMap { it.questions }.firstOrNull { it.id == questionId }
    override suspend fun installedPack() = InstalledContrastPack(pack.id, pack.contentVersion, pack.licenseVerified, pack.groups.map { it.detail.group.id }.toSet())
    override suspend fun retiredGroup(groupId: String) = pack.retiredGroups.firstOrNull { it.id == groupId }
}

class FakeSourceResolver(private val statuses: Map<String, ResolvedSource> = emptyMap()) : ContrastSourceResolver {
    var calls = 0
    override suspend fun resolve(items: List<ContrastItem>): Map<String, ResolvedSource> {
        calls++
        return items.associate { it.id to (statuses[it.id] ?: ResolvedSource(SourceRefStatus.OK, it.source.sourceContentId)) }
    }
}

/**
 * Practice + progress in memory, with the contract's atomicity: an answer and its review/progress
 * are applied together or not at all ([failNextWrite] simulates a failing transaction).
 */
class FakeContrastStore(private val content: FakeContrastContent) : ContrastPracticeRepository, ContrastProgressRepository {
    val sessions = linkedMapOf<String, ContrastSession>()
    val attempts = mutableListOf<ContrastAttempt>()
    val progressRows = linkedMapOf<String, ContrastProgress>()
    val reviewRows = mutableListOf<ContrastReview>()
    var failNextWrite = false
    private var reviewId = 0L

    override suspend fun activeSession() = sessions.values.firstOrNull { it.status == SessionStatus.ACTIVE }
    override suspend fun session(sessionId: String) = sessions[sessionId]

    override suspend fun createSession(session: ContrastSession) {
        sessions.replaceAll { _, s -> if (s.status == SessionStatus.ACTIVE) s.copy(status = SessionStatus.ABANDONED) else s }
        sessions[session.id] = session
    }

    override suspend fun appendItems(sessionId: String, items: List<SessionItem>, now: Instant) {
        val s = sessions.getValue(sessionId)
        sessions[sessionId] = s.copy(items = s.items + items, lastActivityAt = now)
    }

    override suspend fun recordAnswer(attempt: ContrastAttempt, review: PendingReview?): RecordAnswerResult {
        if (attempts.any { it.requestId == attempt.requestId }) return RecordAnswerResult.DUPLICATE
        if (attempts.any { it.sessionId == attempt.sessionId && it.position == attempt.position }) return RecordAnswerResult.ALREADY_ANSWERED
        if (failNextWrite) {
            failNextWrite = false
            error("simulated transaction failure")
        }
        attempts += attempt
        if (review != null) {
            reviewRows += review.review.copy(id = ++reviewId)
            progressRows[review.progress.groupId] = review.progress
        }
        sessions[attempt.sessionId]?.let { sessions[attempt.sessionId] = it.copy(lastActivityAt = attempt.answeredAt) }
        return RecordAnswerResult.RECORDED
    }

    override suspend fun finishSession(sessionId: String, status: SessionStatus, now: Instant) {
        sessions[sessionId] = sessions.getValue(sessionId).copy(status = status, lastActivityAt = now)
    }

    override suspend fun attempts(sessionId: String) = attempts.filter { it.sessionId == sessionId }.sortedBy { it.position }
    override suspend fun lastAnsweredAt(groupId: String) =
        attempts.filter { it.groupId == groupId }.groupBy { it.questionId }.mapValues { (_, l) -> l.maxOf { it.answeredAt } }
    override suspend fun attemptsBetween(from: Instant?, to: Instant?) =
        attempts.filter { (from == null || !it.answeredAt.isBefore(from)) && (to == null || it.answeredAt.isBefore(to)) }.sortedBy { it.answeredAt }
    override suspend fun attemptsOfGroup(groupId: String) = attempts.filter { it.groupId == groupId }.sortedBy { it.answeredAt }

    private fun published() = content.pack.groups.map { it.detail.group.id }

    override suspend fun progress(groupId: String) = progressRows[groupId]
    override suspend fun allProgress() = progressRows.values.toList()
    override suspend fun dueGroups(now: Instant, limit: Int) =
        progressRows.values.filter { it.groupId in published() && !it.dueAt.isAfter(now) }.sortedBy { it.dueAt }.map { it.groupId }.take(limit)
    override suspend fun newGroups(limit: Int, filter: ContrastFilter) =
        content.pack.groups.map { it.detail.group }
            .filter { it.id !in progressRows && (filter.contentType == null || it.contentType == filter.contentType) }
            .map { it.id }.take(limit)
    override suspend fun newGroupsIntroducedBetween(start: Instant, end: Instant) =
        reviewRows.groupBy { it.groupId }.values.count { list -> list.minOf { it.reviewedAt }.let { !it.isBefore(start) && it.isBefore(end) } }
    override suspend fun reviews(groupId: String) = reviewRows.filter { it.groupId == groupId }
    override fun observeOverview(now: Instant): Flow<ContrastOverview> {
        val scheduled = progressRows.values.filter { it.groupId in published() }
        return flowOf(ContrastOverview(scheduled.count { !it.dueAt.isAfter(now) }, scheduled.size, scheduled.filter { it.dueAt.isAfter(now) }.minOfOrNull { it.dueAt }))
    }
}
