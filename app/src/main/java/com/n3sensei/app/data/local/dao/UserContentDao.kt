package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.n3sensei.app.data.local.ContentEdits
import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.UserKanjiExampleEntity
import com.n3sensei.app.data.local.entity.UserWordEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyKanjiLinkEntity
import com.n3sensei.app.data.local.withSearchText
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.ItemType

/** A word's owner, for "already in pack X". */
data class WordOwner(val id: String, val packTitle: String)

/** A kanji with the title of its pack. */
data class KanjiChoiceRow(val id: String, val character: String, val hanViet: String?, val meaningVi: String, val packTitle: String)

/**
 * The learner's edits of pack items, own words and word ↔ kanji links. Content rows hold the
 * merged values (so every list, search, review and reminder reads them unchanged); the edit
 * rows are the source of truth and are applied again by [ContentPackDao.replacePack].
 */
@Dao
abstract class UserContentDao {

    @Query("SELECT * FROM user_content_edits WHERE item_type = :type AND item_id = :id")
    abstract suspend fun getEdits(type: ItemType, id: String): List<UserContentEditEntity>

    @Upsert
    abstract suspend fun upsertEdits(edits: List<UserContentEditEntity>)

    @Query("DELETE FROM user_content_edits WHERE item_type = :type AND item_id = :id AND field IN (:fields)")
    abstract suspend fun deleteEdits(type: ItemType, id: String, fields: List<String>)

    @Query("DELETE FROM user_content_edits WHERE item_type = :type AND item_id = :id")
    abstract suspend fun deleteAllEdits(type: ItemType, id: String)

