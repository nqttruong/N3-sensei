package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.n3sensei.app.domain.model.ItemType

/**
 * One field of a pack item changed by the learner (learner data, never written by packs).
 *
 * The content row holds the merged value; this row is the source of truth for the change and
 * is applied again whenever the item's pack is reinstalled. A row exists only for a field the
 * learner changed: no row = not edited; `value = ""` = deliberately cleared.
 * Rows are kept when the item disappears from its pack (orphans), and apply again if it returns.
 *
 * @property field a [com.n3sensei.app.domain.edit.EditableField] key.
 * @property value the learner's value, encoded like the content column ([com.n3sensei.app.data.local.ContentEdits]).
 * @property baseValue the pack's value the last time the edit was applied, for revert and review.
 */
@Entity(tableName = "user_content_edits", primaryKeys = ["item_type", "item_id", "field"])
data class UserContentEditEntity(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    val field: String,
    val value: String,
    @ColumnInfo(name = "base_value") val baseValue: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
