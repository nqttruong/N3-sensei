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
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.withSearchText
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.ItemType

/** A word's owner, for "already in pack X". */
data class WordOwner(val id: String, val packTitle: String)

/**
 * The learner's edits of pack items and the learner's own words. Content rows hold the merged
 * values (so every list, search, review and reminder reads them unchanged); the edit rows are
 * the source of truth and are applied again by [ContentPackDao.replacePack].
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

    @Query(
        """
        SELECT COUNT(*) FROM (SELECT DISTINCT item_type, item_id FROM user_content_edits e
        WHERE (e.item_type = 'KANJI' AND NOT EXISTS (SELECT 1 FROM kanji k WHERE k.id = e.item_id))
           OR (e.item_type = 'VOCABULARY' AND NOT EXISTS (SELECT 1 FROM vocabulary v WHERE v.id = e.item_id)))
        """,
    )
    abstract suspend fun countOrphanEdits(): Int

    @Query("SELECT * FROM kanji WHERE id = :id")
    abstract suspend fun getKanji(id: String): KanjiEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    abstract suspend fun getVocabulary(id: String): VocabularyEntity?

    @Update
    abstract suspend fun updateKanji(row: KanjiEntity)

    @Update
    abstract suspend fun updateVocabulary(row: VocabularyEntity)

    @Query("SELECT l.pack_id FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id WHERE v.id = :id")
    abstract suspend fun packOfVocabulary(id: String): String?

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

    /** Writes the edit rows for [values] and returns every edit of the item afterwards. */
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
                previous?.value == value -> Unit
                else -> {
                    val edit = UserContentEditEntity(type, id, field, value, base, previous?.createdAt ?: now, now)
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

    // ---- the learner's own words ----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSourceIfMissing(source: ContentSourceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPackIfMissing(pack: ContentPackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLessonIfMissing(lesson: LessonEntity)

    @Insert
    abstract suspend fun insertVocabulary(row: VocabularyEntity)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM vocabulary WHERE lesson_id = :lessonId")
    abstract suspend fun maxSortOrder(lessonId: String): Int

    @Query("DELETE FROM vocabulary WHERE id = :id")
    abstract suspend fun deleteVocabulary(id: String): Int

    @Query("DELETE FROM learning_progress WHERE item_type = :type AND item_id = :id")
    abstract suspend fun deleteProgress(type: ItemType, id: String)

    @Query("DELETE FROM item_marks WHERE item_type = :type AND item_id = :id")
    abstract suspend fun deleteMark(type: ItemType, id: String)

    /** Adds [row] (any sort order) at the end of "Từ của tôi", creating the pack on first use. */
    @Transaction
    open suspend fun addUserWord(row: VocabularyEntity, now: Long) {
        insertSourceIfMissing(
            ContentSourceEntity(
                id = UserWords.SOURCE_ID,
                name = "Bạn tự thêm",
                url = null,
                license = "Nội dung do người dùng tự nhập trong ứng dụng.",
                attribution = null,
                licenseVerified = false,
            ),
        )
        insertPackIfMissing(ContentPackEntity(UserWords.PACK_ID, "Từ của tôi", 1, UserWords.SOURCE_ID, isSeed = false, importedAt = now))
        insertLessonIfMissing(LessonEntity(UserWords.LESSON_ID, UserWords.PACK_ID, 1, "Từ của tôi", "N3", "Các từ bạn tự thêm."))
        insertVocabulary(row.copy(lessonId = UserWords.LESSON_ID, sourceId = UserWords.SOURCE_ID, sortOrder = maxSortOrder(UserWords.LESSON_ID) + 1).withSearchText())
    }

    /** Deletes one of the learner's words with its schedule and mark (review history stays). */
    @Transaction
    open suspend fun deleteUserWord(id: String): Boolean {
        if (packOfVocabulary(id) != UserWords.PACK_ID) return false
        deleteVocabulary(id)
        deleteProgress(ItemType.VOCABULARY, id)
        deleteMark(ItemType.VOCABULARY, id)
        return true
    }
}