    /** Edited items and linked words/kanji that are in no installed pack. */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM (SELECT DISTINCT item_type, item_id FROM user_content_edits e
            WHERE (e.item_type = 'KANJI' AND NOT EXISTS (SELECT 1 FROM kanji k WHERE k.id = e.item_id))
               OR (e.item_type = 'VOCABULARY' AND NOT EXISTS (SELECT 1 FROM vocabulary v WHERE v.id = e.item_id))
               OR (e.item_type = 'GRAMMAR' AND NOT EXISTS (SELECT 1 FROM grammar g WHERE g.id = e.item_id))))
          + (SELECT COUNT(*) FROM vocabulary_kanji_links x
            WHERE NOT EXISTS (SELECT 1 FROM vocabulary v WHERE v.id = x.vocabulary_id)
               OR NOT EXISTS (SELECT 1 FROM kanji k WHERE k.id = x.kanji_id))
        """,
    )
    abstract suspend fun countOrphans(): Int

    @Query("SELECT * FROM kanji WHERE id = :id")
    abstract suspend fun getKanji(id: String): KanjiEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    abstract suspend fun getVocabulary(id: String): VocabularyEntity?

    @Query("SELECT * FROM grammar WHERE id = :id")
    abstract suspend fun getGrammar(id: String): GrammarEntity?

    @Update
    abstract suspend fun updateKanji(row: KanjiEntity)

    @Update
    abstract suspend fun updateVocabulary(row: VocabularyEntity)

    @Update
    abstract suspend fun updateGrammar(row: GrammarEntity)

    @Query("SELECT l.pack_id FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id WHERE v.id = :id")
    abstract suspend fun packOfVocabulary(id: String): String?

    @Query("SELECT COUNT(*) FROM learning_progress WHERE item_type = :type AND item_id = :id")
    abstract suspend fun countProgress(type: ItemType, id: String): Int

    /** Another word with this spelling and reading (excluding [exceptId]), in any pack. */
    @Query(
        """
        SELECT v.id AS id, p.title AS packTitle FROM vocabulary v
        JOIN lessons l ON l.id = v.lesson_id JOIN content_packs p ON p.id = l.pack_id
        WHERE v.expression = :expression AND v.reading = :reading AND v.id != :exceptId
        ORDER BY p.id LIMIT 1
        """,
    )
    abstract suspend fun findWord(expression: String, reading: String, exceptId: String = ""): WordOwner?

    // ---- edits of pack items --------------------------------------------------------------

    /**
     * Sets [values] (field key → stored value) on a pack kanji. A value equal to the pack's
     * removes the edit; fields not in [values] keep their edits.
     */
    @Transaction
    open suspend fun editKanji(id: String, values: Map<String, String>, now: Long): Boolean {
        val row = getKanji(id) ?: return false
        val old = getEdits(ItemType.KANJI, id)
        val base = ContentEdits.base(row, old)
        val edits = merge(ItemType.KANJI, id, old, values, now) { ContentEdits.get(base, it) }
        updateKanji(ContentEdits.merged(base, edits).withSearchText())
        return true
    }

    @Transaction
    open suspend fun editVocabulary(id: String, values: Map<String, String>, now: Long): Boolean {
        val row = getVocabulary(id) ?: return false
        val old = getEdits(ItemType.VOCABULARY, id)
        val base = ContentEdits.base(row, old)
        val edits = merge(ItemType.VOCABULARY, id, old, values, now) { ContentEdits.get(base, it) }
        updateVocabulary(ContentEdits.merged(base, edits).withSearchText())
        return true
    }

    @Transaction
    open suspend fun editGrammar(id: String, values: Map<String, String>, now: Long): Boolean {
        val row = getGrammar(id) ?: return false
        val old = getEdits(ItemType.GRAMMAR, id)
        val base = ContentEdits.base(row, old)
        val edits = merge(ItemType.GRAMMAR, id, old, values, now) { ContentEdits.get(base, it) }
        updateGrammar(ContentEdits.merged(base, edits).withSearchText())
        return true
    }

    /**
     * Writes the edit rows for [values] and returns every edit of the item afterwards. Saving a
     * field again clears its conflict flag: the learner has seen the pack's new value.
     */
    private suspend fun merge(
        type: ItemType,
        id: String,
        old: List<UserContentEditEntity>,
        values: Map<String, String>,
        now: Long,
        baseOf: (String) -> String,
    ): List<UserContentEditEntity> {
        val byField = old.associateBy { it.field }.toMutableMap()
        val dropped = mutableListOf<String>()
        val written = mutableListOf<UserContentEditEntity>()
        values.forEach { (field, value) ->
            val base = baseOf(field)
            val previous = byField[field]
            when {
                value == base -> if (previous != null) {
                    dropped += field
                    byField.remove(field)
                }
                previous != null && previous.value == value && !previous.baseChanged -> Unit
                else -> {
                    val edit = UserContentEditEntity(type, id, field, value, base, previous?.createdAt ?: now, now, baseChanged = false)
                    written += edit
                    byField[field] = edit
                }
            }
        }
        if (dropped.isNotEmpty()) deleteEdits(type, id, dropped)
        if (written.isNotEmpty()) upsertEdits(written)
        return byField.values.toList()
    }

    @Transaction
    open suspend fun revertKanji(id: String): Boolean {
        val row = getKanji(id) ?: return false
        updateKanji(ContentEdits.base(row, getEdits(ItemType.KANJI, id)).withSearchText())
        deleteAllEdits(ItemType.KANJI, id)
        return true
    }

    @Transaction
    open suspend fun revertVocabulary(id: String): Boolean {
        val row = getVocabulary(id) ?: return false
        updateVocabulary(ContentEdits.base(row, getEdits(ItemType.VOCABULARY, id)).withSearchText())
        deleteAllEdits(ItemType.VOCABULARY, id)
        return true
    }

    @Transaction
    open suspend fun revertGrammar(id: String): Boolean {
        val row = getGrammar(id) ?: return false
        updateGrammar(ContentEdits.base(row, getEdits(ItemType.GRAMMAR, id)).withSearchText())
        deleteAllEdits(ItemType.GRAMMAR, id)
        return true
    }

    // ---- word ↔ kanji links ---------------------------------------------------------------

    @Query("SELECT * FROM vocabulary_kanji_links WHERE vocabulary_id = :vocabularyId ORDER BY created_at, kanji_id")
    abstract suspend fun getLinks(vocabularyId: String): List<VocabularyKanjiLinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLinks(links: List<VocabularyKanjiLinkEntity>)

    @Query("DELETE FROM vocabulary_kanji_links WHERE vocabulary_id = :vocabularyId AND kanji_id IN (:kanjiIds)")
    abstract suspend fun deleteLinks(vocabularyId: String, kanjiIds: List<String>)

    @Query("DELETE FROM vocabulary_kanji_links WHERE vocabulary_id = :vocabularyId")
    abstract suspend fun deleteAllLinks(vocabularyId: String)

    @Query(
        """
        SELECT k.id AS id, k.character AS character, k.han_viet AS hanViet, k.meaning_vi AS meaningVi, p.title AS packTitle
        FROM kanji k JOIN lessons l ON l.id = k.lesson_id JOIN content_packs p ON p.id = l.pack_id
        WHERE k.character IN (:characters) ORDER BY p.id, l.number, k.sort_order
        """,
    )
    abstract suspend fun kanjiByCharacters(characters: List<String>): List<KanjiChoiceRow>

    @Query(
        """
        SELECT k.id AS id, k.character AS character, k.han_viet AS hanViet, k.meaning_vi AS meaningVi, p.title AS packTitle
        FROM vocabulary_kanji_links x JOIN kanji k ON k.id = x.kanji_id
        JOIN lessons l ON l.id = k.lesson_id JOIN content_packs p ON p.id = l.pack_id
        WHERE x.vocabulary_id = :vocabularyId ORDER BY x.created_at, x.kanji_id
        """,
    )
    abstract suspend fun linkedKanji(vocabularyId: String): List<KanjiChoiceRow>

    /** Makes [kanjiIds] the word's links: new ones are added now, others removed, kept ones untouched. */
    @Transaction
    open suspend fun setLinks(vocabularyId: String, kanjiIds: Set<String>, now: Long) {
        val current = getLinks(vocabularyId).map { it.kanjiId }.toSet()
        val removed = current - kanjiIds
        if (removed.isNotEmpty()) deleteLinks(vocabularyId, removed.toList())
        insertLinks((kanjiIds - current).sorted().map { VocabularyKanjiLinkEntity(vocabularyId, it, now) })
    }

    // ---- the learner's own words ----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSourceIfMissing(source: ContentSourceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPackIfMissing(pack: ContentPackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLessonIfMissing(lesson: LessonEntity)

    @Insert
    abstract suspend fun insertVocabulary(row: VocabularyEntity)

    @Upsert
    abstract suspend fun upsertUserWord(row: UserWordEntity)

    @Query("SELECT * FROM user_words WHERE vocabulary_id = :id")
    abstract suspend fun getUserWord(id: String): UserWordEntity?

    @Query("DELETE FROM user_words WHERE vocabulary_id = :id")
    abstract suspend fun deleteUserWordMeta(id: String)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM vocabulary WHERE lesson_id = :lessonId")
    abstract suspend fun maxSortOrder(lessonId: String): Int

    @Query("DELETE FROM vocabulary WHERE id = :id")
    abstract suspend fun deleteVocabulary(id: String): Int

    @Query("DELETE FROM learning_progress WHERE item_type = :type AND item_id = :id")
    abstract suspend fun deleteProgress(type: ItemType, id: String)

    @Query("DELETE FROM item_marks WHERE item_type = :type AND item_id = :id")
    abstract suspend fun deleteMark(type: ItemType, id: String)

    @Query(
        """
        SELECT e.* FROM example_sentences e JOIN vocabulary_examples x ON x.example_id = e.id
        WHERE x.vocabulary_id = :vocabularyId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun examplesOf(vocabularyId: String): List<ExampleSentenceEntity>

    @Upsert
    abstract suspend fun upsertExample(row: ExampleSentenceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertVocabularyExample(row: VocabularyExampleCrossRef)

    @Query("DELETE FROM example_sentences WHERE id = :id")
    abstract suspend fun deleteExample(id: String)

    @Upsert
    abstract suspend fun upsertKanjiExampleLink(row: UserKanjiExampleEntity)

    @Query("SELECT * FROM user_kanji_examples WHERE example_id = :id")
    abstract suspend fun getKanjiExampleLink(id: String): UserKanjiExampleEntity?

    @Query("DELETE FROM user_kanji_examples WHERE example_id = :id")
    abstract suspend fun deleteKanjiExampleLink(id: String)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM user_kanji_examples WHERE kanji_id = :kanjiId")
    abstract suspend fun maxKanjiExampleOrder(kanjiId: String): Int

    @Query("SELECT * FROM example_sentences WHERE id = :id")
    abstract suspend fun getExample(id: String): ExampleSentenceEntity?

    /** The kanji's examples from its pack (read only in the edit form). */
    @Query(
        """
        SELECT e.* FROM example_sentences e JOIN kanji_examples x ON x.example_id = e.id
        WHERE x.kanji_id = :kanjiId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun packKanjiExamples(kanjiId: String): List<ExampleSentenceEntity>

    /** The learner's examples of a kanji, in the order they were added. */
    @Query(
        """
        SELECT e.* FROM example_sentences e JOIN user_kanji_examples x ON x.example_id = e.id
        WHERE x.kanji_id = :kanjiId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun ownKanjiExamples(kanjiId: String): List<ExampleSentenceEntity>

    /** Creates the "Từ của tôi" pack, its lesson and source if they are not there yet. */
    @Transaction
    open suspend fun ensureUserPack(now: Long) {
        insertSourceIfMissing(
            ContentSourceEntity(
                id = UserWords.SOURCE_ID,
                name = "Bạn tự thêm",
                url = null,
                license = "Nội dung do người dùng tự nhập trong ứng dụng.",
                attribution = null,
                licenseVerified = true,
            ),
        )
        insertPackIfMissing(ContentPackEntity(UserWords.PACK_ID, "Từ của tôi", 1, UserWords.SOURCE_ID, isSeed = false, importedAt = now))
        insertLessonIfMissing(LessonEntity(UserWords.LESSON_ID, UserWords.PACK_ID, 1, "Từ của tôi", "N3", "Các từ bạn tự thêm."))
    }

    /** Adds [row] (any sort order) at the end of "Từ của tôi", creating the pack on first use. */
    @Transaction
    open suspend fun addUserWord(row: VocabularyEntity, example: Pair<String, String>?, kanjiIds: Set<String>, now: Long) {
        ensureUserPack(now)
        insertVocabulary(row.copy(lessonId = UserWords.LESSON_ID, sourceId = UserWords.SOURCE_ID, sortOrder = maxSortOrder(UserWords.LESSON_ID) + 1).withSearchText())
        upsertUserWord(UserWordEntity(row.id, now, now))
        setExample(row.id, example)
        setLinks(row.id, kanjiIds, now)
    }

    /**
     * Saves one of the learner's words: the row, its example (null = none) and the date of the
     * change. Only [addUserWord] creates the `user_words` row that makes a word the learner's
     * own; editing never does, so a word that was not added by the learner cannot become one.
     */
    @Transaction
    open suspend fun updateUserWord(row: VocabularyEntity, example: Pair<String, String>?, now: Long) {
        updateVocabulary(row.withSearchText())
        setExample(row.id, example)
        getUserWord(row.id)?.let { upsertUserWord(it.copy(updatedAt = now)) }
    }

    /** The learner's words have at most one example, with the id `user-x-<word id>`. */
    private suspend fun setExample(vocabularyId: String, example: Pair<String, String>?) {
        val id = UserWords.EXAMPLE_ID_PREFIX + vocabularyId.removePrefix(UserWords.ID_PREFIX)
        if (example == null) {
            deleteExample(id)
        } else {
            upsertExample(ExampleSentenceEntity(id, UserWords.PACK_ID, UserWords.SOURCE_ID, example.first, null, example.second))
            insertVocabularyExample(VocabularyExampleCrossRef(vocabularyId, id, 0))
        }
    }

    /** Adds a learner's example ([example] in the learner's pack) at the end of the kanji's own examples. */
    @Transaction
    open suspend fun addKanjiExample(kanjiId: String, example: ExampleSentenceEntity, now: Long) {
        ensureUserPack(now)
        upsertExample(example)
        upsertKanjiExampleLink(UserKanjiExampleEntity(example.id, kanjiId, maxKanjiExampleOrder(kanjiId) + 1, now, now))
    }

    /** Changes one of the learner's kanji examples; false if [example] is not one (pack examples are read only). */
    @Transaction
    open suspend fun updateKanjiExample(example: ExampleSentenceEntity, now: Long): Boolean {
        val link = getKanjiExampleLink(example.id) ?: return false
        upsertExample(example)
        upsertKanjiExampleLink(link.copy(updatedAt = now))
        return true
    }

    /** Deletes one of the learner's kanji examples; false (and nothing deleted) for any other example. */
    @Transaction
    open suspend fun deleteKanjiExample(id: String): Boolean {
        getKanjiExampleLink(id) ?: return false
        deleteKanjiExampleLink(id)
        deleteExample(id)
        return true
    }

    /** Deletes one of the learner's words with its example, schedule, mark and links (review history stays). */
    @Transaction
    open suspend fun deleteUserWord(id: String): Boolean {
        if (packOfVocabulary(id) != UserWords.PACK_ID) return false
        setExample(id, null)
        deleteVocabulary(id)
        deleteUserWordMeta(id)
        deleteAllLinks(id)
        deleteProgress(ItemType.VOCABULARY, id)
        deleteMark(ItemType.VOCABULARY, id)
        return true
    }
}
