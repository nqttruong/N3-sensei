package com.n3sensei.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.ErrorRecordEntity
import com.n3sensei.app.data.local.entity.ExamAnswerEntity
import com.n3sensei.app.data.local.entity.ExamAttemptEntity
import com.n3sensei.app.data.local.entity.ExamAttemptItemEntity
import com.n3sensei.app.data.local.entity.ExamFlagEntity
import com.n3sensei.app.data.local.entity.ExamPassageEntity
import com.n3sensei.app.data.local.entity.ExamQuestionEntity
import com.n3sensei.app.data.local.entity.ExamQuestionItemEntity
import com.n3sensei.app.data.local.entity.ExamSectionEntity
import com.n3sensei.app.data.local.entity.ExamSetEntity
import com.n3sensei.app.data.local.entity.ReviewItemEntity
import com.n3sensei.app.domain.mistakes.ErrorReviewSchedule
import kotlinx.coroutines.flow.Flow

/** An exam set with everything it contains, as written and read in one transaction. */
data class ExamSetBundle(
    val set: ExamSetEntity,
    val sections: List<ExamSectionEntity>,
    val passages: List<ExamPassageEntity>,
    val questions: List<ExamQuestionEntity>,
    val items: List<ExamQuestionItemEntity>,
)

data class ExamSetSummaryRow(
    @Embedded val set: ExamSetEntity,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "submitted_attempts") val submittedAttempts: Int,
    @ColumnInfo(name = "last_correct") val lastCorrect: Int?,
    @ColumnInfo(name = "in_progress_attempts") val inProgressAttempts: Int,
)

data class ErrorReviewRow(
    @Embedded val error: ErrorRecordEntity,
    @Embedded(prefix = "r_") val review: ReviewItemEntity,
)

/** One kind of question in a set: its type and the section it is in (to tell the set's skills). */
data class ExamQuestionKindRow(
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    @ColumnInfo(name = "question_type") val questionType: String,
    val section: String,
)

/** A graded question of a submitted attempt, with when it was submitted and what was answered. */
data class GradedRow(
    @Embedded val item: ExamAttemptItemEntity,
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
    val status: String,
    @ColumnInfo(name = "selected_index") val selectedIndex: Int?,
    @ColumnInfo(name = "text_answer") val textAnswer: String?,
    @ColumnInfo(name = "time_spent_ms") val timeSpentMs: Long?,
)

/** A wrong (or unanswered) answer of a submitted attempt, to put in the error notebook. */
data class WrongAnswer(
    val questionId: String,
    val examSetId: String,
    val errorType: String,
    val questionSnapshot: String,
    val jlptLevel: String? = null,
    val skill: String? = null,
)

@Dao
abstract class ExamDao {

    // --- exam content ---------------------------------------------------------------------

    @Insert
    abstract suspend fun insertSet(row: ExamSetEntity)

    @Insert
    abstract suspend fun insertSections(rows: List<ExamSectionEntity>)

    @Insert
    abstract suspend fun insertPassages(rows: List<ExamPassageEntity>)

    @Insert
    abstract suspend fun insertQuestions(rows: List<ExamQuestionEntity>)

    @Insert
    abstract suspend fun insertQuestionItems(rows: List<ExamQuestionItemEntity>)

    /** Deletes the set's content (sections, passages, questions and links cascade); no learner data. */
    @Query("DELETE FROM exam_sets WHERE id = :id")
    abstract suspend fun deleteSet(id: String): Int

