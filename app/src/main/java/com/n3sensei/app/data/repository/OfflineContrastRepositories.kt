package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.SearchText
import com.n3sensei.app.data.local.SqlLike
import com.n3sensei.app.data.local.dao.ContrastDao
import com.n3sensei.app.data.local.entity.ContrastAttemptEntity
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.ContrastFilter
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastProgress
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastReview
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.InstalledContrastPack
import com.n3sensei.app.domain.contrast.PendingReview
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastPack
import com.n3sensei.app.domain.repository.AttemptFilter
import com.n3sensei.app.domain.repository.ContrastAnalyticsRepository
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.DirectionCount
import com.n3sensei.app.domain.repository.GroupOutcomes
import com.n3sensei.app.domain.repository.MistakeSnapshot
import com.n3sensei.app.domain.repository.OutcomeCounts
import com.n3sensei.app.domain.repository.SessionOutcomes
import com.n3sensei.app.domain.repository.TypeOutcomes
import com.n3sensei.app.domain.repository.ContrastOverview
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.repository.RecordAnswerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private fun ContrastFilter.pattern(): String? =
    query?.let(SearchText::normalize)?.takeIf { it.isNotBlank() }?.let(SqlLike::contains)

/** Room-backed Contrast content (read side) and the write entry point for the importer (M4). */
class OfflineContrastContentRepository(private val dao: ContrastDao) : ContrastContentRepository {

    override fun observeGroups(filter: ContrastFilter): Flow<List<ContrastGroup>> =
        dao.observeGroups(filter.contentType, filter.jlptLevel, filter.pattern()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getDetail(groupId: String): ContrastDetail? {
        val row = dao.groupWithItems(groupId) ?: return null
        val pack = dao.pack(row.group.packId) ?: return null
        return detailOf(row, dao.notes(groupId), dao.examples(groupId), pack)
    }

    override suspend fun getQuestions(groupId: String): List<ContrastQuestion> {
        val questions = dao.questions(groupId)
        val options = dao.options(questions.map { it.id }).groupBy { it.questionId }
        return questions.map { it.toDomain(options[it.id].orEmpty()) }
    }

    override suspend fun getQuestion(questionId: String): ContrastQuestion? =
        dao.question(questionId)?.let { it.toDomain(dao.options(listOf(it.id))) }

    override suspend fun installedPack(): InstalledContrastPack? =
        dao.installedPack()?.let { InstalledContrastPack(it.id, it.contentVersion, it.licenseVerified, dao.groupIds(it.id).toSet()) }

    override suspend fun retiredGroup(groupId: String): RetiredGroup? = dao.retiredGroup(groupId)?.toDomain()

    /**
     * Installs a VALIDATED pack (no ERROR) in one transaction, replacing the content of the same
     * pack id. Learner data is never touched. Called by the importer (M4); not by the UI.
     */
    suspend fun replaceInstalledPack(pack: ValidatedContrastPack, schemaVersion: Int, now: Instant) {
        dao.replacePack(pack.toRows(now, schemaVersion))
    }
}

/** Sessions and answers. [recordAnswer] is one Room transaction (attempt + review + progress + session activity). */
class OfflineContrastPracticeRepository(private val dao: ContrastDao) : ContrastPracticeRepository {

    override suspend fun activeSession(): ContrastSession? =
        dao.activeSession()?.let { it.toDomain(dao.sessionItems(it.id)) }

    override suspend fun session(sessionId: String): ContrastSession? =
        dao.session(sessionId)?.let { it.toDomain(dao.sessionItems(it.id)) }

    override suspend fun createSession(session: ContrastSession) {
        require(session.status == SessionStatus.ACTIVE) { "A new session is ACTIVE" }
        val (entity, items) = session.toEntities()
        dao.startSession(entity, items)
    }

    override suspend fun appendItems(sessionId: String, items: List<SessionItem>, now: Instant) {
        dao.appendSessionItems(sessionId, items.map { it.toEntity(sessionId) }, now.toEpochMilli())
    }

    override suspend fun recordAnswer(attempt: ContrastAttempt, review: PendingReview?): RecordAnswerResult =
        dao.recordAnswer(attempt.toEntity(), review?.review?.toEntity(), review?.progress?.toEntity())

    override suspend fun finishSession(sessionId: String, status: SessionStatus, now: Instant) {
        dao.finishSession(sessionId, status, now.toEpochMilli())
    }

    override suspend fun attempts(sessionId: String): List<ContrastAttempt> = dao.attemptsOfSession(sessionId).map { it.toDomain() }

