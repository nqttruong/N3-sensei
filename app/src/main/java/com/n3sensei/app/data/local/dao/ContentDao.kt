package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.model.GrammarDetailRow
import com.n3sensei.app.data.local.model.KanjiDetailRow
import com.n3sensei.app.data.local.model.LessonSummaryRow
import com.n3sensei.app.data.local.model.VocabularyDetailRow
import kotlinx.coroutines.flow.Flow

/** Read queries over shared content. Writes go through [ContentPackDao]. */
@Dao
abstract class ContentDao {

    @Query(
        """
        SELECT l.*,
            (SELECT COUNT(*) FROM vocabulary v WHERE v.lesson_id = l.id) AS vocabulary_count,
            (SELECT COUNT(*) FROM kanji k WHERE k.lesson_id = l.id) AS kanji_count,
            (SELECT COUNT(*) FROM grammar g WHERE g.lesson_id = l.id) AS grammar_count
        FROM lessons l
        ORDER BY l.pack_id, l.number
        """,
    )
    abstract fun observeLessonSummaries(): Flow<List<LessonSummaryRow>>

    @Query("SELECT * FROM vocabulary WHERE lesson_id = :lessonId ORDER BY sort_order")
    abstract fun observeVocabularyByLesson(lessonId: String): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM kanji WHERE lesson_id = :lessonId ORDER BY sort_order")
    abstract fun observeKanjiByLesson(lessonId: String): Flow<List<KanjiEntity>>

    @Query("SELECT * FROM grammar WHERE lesson_id = :lessonId ORDER BY sort_order")
    abstract fun observeGrammarByLesson(lessonId: String): Flow<List<GrammarEntity>>

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    abstract suspend fun getVocabulary(id: String): VocabularyEntity?

    @Query("SELECT * FROM kanji WHERE id = :id")
    abstract suspend fun getKanji(id: String): KanjiEntity?

    @Query("SELECT * FROM grammar WHERE id = :id")
    abstract suspend fun getGrammar(id: String): GrammarEntity?

    @Query("SELECT * FROM content_sources WHERE id = :id")
    abstract suspend fun getSource(id: String): ContentSourceEntity?

    @Query(
        """
        SELECT e.* FROM example_sentences e
        JOIN vocabulary_examples x ON x.example_id = e.id
        WHERE x.vocabulary_id = :vocabularyId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun getVocabularyExamples(vocabularyId: String): List<ExampleSentenceEntity>

    @Query(
        """
        SELECT e.* FROM example_sentences e
        JOIN kanji_examples x ON x.example_id = e.id
        WHERE x.kanji_id = :kanjiId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun getKanjiExamples(kanjiId: String): List<ExampleSentenceEntity>

    @Query(
        """
        SELECT e.* FROM example_sentences e
        JOIN grammar_examples x ON x.example_id = e.id
        WHERE x.grammar_id = :grammarId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun getGrammarExamples(grammarId: String): List<ExampleSentenceEntity>

    @Query(
        """
        SELECT t.* FROM tags t
        JOIN vocabulary_tags x ON x.tag_id = t.id
        WHERE x.vocabulary_id = :vocabularyId ORDER BY t.name
        """,
    )
    abstract suspend fun getVocabularyTags(vocabularyId: String): List<TagEntity>

    // Detail reads run in one transaction so the item, its examples and source are consistent
    // even if a content pack is replaced concurrently.

    @Transaction
    open suspend fun getVocabularyDetail(id: String): VocabularyDetailRow? {
        val vocabulary = getVocabulary(id) ?: return null
        val source = getSource(vocabulary.sourceId) ?: return null
        return VocabularyDetailRow(vocabulary, getVocabularyExamples(id), getVocabularyTags(id), source)
    }

    @Transaction
    open suspend fun getKanjiDetail(id: String): KanjiDetailRow? {
        val kanji = getKanji(id) ?: return null
        val source = getSource(kanji.sourceId) ?: return null
        return KanjiDetailRow(kanji, getKanjiExamples(id), source)
    }

    @Transaction
    open suspend fun getGrammarDetail(id: String): GrammarDetailRow? {
        val grammar = getGrammar(id) ?: return null
        val source = getSource(grammar.sourceId) ?: return null
        return GrammarDetailRow(grammar, getGrammarExamples(id), source)
    }

    // Search: `pattern` is built with SqlLike.contains; exact matches rank first.

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE expression LIKE :pattern ESCAPE '\' OR reading LIKE :pattern ESCAPE '\'
            OR meaning_vi LIKE :pattern ESCAPE '\'
        ORDER BY CASE WHEN expression = :query OR reading = :query THEN 0 ELSE 1 END,
            lesson_id, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun searchVocabulary(query: String, pattern: String, limit: Int, offset: Int): List<VocabularyEntity>

    @Query(
        """
        SELECT * FROM kanji
        WHERE character LIKE :pattern ESCAPE '\' OR meaning_vi LIKE :pattern ESCAPE '\'
            OR han_viet LIKE :pattern ESCAPE '\' OR on_readings LIKE :pattern ESCAPE '\'
            OR kun_readings LIKE :pattern ESCAPE '\'
        ORDER BY CASE WHEN character = :query THEN 0 ELSE 1 END, lesson_id, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun searchKanji(query: String, pattern: String, limit: Int, offset: Int): List<KanjiEntity>

    @Query(
        """
        SELECT * FROM grammar
        WHERE pattern LIKE :pattern ESCAPE '\' OR meaning_vi LIKE :pattern ESCAPE '\'
        ORDER BY CASE WHEN pattern = :query THEN 0 ELSE 1 END, lesson_id, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun searchGrammar(query: String, pattern: String, limit: Int, offset: Int): List<GrammarEntity>
}