    /** Replaces a whole set at once: never half old, half new. */
    @Transaction
    open suspend fun replaceSet(bundle: ExamSetBundle) {
        deleteSet(bundle.set.id)
        insertSet(bundle.set)
        insertSections(bundle.sections)
        insertPassages(bundle.passages)
        insertQuestions(bundle.questions)
        insertQuestionItems(bundle.items)
    }

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM exam_questions q WHERE q.exam_set_id = s.id) AS question_count,
            (SELECT COUNT(*) FROM exam_attempts a WHERE a.exam_set_id = s.id AND a.status IN ('SUBMITTED', 'EXPIRED')) AS submitted_attempts,
            (SELECT a.correct_count FROM exam_attempts a WHERE a.exam_set_id = s.id AND a.status IN ('SUBMITTED', 'EXPIRED')
                ORDER BY a.submitted_at DESC LIMIT 1) AS last_correct,
            (SELECT COUNT(*) FROM exam_attempts a WHERE a.exam_set_id = s.id AND a.status = 'IN_PROGRESS') AS in_progress_attempts
        FROM exam_sets s ORDER BY s.jlpt_level, s.created_at, s.title
        """,
    )
    abstract fun observeSetSummaries(): Flow<List<ExamSetSummaryRow>>

    @Query(
        """
        SELECT DISTINCT q.exam_set_id, q.question_type, s.section
        FROM exam_questions q JOIN exam_sections s ON s.id = q.section_id
        """,
    )
    abstract fun observeQuestionKinds(): Flow<List<ExamQuestionKindRow>>

    @Query("SELECT * FROM exam_sets WHERE id = :id")
    abstract suspend fun getSet(id: String): ExamSetEntity?

    @Query("SELECT * FROM exam_sections WHERE exam_set_id = :setId ORDER BY section_order")
    abstract suspend fun getSections(setId: String): List<ExamSectionEntity>

    @Query("SELECT * FROM exam_passages WHERE exam_set_id = :setId ORDER BY id")
    abstract suspend fun getPassages(setId: String): List<ExamPassageEntity>

    /** In exam order: by section, then by position. */
    @Query(
        """
        SELECT q.* FROM exam_questions q JOIN exam_sections s ON s.id = q.section_id
        WHERE q.exam_set_id = :setId ORDER BY s.section_order, q.question_order
        """,
    )
    abstract suspend fun getQuestions(setId: String): List<ExamQuestionEntity>

    @Query("SELECT i.* FROM exam_question_items i JOIN exam_questions q ON q.id = i.question_id WHERE q.exam_set_id = :setId")
    abstract suspend fun getQuestionItems(setId: String): List<ExamQuestionItemEntity>

    @Transaction
    open suspend fun getBundle(setId: String): ExamSetBundle? {
        val set = getSet(setId) ?: return null
        return ExamSetBundle(set, getSections(setId), getPassages(setId), getQuestions(setId), getQuestionItems(setId))
    }

    @Query("SELECT * FROM exam_questions")
    abstract suspend fun getAllQuestions(): List<ExamQuestionEntity>

    @Query("SELECT * FROM exam_question_items")
    abstract suspend fun getAllQuestionItems(): List<ExamQuestionItemEntity>

    @Query("SELECT * FROM exam_passages")
    abstract suspend fun getAllPassages(): List<ExamPassageEntity>

    @Query("SELECT * FROM exam_questions WHERE id = :id")
    abstract suspend fun getQuestion(id: String): ExamQuestionEntity?

    @Query("SELECT * FROM exam_question_items WHERE question_id = :questionId")
    abstract suspend fun getItemsOf(questionId: String): List<ExamQuestionItemEntity>

    // --- attempts and answers -------------------------------------------------------------

    @Insert
    abstract suspend fun insertAttempt(row: ExamAttemptEntity)

    @Update
    abstract suspend fun updateAttempt(row: ExamAttemptEntity)

    @Query("SELECT * FROM exam_attempts WHERE id = :id")
    abstract suspend fun getAttempt(id: String): ExamAttemptEntity?

    @Query("SELECT * FROM exam_attempts WHERE (:setId IS NULL OR exam_set_id = :setId) ORDER BY started_at DESC")
    abstract fun observeAttempts(setId: String?): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempts WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC")
    abstract suspend fun getInProgressAttempts(): List<ExamAttemptEntity>

    @Query("UPDATE exam_attempts SET current_question_id = :questionId WHERE id = :attemptId AND status = 'IN_PROGRESS'")
    abstract suspend fun setCurrentQuestion(attemptId: String, questionId: String): Int

    @Upsert
    abstract suspend fun upsertFlag(row: ExamFlagEntity)

    @Query("DELETE FROM exam_flags WHERE attempt_id = :attemptId AND question_id = :questionId")
    abstract suspend fun deleteFlag(attemptId: String, questionId: String): Int

    @Query("SELECT question_id FROM exam_flags WHERE attempt_id = :attemptId ORDER BY flagged_at, question_id")
    abstract suspend fun getFlags(attemptId: String): List<String>

    @Insert
    abstract suspend fun insertAttemptItems(rows: List<ExamAttemptItemEntity>)

    @Query("SELECT * FROM exam_attempt_items WHERE attempt_id = :attemptId ORDER BY position")
    abstract suspend fun getAttemptItems(attemptId: String): List<ExamAttemptItemEntity>

    @Query(
        """
        SELECT i.*, a.exam_set_id, a.submitted_at, a.status, ans.selected_index, ans.text_answer, ans.time_spent_ms
        FROM exam_attempt_items i
        JOIN exam_attempts a ON a.id = i.attempt_id
        LEFT JOIN exam_answers ans ON ans.attempt_id = i.attempt_id AND ans.question_id = i.question_id
        WHERE a.status IN ('SUBMITTED', 'EXPIRED') AND a.submitted_at IS NOT NULL
        ORDER BY a.submitted_at, i.position
        """,
    )
    abstract suspend fun getGradedHistory(): List<GradedRow>

    @Upsert
    abstract suspend fun upsertAnswer(row: ExamAnswerEntity)

    @Query("SELECT * FROM exam_answers WHERE attempt_id = :attemptId AND question_id = :questionId")
    abstract suspend fun getAnswer(attemptId: String, questionId: String): ExamAnswerEntity?

    @Query("SELECT * FROM exam_answers WHERE attempt_id = :attemptId ORDER BY answered_at")
    abstract suspend fun getAnswers(attemptId: String): List<ExamAnswerEntity>

    // --- error notebook and its reviews ---------------------------------------------------

    @Insert
    abstract suspend fun insertError(row: ErrorRecordEntity): Long

    @Update
    abstract suspend fun updateError(row: ErrorRecordEntity)

    @Query("SELECT * FROM error_records WHERE id = :id")
    abstract suspend fun getError(id: Long): ErrorRecordEntity?

    @Query("SELECT * FROM error_records WHERE question_id = :questionId")
    abstract suspend fun getErrorForQuestion(questionId: String): ErrorRecordEntity?

    @Query("SELECT * FROM error_records WHERE (:status IS NULL OR status = :status) ORDER BY last_wrong_at DESC, id DESC")
    abstract fun observeErrors(status: String?): Flow<List<ErrorRecordEntity>>

    @Query("DELETE FROM error_records WHERE id = :id")
    abstract suspend fun deleteError(id: Long): Int

    @Insert
    abstract suspend fun insertReviewItem(row: ReviewItemEntity): Long

    @Update
    abstract suspend fun updateReviewItem(row: ReviewItemEntity)

    @Query("SELECT * FROM review_items WHERE error_record_id = :errorId")
    abstract suspend fun getReviewItemFor(errorId: Long): ReviewItemEntity?

    @Query(
        """
        SELECT e.*, r.id AS r_id, r.error_record_id AS r_error_record_id, r.next_review_at AS r_next_review_at,
            r.interval_minutes AS r_interval_minutes, r.step_index AS r_step_index, r.review_count AS r_review_count,
            r.success_count AS r_success_count, r.last_reviewed_at AS r_last_reviewed_at, r.review_status AS r_review_status
        FROM error_records e JOIN review_items r ON r.error_record_id = e.id
        WHERE e.status = 'ACTIVE' AND r.next_review_at <= :now
        ORDER BY r.next_review_at, e.id
        """,
    )
    abstract fun observeDueReviews(now: Long): Flow<List<ErrorReviewRow>>

    /**
     * Ends an attempt and records its mistakes, all in one transaction: a question already in
     * the notebook is counted again and its review starts over; a new one gets a record and a
     * review item. Either way the first review is a day later (ErrorReviewSchedule). A record that was mastered or archived becomes active.
     */
    @Transaction
    open suspend fun submit(attempt: ExamAttemptEntity, items: List<ExamAttemptItemEntity>, wrong: List<WrongAnswer>, now: Long) {
        updateAttempt(attempt)
        insertAttemptItems(items)
        wrong.forEach { w ->
            val existing = getErrorForQuestion(w.questionId)
            val errorId = if (existing == null) {
                insertError(
                    ErrorRecordEntity(
                        questionId = w.questionId, examSetId = w.examSetId, errorType = w.errorType, errorCount = 1,
                        firstWrongAt = now, lastWrongAt = now, masteryLevel = 0, status = "ACTIVE", notes = null,
                        questionSnapshot = w.questionSnapshot, jlptLevel = w.jlptLevel, skill = w.skill,
                    ),
                )
            } else {
                updateError(
                    existing.copy(
                        errorType = w.errorType, errorCount = existing.errorCount + 1, lastWrongAt = now,
                        masteryLevel = 0, status = "ACTIVE", questionSnapshot = w.questionSnapshot,
                        // Missed again: "understood" no longer holds; level and skill as of this set.
                        understoodAt = null, jlptLevel = w.jlptLevel ?: existing.jlptLevel, skill = w.skill ?: existing.skill,
                    ),
                )
                existing.id
            }
            val review = getReviewItemFor(errorId)
            if (review == null) {
                insertReviewItem(
                    ReviewItemEntity(
                        errorRecordId = errorId, nextReviewAt = ErrorReviewSchedule.firstDue(now),
                        intervalMinutes = ErrorReviewSchedule.LADDER_DAYS.first() * ErrorReviewSchedule.DAY_MINUTES, stepIndex = 0,
                        reviewCount = 0, successCount = 0, lastReviewedAt = null, reviewStatus = "NEW",
                    ),
                )
            } else {
                // Review counts are history: kept. The step starts over, first review a day later.
                updateReviewItem(
                    review.copy(
                        nextReviewAt = ErrorReviewSchedule.firstDue(now),
                        intervalMinutes = ErrorReviewSchedule.LADDER_DAYS.first() * ErrorReviewSchedule.DAY_MINUTES,
                        stepIndex = 0, reviewStatus = "LEARNING",
                    ),
                )
            }
        }
    }
}
