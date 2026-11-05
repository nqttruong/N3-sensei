package com.n3sensei.app.domain.exam

import com.n3sensei.app.domain.repository.ExamRepository
import kotlinx.coroutines.flow.first

/** A submitted attempt with its report, the attempts of the same set and the comparison with the one before. */
data class ExamResultOverview(
    val report: ExamReport,
    /** Submitted attempts of the set, latest first, this one included. */
    val history: List<ExamAttempt>,
    /** Against the attempt submitted just before this one; null for the first. */
    val comparison: ExamComparison?,
    /** The set can still be taken again. */
    val setAvailable: Boolean,
)

/**
 * Results of submitted attempts, from what was stored when each was submitted: scoring a later
 * attempt never changes an earlier one, and taking a set again adds a result instead of
 * replacing one.
 */
class ExamResultManager(private val exams: ExamRepository) {

    suspend fun report(attemptId: String): ExamReport? = exams.getRecord(attemptId)?.let(ExamScoring::report)

    suspend fun overview(attemptId: String): ExamResultOverview? {
        val report = report(attemptId) ?: return null
        val attempt = report.attempt
        val history = exams.observeAttempts(attempt.examSetId).first()
            .filter { it.status.isSubmitted }
            .sortedByDescending { it.submittedAt ?: it.startedAt }
        val before = history.filter { other -> other.id != attempt.id && (other.submittedAt ?: 0) < (attempt.submittedAt ?: 0) }
            .maxByOrNull { it.submittedAt ?: 0 }
        val comparison = before?.let { report(it.id) }?.let { ExamScoring.compare(it, report) }
        return ExamResultOverview(report, history, comparison, exams.getExamSet(attempt.examSetId) != null)
    }
}
