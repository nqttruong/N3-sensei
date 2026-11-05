package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExerciseEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.model.GrammarDetailRow
import com.n3sensei.app.data.local.model.GrammarItemRow
import com.n3sensei.app.data.local.model.KanjiItemRow
import com.n3sensei.app.data.local.model.ContentTotalsRow
import com.n3sensei.app.data.local.model.KanjiDetailRow
import com.n3sensei.app.data.local.model.LessonSummaryRow
import com.n3sensei.app.data.local.model.VocabularyDetailRow
import com.n3sensei.app.data.local.model.VocabularyItemRow
import kotlinx.coroutines.flow.Flow

private const val LESSON_SUMMARY_SELECT = """
    SELECT l.*,
        (SELECT COUNT(*) FROM vocabulary v WHERE v.lesson_id = l.id) AS vocabulary_count,
        (SELECT COUNT(*) FROM kanji k WHERE k.lesson_id = l.id) AS kanji_count,
        (SELECT COUNT(*) FROM grammar g WHERE g.lesson_id = l.id) AS grammar_count
    FROM lessons l
"""

/** Read queries over shared content. Writes go through [ContentPackDao]. */
@Dao
abstract class ContentDao {

    @Query("$LESSON_SUMMARY_SELECT ORDER BY l.pack_id, l.number")
    abstract fun observeLessonSummaries(): Flow<List<LessonSummaryRow>>

