package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.EditableItem
import com.n3sensei.app.domain.edit.ExampleDraft
import com.n3sensei.app.domain.edit.ExampleSaveResult
import com.n3sensei.app.domain.edit.KanjiExample
import com.n3sensei.app.domain.edit.KanjiChoice
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.translate.BookmarkEntry
import com.n3sensei.app.domain.translate.BookmarkResult
import com.n3sensei.app.domain.translate.Language
import kotlinx.coroutines.flow.Flow

/**
 * The learner's changes to content: edits of pack items (kept apart from the pack and applied
 * again after every pack update), the learner's own words (the "Từ của tôi" pack) and the
 * learner's links between words and kanji.
 */
interface UserContentRepository {
    /** Kanji, word or grammar point to edit; null if it does not exist. */
    suspend fun load(type: ItemType, id: String): EditableItem?

    /**
     * Saves the form of an existing item. Values equal to the pack's value remove that edit.
     * For a word, [linkedKanji] (kanji ids) replaces its links when not null.
     */
    suspend fun save(
        type: ItemType,
        id: String,
        values: Map<EditableField, String>,
        linkedKanji: Set<String>? = null,
        allowDuplicate: Boolean = false,
    ): SaveResult

    /** Back to the pack's values for every field of a pack item. */
    suspend fun revert(type: ItemType, id: String): Boolean

    /** Adds a word to "Từ của tôi". A word with the same spelling and reading is refused unless [allowDuplicate]. */
    suspend fun addWord(values: Map<EditableField, String>, linkedKanji: Set<String> = emptySet(), allowDuplicate: Boolean = false): SaveResult

    /** Deletes one of the learner's own words (with its schedule, mark and links). Pack words cannot be deleted. */
    suspend fun deleteWord(id: String): Boolean

    /** Kanji written with any of the characters of [text], in every installed pack. */
    suspend fun kanjiChoices(text: String): List<KanjiChoice>

    /** The kanji's examples: the pack's (read only) first, then the learner's own. */
    suspend fun kanjiExamples(kanjiId: String): List<KanjiExample>

    /**
     * Adds an example to a kanji ([exampleId] null) or changes one of the learner's examples.
     * Pack examples cannot be changed. Validated with [com.n3sensei.app.domain.edit.KanjiExampleValidator].
     */
    suspend fun saveKanjiExample(kanjiId: String, exampleId: String?, draft: ExampleDraft): ExampleSaveResult

    /** Deletes one of the learner's kanji examples; false for a pack example or an unknown id. */
    suspend fun deleteKanjiExample(exampleId: String): Boolean

    /** Edits and links whose item is no longer in any installed pack (kept; they apply again if it returns). */
    suspend fun countOrphans(): Int

    /**
     * Saves a "Dịch nhanh" bookmark into "Từ của tôi" as a word **saved, not studied**: no
     * progress, mark, review or reminder is created. Saving the same entry again (same
     * [BookmarkEntry.key]) adds only what it lacks and never makes a second word.
     */
    suspend fun saveBookmark(entry: BookmarkEntry): BookmarkResult

    /** The id of the saved word for this bookmark identity, live; null while not saved. */
    fun observeBookmark(sourceLanguage: Language, key: String): Flow<String?>
}
