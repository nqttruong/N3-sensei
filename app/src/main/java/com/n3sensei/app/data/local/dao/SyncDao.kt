package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.SyncCheckpointEntity
import com.n3sensei.app.data.local.entity.SyncedAttemptAnswerEntity
import com.n3sensei.app.data.local.entity.SyncedAttemptEntity
import com.n3sensei.app.data.local.entity.SyncedExamEntity
import com.n3sensei.app.data.local.entity.SyncedExplanationEntity
import com.n3sensei.app.data.local.entity.SyncedKnowledgeEntity
import com.n3sensei.app.data.local.entity.SyncedPassageEntity
import com.n3sensei.app.data.local.entity.SyncedQuestionEntity
import com.n3sensei.app.data.local.entity.SyncedReviewEventEntity
import com.n3sensei.app.data.local.entity.SyncedReviewItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SyncDao {
    @Query("SELECT * FROM sync_checkpoints WHERE id = :id")
    abstract suspend fun checkpoint(id: String): SyncCheckpointEntity?

    @Upsert abstract suspend fun putCheckpoint(value: SyncCheckpointEntity)
    @Upsert abstract suspend fun putExam(value: SyncedExamEntity)
    @Upsert abstract suspend fun putPassage(value: SyncedPassageEntity)
    @Upsert abstract suspend fun putQuestion(value: SyncedQuestionEntity)
    @Upsert abstract suspend fun putExplanation(value: SyncedExplanationEntity)
    @Upsert abstract suspend fun putAttempt(value: SyncedAttemptEntity)
    @Upsert abstract suspend fun putKnowledge(value: SyncedKnowledgeEntity)
    @Upsert abstract suspend fun putAnswer(value: SyncedAttemptAnswerEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertReviewItem(value: SyncedReviewItemEntity)
    @Query("UPDATE synced_review_items SET review_type=:type, status=:status, source_question_id=:questionId, source_question_ids_json=:questionIds, knowledge_id=:knowledgeId, related_knowledge_id=:relatedKnowledgeId, source_explanation_id=:explanationId, remote_version=:version, updated_cursor=:cursor, is_deleted=:deleted WHERE id=:id")
    abstract suspend fun updateReviewMetadata(id: String, type: String, status: String, questionId: String?, questionIds: String, knowledgeId: String?, relatedKnowledgeId: String?, explanationId: String?, version: Int, cursor: Long, deleted: Boolean)
    suspend fun putRemoteReview(value: SyncedReviewItemEntity) {
        insertReviewItem(value)
        updateReviewMetadata(value.id, value.reviewType, value.status, value.sourceQuestionId, value.sourceQuestionIdsJson, value.knowledgeId, value.relatedKnowledgeId, value.sourceExplanationId, value.remoteVersion, value.updatedCursor, value.isDeleted)
    }
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun addEvent(value: SyncedReviewEventEntity): Long
    @Query("UPDATE synced_review_items SET local_step_index=:step, local_interval_minutes=:interval, local_due_at=:due, local_last_reviewed_at=:reviewed, local_review_count=local_review_count+1 WHERE id=:id")
    abstract suspend fun updateLocalSchedule(id: String, step: Int?, interval: Long, due: Long, reviewed: Long)
    @Transaction
    open suspend fun recordOfflineReview(itemId: String, step: Int?, interval: Long, due: Long, reviewed: Long, event: SyncedReviewEventEntity) {
        if (addEvent(event) != -1L) updateLocalSchedule(itemId, step, interval, due, reviewed)
    }
    @Query("UPDATE synced_review_events SET uploaded_at = :at, last_error = NULL WHERE request_id IN (:ids)")
    abstract suspend fun acknowledge(ids: List<String>, at: Long)
    @Query("UPDATE synced_review_events SET attempt_count = attempt_count + 1, last_error = :error WHERE request_id IN (:ids)")
    abstract suspend fun failUpload(ids: List<String>, error: String)
    @Query("SELECT * FROM synced_review_events WHERE uploaded_at IS NULL ORDER BY created_at LIMIT :limit")
    abstract suspend fun pendingEvents(limit: Int = 100): List<SyncedReviewEventEntity>
    @Query("SELECT COUNT(*) FROM synced_review_events WHERE uploaded_at IS NULL")
    abstract fun observePendingCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM synced_review_events WHERE uploaded_at IS NULL")
    abstract suspend fun pendingCount(): Int
    @Query("SELECT COUNT(*) FROM synced_knowledge WHERE is_deleted = 0")
    abstract suspend fun knowledgeCount(): Int
    @Query("SELECT COUNT(*) FROM synced_knowledge WHERE is_deleted = 0 AND status = 'active'")
    abstract fun observeKnowledgeCount(): Flow<Int>
    @Query("SELECT * FROM synced_knowledge WHERE is_deleted = 0 AND status = 'active' ORDER BY title")
    abstract fun observeKnowledge(): Flow<List<SyncedKnowledgeEntity>>
    @Query("SELECT * FROM synced_review_items WHERE is_deleted = 0 AND status = 'active' ORDER BY COALESCE(local_due_at, 0), id")
    abstract fun observeReviewItems(): Flow<List<SyncedReviewItemEntity>>
    @Query("SELECT * FROM synced_questions WHERE is_deleted = 0 AND answer_verified = 1 AND id IN (SELECT question_id FROM synced_attempt_answers WHERE is_correct = 0 AND is_deleted = 0) ORDER BY question_number")
    abstract fun observeMistakes(): Flow<List<SyncedQuestionEntity>>
    @Query("SELECT * FROM synced_exams WHERE is_deleted = 0 ORDER BY title")
    abstract fun observeExams(): Flow<List<SyncedExamEntity>>
    @Query("SELECT * FROM synced_exams WHERE id = :id")
    abstract suspend fun syncedExam(id: String): SyncedExamEntity?
    @Query("SELECT * FROM synced_knowledge WHERE is_deleted = 0 AND status = 'active' ORDER BY title")
    abstract fun observeKnowledgeItems(): Flow<List<SyncedKnowledgeEntity>>
    @Query("SELECT aa.* FROM synced_attempt_answers aa LEFT JOIN synced_attempts a ON a.id = aa.attempt_id WHERE aa.question_id = :id AND aa.is_deleted = 0 ORDER BY COALESCE(a.completed_at, a.started_at, '') DESC LIMIT 1")
    abstract suspend fun latestAnswer(id: String): SyncedAttemptAnswerEntity?
    @Query("SELECT * FROM synced_passages WHERE id = :id AND is_deleted = 0")
    abstract suspend fun passage(id: String): SyncedPassageEntity?
    @Query("SELECT * FROM synced_questions WHERE id = :id AND is_deleted = 0")
    abstract suspend fun question(id: String): SyncedQuestionEntity?
    @Query("SELECT * FROM synced_explanations WHERE question_id = :id AND available = 1 ORDER BY updated_cursor DESC LIMIT 1")
    abstract suspend fun explanation(id: String): SyncedExplanationEntity?
    @Query("UPDATE synced_explanations SET available = 0, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteExplanation(id: String, cursor: Long)
    @Query("UPDATE synced_attempts SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteAttempt(id: String, cursor: Long)
    @Query("UPDATE synced_exams SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteExam(id: String, cursor: Long)
    @Query("UPDATE synced_passages SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deletePassage(id: String, cursor: Long)
    @Query("UPDATE synced_questions SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteQuestion(id: String, cursor: Long)
    @Query("UPDATE synced_knowledge SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteKnowledge(id: String, cursor: Long)
    @Query("UPDATE synced_review_items SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteReviewItem(id: String, cursor: Long)
    @Query("UPDATE synced_attempt_answers SET is_deleted = 1, updated_cursor = :cursor WHERE server_id = :id")
    abstract suspend fun deleteAttemptAnswer(id: String, cursor: Long)
    @Query("UPDATE synced_attempt_answers SET is_deleted = 1, updated_cursor = :cursor WHERE question_id = :id")
    abstract suspend fun deleteAttemptAnswersForQuestion(id: String, cursor: Long)
}