    override suspend fun lastAnsweredAt(groupId: String): Map<String, Instant> =
        dao.lastAnswers(groupId).associate { it.questionId to Instant.ofEpochMilli(it.answeredAt) }

    override suspend fun attemptsBetween(from: Instant?, to: Instant?): List<ContrastAttempt> =
        dao.attemptsBetween(from?.toEpochMilli(), to?.toEpochMilli()).map { it.toDomain() }

    override suspend fun attemptsOfGroup(groupId: String): List<ContrastAttempt> = dao.attemptsOfGroup(groupId).map { it.toDomain() }
}

/** Progress, schedule and review history (written only by [OfflineContrastPracticeRepository.recordAnswer]). */
class OfflineContrastProgressRepository(private val dao: ContrastDao) : ContrastProgressRepository {

    override suspend fun progress(groupId: String): ContrastProgress? = dao.progress(groupId)?.toDomain()

    override suspend fun allProgress(): List<ContrastProgress> = dao.allProgress().map { it.toDomain() }

    override suspend fun dueGroups(now: Instant, limit: Int): List<String> = dao.dueGroups(now.toEpochMilli(), limit)

    override suspend fun newGroups(limit: Int, filter: ContrastFilter): List<String> =
        dao.newGroups(limit, filter.contentType, filter.jlptLevel, filter.pattern())

    override suspend fun newGroupsIntroducedBetween(start: Instant, end: Instant): Int =
        dao.groupsFirstReviewedBetween(start.toEpochMilli(), end.toEpochMilli())

    override suspend fun reviews(groupId: String): List<ContrastReview> = dao.reviews(groupId).map { it.toDomain() }

    override fun observeOverview(now: Instant): Flow<ContrastOverview> =
        dao.observeOverview(now.toEpochMilli()).map { ContrastOverview(it.dueCount, it.scheduledCount, it.nextDueAt?.let(Instant::ofEpochMilli)) }
}

/** M7 analytics: every aggregate is one SQL query over contrast_attempts (no attempt list in memory). */
class OfflineContrastAnalyticsRepository(private val dao: ContrastDao) : ContrastAnalyticsRepository {

    override fun observeChanges(): Flow<Int> = dao.observeAttemptCount()

    override suspend fun totals(filter: AttemptFilter): OutcomeCounts =
        dao.outcomeTotals(filter.groupId, filter.questionType, filter.contentType).let { OutcomeCounts(it.correct, it.incorrect, it.skipped) }

    override suspend fun byGroup(filter: AttemptFilter): List<GroupOutcomes> =
        dao.outcomesByGroup(filter.groupId, filter.questionType, filter.contentType).map {
            GroupOutcomes(it.groupId, OutcomeCounts(it.correct, it.incorrect, it.skipped), it.lastWrongAt?.let(Instant::ofEpochMilli))
        }

    override suspend fun byQuestionType(filter: AttemptFilter): List<TypeOutcomes> =
        dao.outcomesByQuestionType(filter.groupId, filter.questionType, filter.contentType).map {
            TypeOutcomes(it.questionType, OutcomeCounts(it.correct, it.incorrect, it.skipped))
        }

    override suspend fun directions(filter: AttemptFilter): List<DirectionCount> =
        dao.confusionDirections(filter.groupId, filter.questionType, filter.contentType).map {
            DirectionCount(it.groupId, it.expectedItemId, it.chosenItemId, it.count, it.sessions, Instant.ofEpochMilli(it.lastAt), it.sessionsSince)
        }

    override suspend fun recentSessions(filter: AttemptFilter, limit: Int): List<SessionOutcomes> =
        dao.recentSessionOutcomes(filter.groupId, filter.questionType, filter.contentType, limit).map {
            SessionOutcomes(it.sessionId, Instant.ofEpochMilli(it.firstAt), OutcomeCounts(it.correct, it.incorrect, it.skipped))
        }

    override suspend fun latestExample(groupId: String, expectedItemId: String, chosenItemId: String): MistakeSnapshot? =
        dao.latestConfusion(groupId, expectedItemId, chosenItemId)?.toSnapshot()

    override suspend fun recentMistakes(filter: AttemptFilter, limit: Int): List<MistakeSnapshot> =
        dao.recentIncorrect(filter.groupId, filter.questionType, filter.contentType, limit).map { it.toSnapshot() }

    private fun ContrastAttemptEntity.toSnapshot() = MistakeSnapshot(
        groupId, expectedItemId, chosenItemId, questionId, questionRevision, contentVersion, questionType, chosenText, correctText, Instant.ofEpochMilli(answeredAt),
    )

    override suspend fun lastAnsweredGroup(): String? = dao.lastAnsweredGroup()
}
