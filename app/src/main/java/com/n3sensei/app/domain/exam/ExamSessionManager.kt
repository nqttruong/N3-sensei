package com.n3sensei.app.domain.exam

import com.n3sensei.app.domain.repository.ExamRepository

/** An attempt in progress with everything needed to show it, as saved. */
data class ExamSession(
    val set: ExamSet,
    val attempt: ExamAttempt,
    val answers: Map<String, ExamAnswer>,
    val flagged: Set<String>,
    /** Index in [ExamSet.questions] of the question the learner is on. */
    val currentIndex: Int,
) {
    val questions: List<ExamQuestion> get() = set.questions
}

/** A set about to be taken, and its unfinished attempt (null when none). */
data class PreparedExam(val set: ExamSet, val unfinished: ExamAttempt?)

sealed interface OpenedSession {
    data class Active(val session: ExamSession) : OpenedSession

    /** Over: submitted (perhaps just now, because its time ran out while away) or abandoned. */
    data class Finished(val result: AttemptResult) : OpenedSession

    data object Missing : OpenedSession
}

/**
 * Starting, resuming and moving through attempts. Everything lives in the database, so an
 * attempt survives the app being closed, the process being killed or the screen recreated; when
 * it is opened again after its time ran out, it is submitted as of its deadline instead.
 */
class ExamSessionManager(private val exams: ExamRepository, private val submission: ExamSubmissionManager) {

    /**
     * A set before its attempt starts, with its latest attempt still running if any (to offer
     * continuing it). Attempts that ran out of time are submitted first. Null if no such set.
     */
    suspend fun prepare(examSetId: String): PreparedExam? {
        val set = exams.getExamSet(examSetId) ?: return null
        return PreparedExam(set, resumable(examSetId).firstOrNull())
    }

    /** A new attempt of the set, on its first question; null if the set does not exist. */
    suspend fun start(examSetId: String): ExamSession? {
        val set = exams.getExamSet(examSetId) ?: return null
        val attempt = exams.startAttempt(examSetId) ?: return null
        return ExamSession(set, attempt, emptyMap(), emptySet(), 0)
    }

    suspend fun open(attemptId: String): OpenedSession {
        val attempt = exams.getAttempt(attemptId) ?: return OpenedSession.Missing
        if (attempt.status != AttemptStatus.IN_PROGRESS) return finished(attemptId)
        submission.submitIfExpired(attempt)?.let { return finished(attemptId) }
        // The set was deleted meanwhile: it cannot be shown, so it is submitted with the answers it has.
        val set = exams.getExamSet(attempt.examSetId) ?: run {
            submission.submit(attemptId)
            return finished(attemptId)
        }
        val answers = exams.getAnswers(attemptId).associateBy { it.questionId }
        val flagged = exams.getFlags(attemptId).toSet()
        val index = set.questions.indexOfFirst { it.id == attempt.currentQuestionId }.coerceAtLeast(0)
        return OpenedSession.Active(ExamSession(set, attempt, answers, flagged, index))
    }

    /**
     * Attempts that can be continued, latest first. Those whose time ran out while the app was
     * closed are submitted first and are not among them.
     */
    suspend fun resumable(examSetId: String? = null): List<ExamAttempt> {
        submission.submitExpired()
        return exams.getInProgressAttempts().filter { examSetId == null || it.examSetId == examSetId }
    }

    /** Saves the question the learner is on; false when the attempt is over. */
    suspend fun moveTo(attemptId: String, questionId: String): Boolean = exams.setCurrentQuestion(attemptId, questionId)

    private suspend fun finished(attemptId: String): OpenedSession =
        exams.getResult(attemptId)?.let { OpenedSession.Finished(it) } ?: OpenedSession.Missing
}
