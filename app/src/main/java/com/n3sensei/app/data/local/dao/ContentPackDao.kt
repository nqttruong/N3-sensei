package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExampleCrossRef
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.KanjiExampleCrossRef
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyTagCrossRef
import com.n3sensei.app.data.local.model.ContentPackBundle
import kotlinx.coroutines.flow.Flow

/** Writes content packs. Inserts abort on conflict so malformed packs fail loudly. */
@Dao
abstract class ContentPackDao {

    @Query("SELECT * FROM content_packs ORDER BY id")
    abstract fun observePacks(): Flow<List<ContentPackEntity>>

    @Query("SELECT * FROM content_packs WHERE id = :id")
    abstract suspend fun getPack(id: String): ContentPackEntity?

    /** Deletes the pack and, via cascades, its lessons, items, examples and links. */
    @Query("DELETE FROM content_packs WHERE id = :id")
    abstract suspend fun deletePack(id: String): Int

    /**
     * Atomically replaces a pack with [bundle]. If any row is invalid, nothing changes.
     * Learner progress is untouched because it is keyed by stable item IDs, not by FK.
     */
    @Transaction
    open suspend fun replacePack(bundle: ContentPackBundle) {
        deletePack(bundle.pack.id)
        upsertSources(bundle.sources)
        insertPack(bundle.pack)
        insertLessons(bundle.lessons)
        insertVocabulary(bundle.vocabulary)
        insertKanji(bundle.kanji)
        insertGrammar(bundle.grammar)
        insertExamples(bundle.examples)
        insertVocabularyExamples(bundle.vocabularyExamples)
        insertKanjiExamples(bundle.kanjiExamples)
        insertGrammarExamples(bundle.grammarExamples)
        upsertTags(bundle.tags)
        insertVocabularyTags(bundle.vocabularyTags)
    }

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
