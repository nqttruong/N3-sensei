package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.n3sensei.app.data.local.entity.ErrorRecordEntity
import com.n3sensei.app.data.local.entity.ErrorReviewLogEntity
import com.n3sensei.app.data.local.entity.ReviewItemEntity
import kotlinx.coroutines.flow.Flow

/** An error with its review state (null only for records that never got one). */
data class ErrorWithReview(
    @Embedded val error: ErrorRecordEntity,
    @Embedded(prefix = "r_") val review: ReviewItemEntity?,
)

/** The error notebook: errors, their flags and notes, their reviews and review history. */
@Dao
abstract class ErrorDao {

    @Query(
        """
        SELECT e.*, r.id AS r_id, r.error_record_id AS r_error_record_id, r.next_review_at AS r_next_review_at,
            r.interval_minutes AS r_interval_minutes, r.step_index AS r_step_index, r.review_count AS r_review_count,
            r.success_count AS r_success_count, r.last_reviewed_at AS r_last_reviewed_at, r.review_status AS r_review_status
        FROM error_records e LEFT JOIN review_items r ON r.error_record_id = e.id
        ORDER BY e.last_wrong_at DESC, e.id DESC
        """,
    )
    abstract fun observeAll(): Flow<List<ErrorWithReview>>

    @Query(
        """
        SELECT e.*, r.id AS r_id, r.error_record_id AS r_error_record_id, r.next_review_at AS r_next_review_at,
            r.interval_minutes AS r_interval_minutes, r.step_index AS r_step_index, r.review_count AS r_review_count,
            r.success_count AS r_success_count, r.last_reviewed_at AS r_last_reviewed_at, r.review_status AS r_review_status
        FROM error_records e LEFT JOIN review_items r ON r.error_record_id = e.id
        WHERE e.id = :id
        """,
    )
    abstract fun observe(id: Long): Flow<ErrorWithReview?>

    @Query("SELECT * FROM error_records WHERE id = :id")
    abstract suspend fun getError(id: Long): ErrorRecordEntity?

    @Query("SELECT id FROM error_records WHERE question_id = :questionId")
    abstract suspend fun getErrorId(questionId: String): Long?

    @Update
    abstract suspend fun updateError(row: ErrorRecordEntity)

    @Query("SELECT * FROM review_items WHERE error_record_id = :errorId")
    abstract suspend fun getReview(errorId: Long): ReviewItemEntity?

    @Insert
    abstract suspend fun insertReview(row: ReviewItemEntity): Long

    @Update
    abstract suspend fun updateReview(row: ReviewItemEntity)

    @Insert
    abstract suspend fun insertLog(row: ErrorReviewLogEntity): Long

    @Query("SELECT * FROM error_review_log WHERE error_record_id = :errorId ORDER BY reviewed_at DESC, id DESC")
    abstract fun observeLog(errorId: Long): Flow<List<ErrorReviewLogEntity>>

    /** Open errors whose review is due at [now] (overdue ones included). */
    @Query(
        """
        SELECT COUNT(*) FROM error_records e JOIN review_items r ON r.error_record_id = e.id
        WHERE e.status = 'ACTIVE' AND r.review_status != 'MASTERED' AND r.next_review_at <= :now
        """,
    )
    abstract fun observeDueCount(now: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM error_review_log
        WHERE error_record_id = :errorId AND counted = 1 AND correct = 1
            AND question_kind IS NOT NULL AND question_kind != 'ORIGINAL' AND reviewed_at >= :since
            AND reviewed_at > (SELECT COALESCE(MAX(reviewed_at), -1) FROM error_review_log WHERE error_record_id = :errorId AND correct = 0)
        """,
    )
    abstract suspend fun countPassedOtherQuestions(errorId: Long, since: Long): Int

    /** One review, all at once: the log row, the new review state and the error's status. */
    @Transaction
    open suspend fun recordReview(error: ErrorRecordEntity, review: ReviewItemEntity, log: ErrorReviewLogEntity) {
        if (review.id == 0L) insertReview(review) else updateReview(review)
        updateError(error)
        insertLog(log)
    }
}
