package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExampleCrossRef
import com.n3sensei.app.data.local.entity.GrammarExerciseEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.KanjiExampleCrossRef
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyTagCrossRef
import com.n3sensei.app.data.local.ContentEdits
import com.n3sensei.app.data.local.model.ContentPackBundle
import com.n3sensei.app.data.local.model.ContentPackRow
import com.n3sensei.app.data.local.withSearchText
import kotlinx.coroutines.flow.Flow

/** Writes content packs. Inserts abort on conflict so malformed packs fail loudly. */
@Dao
abstract class ContentPackDao {

    @Query("SELECT * FROM content_packs ORDER BY id")
    abstract fun observePacks(): Flow<List<ContentPackEntity>>

    @Query(
        """
        SELECT p.*, (SELECT COUNT(*) FROM lessons l WHERE l.pack_id = p.id) AS lesson_count
        FROM content_packs p ORDER BY p.title
        """,
    )
    abstract fun observePackRows(): Flow<List<ContentPackRow>>

    @Query("SELECT * FROM content_packs WHERE id = :id")
    abstract suspend fun getPack(id: String): ContentPackEntity?

    /** `TYPE:id` of every item in a pack. */
    @Query(
        """
        SELECT 'VOCABULARY:' || v.id FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id WHERE l.pack_id = :packId
        UNION ALL SELECT 'KANJI:' || k.id FROM kanji k JOIN lessons l ON l.id = k.lesson_id WHERE l.pack_id = :packId
        UNION ALL SELECT 'GRAMMAR:' || g.id FROM grammar g JOIN lessons l ON l.id = g.lesson_id WHERE l.pack_id = :packId
        """,
    )
    abstract suspend fun getItemKeys(packId: String): List<String>

    /** How many of [keys] (`TYPE:id`) have learner progress. */
    @Query("SELECT COUNT(*) FROM learning_progress WHERE item_type || ':' || item_id IN (:keys)")
    abstract suspend fun countWithProgress(keys: List<String>): Int

    /** Deletes the pack and, via cascades, its lessons, items, examples and links. */
    @Query("DELETE FROM content_packs WHERE id = :id")
    abstract suspend fun deletePack(id: String): Int

    /**
     * Atomically replaces a pack with [bundle]. If any row is invalid, nothing changes.
     * Learner progress is untouched because it is keyed by stable item IDs, not by FK.
     * The learner's edits of this pack's items are applied again on the new rows.
     */
    @Transaction
    open suspend fun replacePack(bundle: ContentPackBundle) {
        deletePack(bundle.pack.id)
        upsertSources(bundle.sources)
        insertPack(bundle.pack)
        insertLessons(bundle.lessons)
        insertVocabulary(bundle.vocabulary.map { it.withSearchText() })
        insertKanji(bundle.kanji.map { it.withSearchText() })
        insertGrammar(bundle.grammar.map { it.withSearchText() })
        insertGrammarExercises(bundle.grammarExercises)
        insertExamples(bundle.examples)
        insertVocabularyExamples(bundle.vocabularyExamples)
        insertKanjiExamples(bundle.kanjiExamples)
        insertGrammarExamples(bundle.grammarExamples)
        upsertTags(bundle.tags)
        insertVocabularyTags(bundle.vocabularyTags)
        reapplyUserEdits(bundle.pack.id)
    }

    /**
     * The new rows carry the pack's values: record them as each edit's base and write the
     * learner's values over them. Edits of items the pack no longer has are left as they are.
     */
    private suspend fun reapplyUserEdits(packId: String) {
        getKanjiEditsOfPack(packId).groupBy { it.itemId }.forEach { (id, edits) ->
            val row = getKanji(id) ?: return@forEach
            val rebased = edits.map { it.copy(baseValue = ContentEdits.get(row, it.field)) }
            upsertEdits(rebased.filterIndexed { i, e -> e != edits[i] })
            updateKanji(ContentEdits.merged(row, rebased).withSearchText())
        }
        getVocabularyEditsOfPack(packId).groupBy { it.itemId }.forEach { (id, edits) ->
            val row = getVocabulary(id) ?: return@forEach
            val rebased = edits.map { it.copy(baseValue = ContentEdits.get(row, it.field)) }
            upsertEdits(rebased.filterIndexed { i, e -> e != edits[i] })
            updateVocabulary(ContentEdits.merged(row, rebased).withSearchText())
        }
    }

    @Query(
        """
        SELECT e.* FROM user_content_edits e JOIN kanji k ON k.id = e.item_id JOIN lessons l ON l.id = k.lesson_id
        WHERE e.item_type = 'KANJI' AND l.pack_id = :packId
        """,
    )
    abstract suspend fun getKanjiEditsOfPack(packId: String): List<UserContentEditEntity>

    @Query(
        """
        SELECT e.* FROM user_content_edits e JOIN vocabulary v ON v.id = e.item_id JOIN lessons l ON l.id = v.lesson_id
        WHERE e.item_type = 'VOCABULARY' AND l.pack_id = :packId
        """,
    )
    abstract suspend fun getVocabularyEditsOfPack(packId: String): List<UserContentEditEntity>

    @Query("SELECT * FROM kanji WHERE id = :id")
    abstract suspend fun getKanji(id: String): KanjiEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    abstract suspend fun getVocabulary(id: String): VocabularyEntity?

    @Update
    abstract suspend fun updateKanji(row: KanjiEntity)

    @Update
    abstract suspend fun updateVocabulary(row: VocabularyEntity)

    @Upsert
    abstract suspend fun upsertEdits(edits: List<UserContentEditEntity>)

    // Upsert (not REPLACE) so a referenced source is updated in place instead of being
    // deleted, which the RESTRICT foreign keys would reject.
    @Upsert
    abstract suspend fun upsertSources(sources: List<ContentSourceEntity>)

    @Upsert
    abstract suspend fun upsertTags(tags: List<TagEntity>)

    @Insert
    abstract suspend fun insertPack(pack: ContentPackEntity)

    @Insert
    abstract suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert
    abstract suspend fun insertVocabulary(items: List<VocabularyEntity>)

    @Insert
    abstract suspend fun insertKanji(items: List<KanjiEntity>)

    @Insert
    abstract suspend fun insertGrammar(items: List<GrammarEntity>)

    @Insert
    abstract suspend fun insertGrammarExercises(items: List<GrammarExerciseEntity>)

    @Insert
    abstract suspend fun insertExamples(items: List<ExampleSentenceEntity>)

    @Insert
    abstract suspend fun insertVocabularyExamples(items: List<VocabularyExampleCrossRef>)

    @Insert
    abstract suspend fun insertKanjiExamples(items: List<KanjiExampleCrossRef>)

    @Insert
    abstract suspend fun insertGrammarExamples(items: List<GrammarExampleCrossRef>)

    @Insert
    abstract suspend fun insertVocabularyTags(items: List<VocabularyTagCrossRef>)
}
