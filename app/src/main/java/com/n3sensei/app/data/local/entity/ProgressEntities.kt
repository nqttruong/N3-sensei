package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating

// Learner data. Deliberately has no foreign key to content: content packs are replaced on
// update, and progress must survive that. Items are referenced by (item_type, item_id);
// queries join to content and skip progress whose item no longer exists.
// Enums are stored by name (Room's built-in enum support).

@Entity(
    tableName = "learning_progress",
    primaryKeys = ["item_type", "item_id"],
    indices = [Index("due_at")],
)
data class LearningProgressEntity(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    val state: ProgressState,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Long,
    /** Epoch millis; null while the item has no scheduled review. */
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "lapse_count") val lapseCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "review_records",
    indices = [
        Index(value = ["item_type", "item_id"]),
        Index("reviewed_at"),
        // One row per answer: a repeated submit with the same request id is rejected.
        Index(value = ["request_id"], unique = true),
    ],
)
data class ReviewRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    val rating: ReviewRating,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "interval_before_minutes") val intervalBeforeMinutes: Long,
    @ColumnInfo(name = "interval_after_minutes") val intervalAfterMinutes: Long,
    @ColumnInfo(name = "due_at_after") val dueAtAfter: Long,
    /** Null for records written before v3 (SQLite allows many NULLs in a unique index). */
    @ColumnInfo(name = "request_id") val requestId: String? = null,
    @ColumnInfo(name = "was_new", defaultValue = "0") val wasNew: Boolean = false,
)

/** Learner's "needs attention" mark on an item. Presence of the row means marked. */
@Entity(
    tableName = "item_marks",
    primaryKeys = ["item_type", "item_id"],
)
data class ItemMarkEntity(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "marked_at") val markedAt: Long,
)

/** One scheduled review session: how many cards were planned and how many were answered. */
@Entity(tableName = "review_sessions", indices = [Index("started_at")])
data class ReviewSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    val planned: Int,
    val answered: Int,
    val finished: Boolean,
)
