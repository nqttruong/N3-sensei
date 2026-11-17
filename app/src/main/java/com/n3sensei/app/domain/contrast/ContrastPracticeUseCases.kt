package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.repository.RecordAnswerResult
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

sealed interface StartPracticeResult {
    /** The ACTIVE session of the same scope, resumed; [unavailablePositions] lost their question in a pack update (never counted as wrong). */
    data class Resumed(val session: ContrastSession, val unavailablePositions: Set<Int>) : StartPracticeResult

    data class Started(val session: ContrastSession) : StartPracticeResult

    /** Nothing due, no new group allowed today, or the chosen group has no question. */
    data object NothingToPractice : StartPracticeResult
}

/**
 * Starts a session or resumes the ACTIVE one: one decision point, so two ACTIVE sessions never
 * exist. A new session abandons the old one (in the repository's transaction); the old one's
 * unanswered positions stay PENDING, never wrong.
 */
class StartOrResumePractice(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val config: ContrastConfig = ContrastConfig(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(scope: PracticeScope, now: Instant, zone: ZoneId): StartPracticeResult {
        val active = practice.activeSession()
        if (active != null && active.scope == scope && Duration.between(active.lastActivityAt, now) <= config.resumeWindow) {
            val answered = practice.attempts(active.id).map { it.position }.toSet()
            val unavailable = active.items
                .filter { it.position !in answered && content.getQuestion(it.questionId) == null }
                .map { it.position }.toSet()
            return StartPracticeResult.Resumed(active, unavailable)
        }
        val pack = content.installedPack() ?: return StartPracticeResult.NothingToPractice
        val groupIds = when (scope) {
            is PracticeScope.Group -> listOf(scope.groupId)
            PracticeScope.Due -> dueThenNew(ContrastFilter(), now, zone)
            is PracticeScope.Type -> dueThenNew(ContrastFilter(contentType = scope.contentType), now, zone)
        }
        val questions = groupIds.associateWith { content.getQuestions(it) }
        val answered = groupIds.associateWith { practice.lastAnsweredAt(it) }
        val items = ContrastSessionPlanner.plan(groupIds, questions::getValue, answered::getValue, config.questionsPerBlock)
        if (items.isEmpty()) return StartPracticeResult.NothingToPractice
        val session = ContrastSession(newId(), scope, SessionStatus.ACTIVE, now, now, pack.contentVersion, items)
        practice.createSession(session)
        return StartPracticeResult.Started(session)
    }

    private suspend fun dueThenNew(filter: ContrastFilter, now: Instant, zone: ZoneId): List<String> {
        val published = content.observeGroups(filter).first().map { it.id }.toSet()
        val due = progress.dueGroups(now, config.maxGroupsPerSession).filter { it in published }
        val introducedToday = progress.newGroupsIntroducedBetween(ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
        val newAllowed = (config.newGroupsPerDay - introducedToday).coerceAtLeast(0)
        val fresh = if (newAllowed == 0) emptyList() else progress.newGroups(newAllowed, filter)
        return (due + fresh).distinct().take(config.maxGroupsPerSession)
    }
}

enum class SubmitRejection {
    /** No ACTIVE session with this id (finished, abandoned or unknown). */
    NO_ACTIVE_SESSION,
    UNKNOWN_POSITION,

    /** The position already has another answer. */
    ALREADY_ANSWERED,

    /** The question left the pack after the session was planned. */
    QUESTION_UNAVAILABLE,

    /** The option is not one of the question's options. */
    INVALID_OPTION,
}

data class SubmitResult(
    val outcome: AnswerOutcome,
    val correctOptionId: String,
    val explanationVi: String,
    /** Every available question of this block now has an answer. */
    val blockCompleted: Boolean,
    /** The scheduling written with this answer; null when no block was completed or it gave no rating. */
    val review: ContrastReview?,
    /** A different question of the group was added at the end because this one was missed. */
    val retryAdded: Boolean,
    val sessionCompleted: Boolean,
)

sealed interface SubmitOutcome {
    data class Recorded(val result: SubmitResult) : SubmitOutcome

    /** The same request id was already stored: nothing written (safe to retry). */
    data object Duplicate : SubmitOutcome

    data class Rejected(val reason: SubmitRejection) : SubmitOutcome
}

/**
 * Grades and stores one answer. When the answer completes its group's block, the block's rating,
 * the new schedule and progress are computed here ([ContrastScheduler]) and written by the
 * repository in the same transaction as the attempt: scheduling and progress updates are part of
 * this use case, not separate public ones, so they can never run apart or twice.
 */
class SubmitContrastAnswer(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val config: ContrastConfig = ContrastConfig(),
) {
    private val scheduler = ContrastScheduler(config)

    suspend operator fun invoke(
        sessionId: String,
        position: Int,
        input: AnswerInput,
        requestId: String,
        now: Instant,
        zone: ZoneId,
    ): SubmitOutcome {
        val previous = practice.attempts(sessionId)
        if (previous.any { it.requestId == requestId }) return SubmitOutcome.Duplicate
        val session = practice.activeSession()?.takeIf { it.id == sessionId }
            ?: return SubmitOutcome.Rejected(SubmitRejection.NO_ACTIVE_SESSION)
        val item = session.items.getOrNull(position) ?: return SubmitOutcome.Rejected(SubmitRejection.UNKNOWN_POSITION)
        if (previous.any { it.position == position }) return SubmitOutcome.Rejected(SubmitRejection.ALREADY_ANSWERED)
        val question = content.getQuestion(item.questionId) ?: return SubmitOutcome.Rejected(SubmitRejection.QUESTION_UNAVAILABLE)
        if (input is AnswerInput.Chosen && question.option(input.optionId) == null) return SubmitOutcome.Rejected(SubmitRejection.INVALID_OPTION)
        val pack = content.installedPack() ?: return SubmitOutcome.Rejected(SubmitRejection.QUESTION_UNAVAILABLE)
        val group = content.getDetail(item.groupId)?.group ?: return SubmitOutcome.Rejected(SubmitRejection.QUESTION_UNAVAILABLE)

        val graded = ContrastAnswerChecker.grade(question, input)
        val attempt = ContrastAttempt(
            requestId = requestId,
            sessionId = sessionId,
            position = position,
            groupId = item.groupId,
            groupRevision = group.revision,
            questionId = question.id,
            questionRevision = question.revision,
            questionType = question.type,
            contentPackId = pack.id,
            contentVersion = pack.contentVersion,
            outcome = graded.outcome,
            chosenOptionId = graded.chosenOptionId,
            correctOptionId = question.correctOptionId,
            expectedItemId = graded.expectedItemId,
            chosenItemId = graded.chosenItemId,
            chosenText = graded.chosenText,
            correctText = graded.correctText,
            answeredAt = now,
        )

        val answers = previous.associateBy { it.position } + (position to attempt)
        val unavailable = session.items.filter { it.position !in answers && content.getQuestion(it.questionId) == null }.map { it.position }.toSet()
        val blockItems = item.blockIndex?.let { block -> session.items.filter { it.groupId == item.groupId && it.blockIndex == block } }.orEmpty()
        val blockCompleted = blockItems.isNotEmpty() && blockItems.all { it.position in answers || it.position in unavailable }
        val pending = if (blockCompleted) {
            val tally = BlockTally.of(blockItems.mapNotNull { answers[it.position]?.outcome })
            scheduler.schedule(item.groupId, sessionId, progress.progress(item.groupId), tally, now, zone, pack.contentVersion)
        } else {
            null
        }

        when (practice.recordAnswer(attempt, pending)) {
            RecordAnswerResult.DUPLICATE -> return SubmitOutcome.Duplicate
            RecordAnswerResult.ALREADY_ANSWERED -> return SubmitOutcome.Rejected(SubmitRejection.ALREADY_ANSWERED)
            RecordAnswerResult.RECORDED -> Unit
        }

        var items = session.items
        val retry = if (graded.outcome == AnswerOutcome.INCORRECT && item.blockIndex != null) {
            ContrastSessionPlanner.retryFor(question, content.getQuestions(item.groupId), session.items.map { it.questionId }.toSet())
        } else {
            null
        }
        if (retry != null) {
            val added = SessionItem(items.size, item.groupId, retry.id, retry.revision, blockIndex = null)
            practice.appendItems(sessionId, listOf(added), now)
            items = items + added
        }
        val sessionCompleted = items.all { it.position in answers || it.position in unavailable }
        if (sessionCompleted) practice.finishSession(sessionId, SessionStatus.COMPLETED, now)
        return SubmitOutcome.Recorded(
            SubmitResult(
                outcome = graded.outcome,
                correctOptionId = question.correctOptionId,
                explanationVi = question.explanationVi,
                blockCompleted = blockCompleted,
                review = pending?.review,
                retryAdded = retry != null,
                sessionCompleted = sessionCompleted,
            ),
        )
    }
}

/** A directed confusion within one group. */
data class GroupConfusion(val groupId: String, val confusion: Confusion)

data class PracticeResult(
    val session: ContrastSession,
    /** One state per position: answered outcome, PENDING (not done, not wrong) or UNAVAILABLE. */
    val states: List<PositionState>,
    val confusions: List<GroupConfusion>,
    /** Scheduling written during this session, one per completed block that gave a rating. */
    val reviews: List<ContrastReview>,
) {
    fun count(state: PositionState): Int = states.count { it == state }
}

/** The result screen of a session (finished or not). */
class GetPracticeResult(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
) {
    suspend operator fun invoke(sessionId: String): PracticeResult? {
        val session = practice.session(sessionId) ?: return null
        val attempts = practice.attempts(sessionId).associateBy { it.position }
        val states = session.items.map { item ->
            when (attempts[item.position]?.outcome) {
                AnswerOutcome.CORRECT -> PositionState.CORRECT
                AnswerOutcome.INCORRECT -> PositionState.INCORRECT
                AnswerOutcome.SKIPPED -> PositionState.SKIPPED
                null -> if (content.getQuestion(item.questionId) == null) PositionState.UNAVAILABLE else PositionState.PENDING
            }
        }
        val confusions = attempts.values.sortedBy { it.position }.mapNotNull { a -> a.confusion?.let { GroupConfusion(a.groupId, it) } }
        val reviews = session.items.map { it.groupId }.distinct().flatMap { g -> progress.reviews(g).filter { it.sessionId == sessionId } }
        return PracticeResult(session, states, confusions, reviews)
    }
}