    @Query("$LESSON_SUMMARY_SELECT WHERE l.id = :id")
    abstract fun observeLessonSummary(id: String): Flow<LessonSummaryRow?>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM lessons) AS lessons,
            (SELECT COUNT(*) FROM vocabulary) AS vocabulary,
            (SELECT COUNT(*) FROM kanji) AS kanji,
            (SELECT COUNT(*) FROM grammar) AS grammar
        """,
    )
    abstract fun observeContentTotals(): Flow<ContentTotalsRow>

    @Query("SELECT * FROM content_sources ORDER BY name")
    abstract fun observeSources(): Flow<List<ContentSourceEntity>>

    // Item lists: one lesson, or every lesson (null) in course order.

    @Query(
        """
        SELECT v.* FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id
        WHERE :lessonId IS NULL OR v.lesson_id = :lessonId
        ORDER BY l.pack_id, l.number, v.sort_order
        """,
    )
    abstract fun observeVocabulary(lessonId: String?): Flow<List<VocabularyEntity>>

    @Query(
        """
        SELECT k.* FROM kanji k JOIN lessons l ON l.id = k.lesson_id
        WHERE :lessonId IS NULL OR k.lesson_id = :lessonId
        ORDER BY l.pack_id, l.number, k.sort_order
        """,
    )
    abstract fun observeKanji(lessonId: String?): Flow<List<KanjiEntity>>

    @Query(
        """
        SELECT g.* FROM grammar g JOIN lessons l ON l.id = g.lesson_id
        WHERE :lessonId IS NULL OR g.lesson_id = :lessonId
        ORDER BY l.pack_id, l.number, g.sort_order
        """,
    )
    abstract fun observeGrammar(lessonId: String?): Flow<List<GrammarEntity>>

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

    /** Examples the learner added to a kanji, in the order they were added. */
    @Query(
        """
        SELECT e.* FROM example_sentences e
        JOIN user_kanji_examples x ON x.example_id = e.id
        WHERE x.kanji_id = :kanjiId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun getOwnKanjiExamples(kanjiId: String): List<ExampleSentenceEntity>

    /** Vocabulary the learner linked to a kanji (not the kanji's own example words). */
    @Query(
        """
        SELECT v.* FROM vocabulary v JOIN vocabulary_kanji_links x ON x.vocabulary_id = v.id
        WHERE x.kanji_id = :kanjiId ORDER BY x.created_at, v.id
        """,
    )
    abstract suspend fun getLinkedVocabulary(kanjiId: String): List<VocabularyEntity>

    /** Kanji the learner linked a word to. */
    @Query(
        """
        SELECT k.* FROM kanji k JOIN vocabulary_kanji_links x ON x.kanji_id = k.id
        WHERE x.vocabulary_id = :vocabularyId ORDER BY x.created_at, k.id
        """,
    )
    abstract suspend fun getLinkedKanji(vocabularyId: String): List<KanjiEntity>

    @Query(
        """
        SELECT e.* FROM example_sentences e
        JOIN grammar_examples x ON x.example_id = e.id
        WHERE x.grammar_id = :grammarId ORDER BY x.sort_order
        """,
    )
    abstract suspend fun getGrammarExamples(grammarId: String): List<ExampleSentenceEntity>

    @Query("SELECT * FROM grammar_exercises WHERE grammar_id = :grammarId ORDER BY sort_order")
    abstract suspend fun getGrammarExercises(grammarId: String): List<GrammarExerciseEntity>

    @Query("SELECT * FROM tags WHERE id IN (SELECT tag_id FROM vocabulary_tags) ORDER BY name")
    abstract fun observeUsedTags(): Flow<List<TagEntity>>

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
        return VocabularyDetailRow(vocabulary, getVocabularyExamples(id), getVocabularyTags(id), source, getLinkedKanji(id))
    }

    @Transaction
    open suspend fun getKanjiDetail(id: String): KanjiDetailRow? {
        val kanji = getKanji(id) ?: return null
        val source = getSource(kanji.sourceId) ?: return null
        return KanjiDetailRow(kanji, getKanjiExamples(id) + getOwnKanjiExamples(id), source, getLinkedVocabulary(id))
    }

    @Transaction
    open suspend fun getGrammarDetail(id: String): GrammarDetailRow? {
        val grammar = getGrammar(id) ?: return null
        val source = getSource(grammar.sourceId) ?: return null
        return GrammarDetailRow(grammar, getGrammarExamples(id), source, getGrammarExercises(id))
    }

    // Search over normalized search_text: `pattern` = SqlLike.contains(SearchText.normalize(query));
    // exact matches on the raw query rank first.

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE (:lessonId IS NULL OR lesson_id = :lessonId)
            AND search_text LIKE :pattern ESCAPE '\'
        ORDER BY CASE WHEN expression = :query OR reading = :query THEN 0 ELSE 1 END,
            lesson_id, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun searchVocabulary(
        query: String,
        pattern: String,
        lessonId: String?,
        limit: Int,
        offset: Int,
    ): List<VocabularyEntity>

    @Query(
        """
        SELECT * FROM kanji
        WHERE (:lessonId IS NULL OR lesson_id = :lessonId)
            AND search_text LIKE :pattern ESCAPE '\'
        ORDER BY CASE WHEN character = :query THEN 0 ELSE 1 END, lesson_id, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun searchKanji(
        query: String,
        pattern: String,
        lessonId: String?,
        limit: Int,
        offset: Int,
    ): List<KanjiEntity>

    @Query(
        """
        SELECT * FROM grammar
        WHERE (:lessonId IS NULL OR lesson_id = :lessonId)
            AND search_text LIKE :pattern ESCAPE '\'
        ORDER BY CASE WHEN pattern = :query THEN 0 ELSE 1 END, lesson_id, sort_order
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun searchGrammar(
        query: String,
        pattern: String,
        lessonId: String?,
        limit: Int,
        offset: Int,
    ): List<GrammarEntity>

    // Browse queries: optional filters (null = any), learner state joined in. `pattern` is a
    // LIKE pattern over normalized search_text; `query` is the raw text for exact-match ranking.
    // A negative `limit` means no limit.

    @Query(
        """
        SELECT v.*,
            (m.item_id IS NOT NULL) AS is_marked,
            p.state AS progress_state
        FROM vocabulary v
        JOIN lessons l ON l.id = v.lesson_id
        LEFT JOIN learning_progress p ON p.item_type = 'VOCABULARY' AND p.item_id = v.id
        LEFT JOIN item_marks m ON m.item_type = 'VOCABULARY' AND m.item_id = v.id
        WHERE (:lessonId IS NULL OR v.lesson_id = :lessonId)
            AND (:tagId IS NULL OR EXISTS (
                SELECT 1 FROM vocabulary_tags t WHERE t.vocabulary_id = v.id AND t.tag_id = :tagId))
            AND (:status IS NULL OR (:status = 'NEW' AND p.item_id IS NULL) OR p.state = :status)
            AND (:markedOnly = 0 OR m.item_id IS NOT NULL)
            AND (:pattern IS NULL OR v.search_text LIKE :pattern ESCAPE '\')
            AND (:userWordsOnly = 0 OR (v.lesson_id = 'user-words-l1' AND v.id LIKE 'user-w-%'
                AND EXISTS (SELECT 1 FROM user_words u WHERE u.vocabulary_id = v.id)))
        ORDER BY CASE WHEN v.expression = :query OR v.reading = :query THEN 0 ELSE 1 END,
            l.pack_id, l.number, v.sort_order
        LIMIT :limit
        """,
    )
    abstract fun observeVocabularyItems(
        lessonId: String?,
        tagId: String?,
        status: String?,
        markedOnly: Boolean,
        pattern: String?,
        query: String?,
        limit: Int,
        // Literals above match UserWords.LESSON_ID and UserWords.ID_PREFIX.
        userWordsOnly: Boolean = false,
    ): Flow<List<VocabularyItemRow>>

    @Query(
        """
        SELECT k.*,
            (m.item_id IS NOT NULL) AS is_marked,
            p.state AS progress_state
        FROM kanji k
        JOIN lessons l ON l.id = k.lesson_id
        LEFT JOIN learning_progress p ON p.item_type = 'KANJI' AND p.item_id = k.id
        LEFT JOIN item_marks m ON m.item_type = 'KANJI' AND m.item_id = k.id
        WHERE (:lessonId IS NULL OR k.lesson_id = :lessonId)
            AND (:status IS NULL OR (:status = 'NEW' AND p.item_id IS NULL) OR p.state = :status)
            AND (:markedOnly = 0 OR m.item_id IS NOT NULL)
            AND (:pattern IS NULL OR k.search_text LIKE :pattern ESCAPE '\')
        ORDER BY CASE WHEN k.character = :query THEN 0 ELSE 1 END, l.pack_id, l.number, k.sort_order
        LIMIT :limit
        """,
    )
    abstract fun observeKanjiItems(
        lessonId: String?,
        status: String?,
        markedOnly: Boolean,
        pattern: String?,
        query: String?,
        limit: Int,
    ): Flow<List<KanjiItemRow>>

    @Query(
        """
        SELECT g.*,
            (m.item_id IS NOT NULL) AS is_marked,
            p.state AS progress_state
        FROM grammar g
        JOIN lessons l ON l.id = g.lesson_id
        LEFT JOIN learning_progress p ON p.item_type = 'GRAMMAR' AND p.item_id = g.id
        LEFT JOIN item_marks m ON m.item_type = 'GRAMMAR' AND m.item_id = g.id
        WHERE (:lessonId IS NULL OR g.lesson_id = :lessonId)
            AND (:status IS NULL OR (:status = 'NEW' AND p.item_id IS NULL) OR p.state = :status)
            AND (:markedOnly = 0 OR m.item_id IS NOT NULL)
            AND (:pattern IS NULL OR g.search_text LIKE :pattern ESCAPE '\')
        ORDER BY CASE WHEN g.pattern = :query THEN 0 ELSE 1 END, l.pack_id, l.number, g.sort_order
        LIMIT :limit
        """,
    )
    abstract fun observeGrammarItems(
        lessonId: String?,
        status: String?,
        markedOnly: Boolean,
        pattern: String?,
        query: String?,
        limit: Int,
    ): Flow<List<GrammarItemRow>>
}
