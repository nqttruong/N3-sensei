package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.model.ReviewRating

// Contrast Lab (schema v16, docs/CONTRAST_LAB_ARCHITECTURE.md §10). Two groups of tables:
//
// - CONTENT, rooted at contrast_packs and replaced when a pack is installed (ON DELETE CASCADE
//   only inside the content tables). No foreign key to grammar / vocabulary / kanji (D3): items
//   reference them by (source_type, source_content_id) with a snapshot and a fingerprint.
// - LEARNER data (sessions, attempts, progress, reviews): no foreign key at all, so installing,
//   updating or retiring content never deletes history (D4). Rows whose group or question left
//   the pack stay and are read with their own snapshots.
//
// Enums are stored by name (Room's built-in enum support); never rename an entry.

// ---- Content -------------------------------------------------------------------------------------

@Entity(tableName = "contrast_packs")
data class ContrastPackEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    /** The pack's contentVersion (independent of the learner data's progress version). */
    @ColumnInfo(name = "content_version") val contentVersion: Int,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "published_at") val publishedAt: String,
    @ColumnInfo(name = "license_text") val licenseText: String,
    @ColumnInfo(name = "license_verified") val licenseVerified: Boolean,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
)

@Entity(
    tableName = "contrast_groups",
    foreignKeys = [ForeignKey(entity = ContrastPackEntity::class, parentColumns = ["id"], childColumns = ["pack_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("pack_id"), Index(value = ["content_type", "sort_order"])],
)
data class ContrastGroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "content_type") val contentType: ContrastContentType,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    val title: String,
    @ColumnInfo(name = "summary_vi") val summaryVi: String,
    val revision: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Normalized title, labels, readings and meanings (SearchText), for accent-insensitive search. */
    @ColumnInfo(name = "search_text") val searchText: String,
)

@Entity(
    tableName = "contrast_items",
    foreignKeys = [ForeignKey(entity = ContrastGroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["group_id", "sort_order"], unique = true), Index(value = ["source_type", "source_content_id"])],
)
data class ContrastItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val label: String,
    @ColumnInfo(name = "label_furigana") val labelFurigana: String?,
    @ColumnInfo(name = "source_type") val sourceType: ContrastContentType,
    @ColumnInfo(name = "source_content_id") val sourceContentId: String,
    @ColumnInfo(name = "source_pack_id") val sourcePackId: String,
    @ColumnInfo(name = "source_schema_version") val sourceSchemaVersion: Int,
    @ColumnInfo(name = "source_snapshot_json") val sourceSnapshotJson: String,
    @ColumnInfo(name = "source_fingerprint") val sourceFingerprint: String,
    /** ItemDetails (grammar / vocabulary / kanji) as JSON: display-only, never queried by field. */
    @ColumnInfo(name = "details_json") val detailsJson: String,
)

/** Display-only blocks of a group: theory, comparison rows, memory tips, common mistakes. */
@Entity(
    tableName = "contrast_notes",
    primaryKeys = ["group_id", "kind", "sort_order"],
    foreignKeys = [ForeignKey(entity = ContrastGroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
)
data class ContrastNoteEntity(
    @ColumnInfo(name = "group_id") val groupId: String,
    /** THEORY, COMPARISON, TIP or MISTAKE ([com.n3sensei.app.data.repository.ContrastNoteKind]). */
    val kind: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
)

@Entity(
    tableName = "contrast_examples",
    foreignKeys = [ForeignKey(entity = ContrastGroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["group_id", "sort_order"], unique = true)],
)
data class ContrastExampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "item_id") val itemId: String,
    val japanese: String,
    val furigana: String?,
    @ColumnInfo(name = "translation_vi") val translationVi: String,
)

@Entity(
    tableName = "contrast_questions",
    foreignKeys = [ForeignKey(entity = ContrastGroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["group_id", "sort_order"], unique = true), Index(value = ["group_id", "type"])],
)
data class ContrastQuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val revision: Int,
    val type: ContrastQuestionType,
    val difficulty: Int,
    @ColumnInfo(name = "target_item_id") val targetItemId: String,
    @ColumnInfo(name = "instruction_vi") val instructionVi: String,
    @ColumnInfo(name = "prompt_json") val promptJson: String?,
    @ColumnInfo(name = "context_json") val contextJson: String?,
    @ColumnInfo(name = "correction_json") val correctionJson: String?,
    @ColumnInfo(name = "correct_option_id") val correctOptionId: String,
    @ColumnInfo(name = "explanation_vi") val explanationVi: String,
)

