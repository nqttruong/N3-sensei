package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.EditableItem
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.model.ItemType

/**
 * The learner's changes to content: edits of pack items (kept apart from the pack and applied
 * again after every pack update) and the learner's own words (the "Từ của tôi" pack).
 */
interface UserContentRepository {
    /** Kanji or word to edit; null if it does not exist. */
    suspend fun load(type: ItemType, id: String): EditableItem?

    /** Saves the form of an existing item. Values equal to the pack's value remove that edit. */
    suspend fun save(type: ItemType, id: String, values: Map<EditableField, String>): SaveResult

    /** Back to the pack's values for every field of a pack item. */
    suspend fun revert(type: ItemType, id: String): Boolean

    /** Adds a word to "Từ của tôi". */
    suspend fun addWord(values: Map<EditableField, String>): SaveResult

    /** Deletes one of the learner's own words (with its schedule and mark). Pack words cannot be deleted. */
    suspend fun deleteWord(id: String): Boolean

    /** Edits whose item is no longer in any installed pack (kept; they apply again if it returns). */
    suspend fun countOrphanEdits(): Int
}
