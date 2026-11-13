package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** JLPT AI Review mirror rows live in a separate namespace/table family from bundled Room content. */
@Entity(tableName = "synced_exams", indices = [Index("updated_cursor")])
data class SyncedExamEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    val title: String,
    val category: String,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    val description: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    val status: String,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
)

@Entity(tableName = "synced_passages", indices = [Index("exam_id")])
data class SyncedPassageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "exam_id") val examId: String,
    val text: String,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
)

@Entity(
    tableName = "synced_questions",
    indices = [Index("exam_id"), Index("passage_id"), Index("updated_cursor")],
)
data class SyncedQuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "exam_id") val examId: String,
    @ColumnInfo(name = "passage_id") val passageId: String?,
    @ColumnInfo(name = "question_number") val questionNumber: Int,
    @ColumnInfo(name = "question_type") val questionType: String,
    val content: String,
    val options: List<String>,
    @ColumnInfo(name = "answer_index") val answerIndex: Int?,
    @ColumnInfo(name = "answer_verified") val answerVerified: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
)

@Entity(tableName = "synced_explanations", indices = [Index("question_id")])
data class SyncedExplanationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: String,
    @ColumnInfo(name = "explanation_json") val explanationJson: String,
    val available: Boolean,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
)

@Entity(tableName = "synced_attempts", indices = [Index("exam_id")])
data class SyncedAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "exam_id") val examId: String,
    @ColumnInfo(name = "started_at") val startedAt: String?,
    @ColumnInfo(name = "completed_at") val completedAt: String?,
    @ColumnInfo(name = "total_questions") val totalQuestions: Int,
    val source: String?,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
)

@Entity(tableName = "synced_knowledge", indices = [Index("knowledge_type"), Index("status")])
data class SyncedKnowledgeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "knowledge_type") val knowledgeType: String,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "details_json") val detailsJson: String,
    @ColumnInfo(name = "linked_question_ids_json") val linkedQuestionIdsJson: String,
    val status: String,
    @ColumnInfo(name = "merged_into_id") val mergedIntoId: String?,
    val version: Int,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
)

@Entity(
    tableName = "synced_attempt_answers",
    primaryKeys = ["attempt_id", "question_id"],
    indices = [Index("question_id"), Index("is_correct")],
)
data class SyncedAttemptAnswerEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "question_number") val questionNumber: Int,
    @ColumnInfo(name = "selected_index") val selectedIndex: Int?,
    @ColumnInfo(name = "correct_index") val correctIndex: Int?,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean?,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
)

/** Remote review item metadata plus an independent, device-local schedule. */
@Entity(tableName = "synced_review_items", indices = [Index("status"), Index("local_due_at")])
data class SyncedReviewItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "review_type") val reviewType: String,
    val status: String,
    @ColumnInfo(name = "source_question_id") val sourceQuestionId: String?,
    @ColumnInfo(name = "source_question_ids_json") val sourceQuestionIdsJson: String,
    @ColumnInfo(name = "knowledge_id") val knowledgeId: String?,
    @ColumnInfo(name = "related_knowledge_id") val relatedKnowledgeId: String?,
    @ColumnInfo(name = "source_explanation_id") val sourceExplanationId: String?,
    @ColumnInfo(name = "remote_version") val remoteVersion: Int,
    @ColumnInfo(name = "updated_cursor") val updatedCursor: Long,
    @ColumnInfo(name = "local_step_index") val localStepIndex: Int?,
    @ColumnInfo(name = "local_interval_minutes") val localIntervalMinutes: Long,
    @ColumnInfo(name = "local_due_at") val localDueAtEpochMillis: Long?,
    @ColumnInfo(name = "local_last_reviewed_at") val localLastReviewedAtEpochMillis: Long?,
    @ColumnInfo(name = "local_review_count") val localReviewCount: Int,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
)

@Entity(tableName = "synced_review_events", indices = [Index("uploaded_at"), Index("item_id")])
data class SyncedReviewEventEntity(
    @PrimaryKey @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "item_type") val itemType: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val rating: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAtEpochMillis: Long,
    @ColumnInfo(name = "interval_before_minutes") val intervalBeforeMinutes: Long,
    @ColumnInfo(name = "interval_after_minutes") val intervalAfterMinutes: Long,
    @ColumnInfo(name = "due_at_after") val dueAtAfterEpochMillis: Long,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "uploaded_at") val uploadedAtEpochMillis: Long?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
)

@Entity(tableName = "sync_checkpoints")
data class SyncCheckpointEntity(
    @PrimaryKey val id: String = CHECKPOINT_ID,
    val cursor: Long = 0,
    @ColumnInfo(name = "schema_version") val schemaVersion: String = "1.0",
    @ColumnInfo(name = "last_success_at") val lastSuccessAtEpochMillis: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "downloaded_count") val downloadedCount: Long = 0,
    @ColumnInfo(name = "last_upload_at") val lastUploadAtEpochMillis: Long? = null,
) {
    companion object { const val CHECKPOINT_ID = "jlpt-ai-review" }
}

const val JLPT_REMOTE_ID_PREFIX = "jlpt:"

fun namespaceServerId(serverId: String): String {
    require(serverId.isNotBlank())
    return "$JLPT_REMOTE_ID_PREFIX$serverId"
}