@Entity(
    tableName = "contrast_options",
    primaryKeys = ["question_id", "option_id"],
    foreignKeys = [ForeignKey(entity = ContrastQuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.CASCADE)],
)
data class ContrastOptionEntity(
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "option_id") val optionId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val text: String,
    val furigana: String?,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "feedback_vi") val feedbackVi: String?,
)

/** A group retired by the installed pack: its learner data stays, it is just no longer offered. */
@Entity(
    tableName = "contrast_retired_groups",
    primaryKeys = ["pack_id", "group_id"],
    foreignKeys = [ForeignKey(entity = ContrastPackEntity::class, parentColumns = ["id"], childColumns = ["pack_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("group_id")],
)
data class ContrastRetiredGroupEntity(
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "retired_in_version") val retiredInVersion: Int,
    @ColumnInfo(name = "reason_vi") val reasonVi: String,
    @ColumnInfo(name = "replaced_by") val replacedBy: String?,
)

// ---- Learner data (no foreign keys) ---------------------------------------------------------------

/** Progress and review schedule of a group (one row, like learning_progress). */
@Entity(tableName = "contrast_progress", indices = [Index("due_at")])
data class ContrastProgressEntity(
    @PrimaryKey @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "lapse_count") val lapseCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One scheduling event. UNIQUE(session_id, group_id): a session has one block per group, so a
 * group can be scheduled at most once per session (no double review count).
 */
@Entity(
    tableName = "contrast_reviews",
    indices = [Index(value = ["session_id", "group_id"], unique = true), Index(value = ["group_id", "reviewed_at"])],
)
data class ContrastReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    val answered: Int,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    val rating: ReviewRating,
    @ColumnInfo(name = "step_before") val stepBefore: Int?,
    @ColumnInfo(name = "step_after") val stepAfter: Int,
    @ColumnInfo(name = "due_at_after") val dueAtAfter: Long,
    @ColumnInfo(name = "content_version") val contentVersion: Int,
)

@Entity(tableName = "contrast_sessions", indices = [Index("status")])
data class ContrastSessionEntity(
    @PrimaryKey val id: String,
    /** DUE, REVIEW (M7), GROUP or TYPE; [scopeValue] is the group id or the content type. */
    @ColumnInfo(name = "scope_kind") val scopeKind: String,
    @ColumnInfo(name = "scope_value") val scopeValue: String?,
    val status: SessionStatus,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @ColumnInfo(name = "content_version") val contentVersion: Int,
)

@Entity(tableName = "contrast_session_items", primaryKeys = ["session_id", "position"])
data class ContrastSessionItemEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val position: Int,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "question_revision") val questionRevision: Int,
    /** Null for a re-ask at the end of the session (never part of a block). */
    @ColumnInfo(name = "block_index") val blockIndex: Int?,
)

/**
 * One answer, immutable. PRIMARY KEY request_id (idempotency) and UNIQUE(session_id, position)
 * (one answer per position). Text snapshots keep history readable after content changes.
 */
@Entity(
    tableName = "contrast_attempts",
    indices = [
        Index(value = ["session_id", "position"], unique = true),
        Index(value = ["group_id", "answered_at"]),
        Index("question_id"),
        Index("answered_at"),
    ],
)
data class ContrastAttemptEntity(
    @PrimaryKey @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val position: Int,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "group_revision") val groupRevision: Int,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "question_revision") val questionRevision: Int,
    @ColumnInfo(name = "question_type") val questionType: ContrastQuestionType,
    @ColumnInfo(name = "content_pack_id") val contentPackId: String,
    @ColumnInfo(name = "content_version") val contentVersion: Int,
    val outcome: AnswerOutcome,
    @ColumnInfo(name = "chosen_option_id") val chosenOptionId: String?,
    @ColumnInfo(name = "correct_option_id") val correctOptionId: String,
    @ColumnInfo(name = "expected_item_id") val expectedItemId: String?,
    @ColumnInfo(name = "chosen_item_id") val chosenItemId: String?,
    @ColumnInfo(name = "chosen_text") val chosenText: String?,
    @ColumnInfo(name = "correct_text") val correctText: String,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
)
