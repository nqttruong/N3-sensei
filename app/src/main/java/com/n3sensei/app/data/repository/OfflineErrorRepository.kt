package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.dao.ErrorWithReview
import com.n3sensei.app.data.local.entity.ErrorRecordEntity
import com.n3sensei.app.data.local.entity.ErrorReviewLogEntity
import com.n3sensei.app.data.local.entity.ReviewItemEntity
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamGrading
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.exam.ReviewItem
import com.n3sensei.app.domain.exam.ReviewStatus
import com.n3sensei.app.domain.mistakes.ErrorReviewEntry
import com.n3sensei.app.domain.mistakes.ErrorReviewSchedule
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.ReviewQuestionKind
import com.n3sensei.app.domain.repository.ErrorRepository
import com.n3sensei.app.domain.repository.ReviewResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock

class OfflineErrorRepository(database: N3Database, private val clock: Clock) : ErrorRepository {

    private val dao = database.errorDao()

    // Each entry decodes its question snapshot: off the main thread.
    override fun observeErrors(): Flow<List<NotebookError>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeError(id: Long): Flow<NotebookError?> = dao.observe(id).map { it?.toDomain() }

    override fun observeReviewLog(id: Long): Flow<List<ErrorReviewEntry>> = dao.observeLog(id).map { rows ->
        rows.map {
            ErrorReviewEntry(
                it.reviewedAt, it.correct, it.counted, it.stepBefore, it.stepAfter, it.questionId,
                ReviewQuestionKind.entries.firstOrNull { k -> k.name == it.questionKind } ?: ReviewQuestionKind.ORIGINAL,
            )
        }
    }

    override suspend fun findByQuestion(questionId: String): Long? = dao.getErrorId(questionId)

    override suspend fun setImportant(id: Long, important: Boolean): Boolean = change(id) { it.copy(isImportant = important) }

    override suspend fun setNote(id: Long, note: String?): Boolean = change(id) { it.copy(notes = note?.trim()?.ifEmpty { null }) }

    override suspend fun setUnderstood(id: Long, understood: Boolean): Boolean =
        change(id) { it.copy(understoodAt = if (understood) clock.millis() else null) }

    override fun observeDueCount(nowMillis: Long): Flow<Int> = dao.observeDueCount(nowMillis)

    override suspend fun review(id: Long, question: ExamQuestion, kind: ReviewQuestionKind, answer: AnswerInput): ReviewResult? {
        val row = dao.getError(id) ?: return null
        val now = clock.millis()
        val correct = ExamGrading.isCorrect(question, answer)
        val current = dao.getReview(id) ?: ReviewItemEntity(
            errorRecordId = id, nextReviewAt = now, intervalMinutes = 0, stepIndex = 0, reviewCount = 0, successCount = 0,
            lastReviewedAt = null, reviewStatus = ReviewStatus.NEW.name,
        )
        val outcome = ErrorReviewSchedule.review(
            current.stepIndex, current.nextReviewAt, correct, now,
            otherQuestion = kind != ReviewQuestionKind.ORIGINAL,
            passedAnotherQuestionBefore = dao.countPassedOtherQuestions(id, row.lastWrongAt) > 0,
        )
        val review = current.copy(
            nextReviewAt = outcome.nextReviewAt, intervalMinutes = outcome.intervalMinutes, stepIndex = outcome.step,
            reviewCount = current.reviewCount + 1, successCount = current.successCount + if (correct) 1 else 0, lastReviewedAt = now,
            reviewStatus = when {
                outcome.mastered -> ReviewStatus.MASTERED
                outcome.step >= 2 -> ReviewStatus.REVIEW
                else -> ReviewStatus.LEARNING
            }.name,
        )
        val updated = row.copy(
            masteryLevel = outcome.step,
            status = if (outcome.mastered) ErrorStatus.MASTERED.name else if (row.status == ErrorStatus.MASTERED.name) ErrorStatus.ACTIVE.name else row.status,
        )
        dao.recordReview(
            updated, review,
            ErrorReviewLogEntity(0, id, now, correct, outcome.counted, current.stepIndex, outcome.step, question.id, kind.name),
        )
        return ReviewResult(correct, outcome.counted, outcome.mastered, outcome.nextReviewAt, current.stepIndex, outcome.step, outcome.needsAnotherQuestion)
    }

    private suspend fun change(id: Long, edit: (ErrorRecordEntity) -> ErrorRecordEntity): Boolean {
        val row = dao.getError(id) ?: return false
        dao.updateError(edit(row))
        return true
    }

    private fun ErrorWithReview.toDomain(): NotebookError {
        val record = error.toDomain()
        val skill = enumOrNull<ExamSkill>(error.skill)
            ?: record.question?.type?.takeIf { it != QuestionType.OTHER }?.let { ExamSkill.of(it, ExamSectionKind.VOCABULARY) }
        return NotebookError(record, review?.toDomain(), error.isImportant, error.understoodAt, error.jlptLevel, skill)
    }

    private fun ErrorRecordEntity.toDomain() = ErrorRecord(
        id, questionId, examSetId, enumOrNull<ErrorType>(errorType) ?: ErrorType.UNKNOWN, errorCount, firstWrongAt, lastWrongAt,
        masteryLevel, enumOrNull<ErrorStatus>(status) ?: ErrorStatus.ACTIVE, notes, OfflineExamRepository.fromSnapshot(questionSnapshot),
    )

    private fun ReviewItemEntity.toDomain() = ReviewItem(
        id, errorRecordId, nextReviewAt, intervalMinutes, stepIndex, reviewCount, successCount, lastReviewedAt,
        enumOrNull<ReviewStatus>(reviewStatus) ?: ReviewStatus.NEW,
    )

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }
}
