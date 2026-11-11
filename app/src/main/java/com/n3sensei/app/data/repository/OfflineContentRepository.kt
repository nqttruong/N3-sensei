package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.SearchText
import com.n3sensei.app.data.local.SqlLike
import com.n3sensei.app.data.local.dao.ContentDao
import com.n3sensei.app.data.local.dao.ContentPackDao
import com.n3sensei.app.domain.model.ContentPackInfo
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ContentTotals
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Tag
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.model.VocabularyDetail
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ContentRepository.Companion.MAX_PAGE_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineContentRepository(
    private val contentDao: ContentDao,
    private val contentPackDao: ContentPackDao,
) : ContentRepository {

    override fun observeLessons(): Flow<List<LessonSummary>> =
        contentDao.observeLessonSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeLesson(id: String): Flow<LessonSummary?> =
        contentDao.observeLessonSummary(id).map { it?.toDomain() }

    override fun observeContentTotals(): Flow<ContentTotals> =
        contentDao.observeContentTotals().map { ContentTotals(it.lessons, it.vocabulary, it.kanji, it.grammar) }

    override fun observeSources(): Flow<List<ContentSource>> =
        contentDao.observeSources().map { rows -> rows.map { it.toDomain() } }

    override fun observePacks(): Flow<List<ContentPackInfo>> =
        contentPackDao.observePackRows().map { rows -> rows.map { it.toDomain() } }

    override fun observeTags(): Flow<List<Tag>> =
        contentDao.observeUsedTags().map { rows -> rows.map { it.toDomain() } }

    override fun observeVocabularyItems(query: ItemQuery): Flow<List<TrackedItem<Vocabulary>>> {
        val text = BrowseText.of(query)
        return contentDao.observeVocabularyItems(
            query.lessonId, query.tagId, query.status?.name, query.markedOnly, text.pattern, text.raw, text.limit,
            query.userWordsOnly,
            query.includeSaved,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeKanjiItems(query: ItemQuery): Flow<List<TrackedItem<Kanji>>> {
        val text = BrowseText.of(query)
        return contentDao.observeKanjiItems(
            query.lessonId, query.status?.name, query.markedOnly, text.pattern, text.raw, text.limit,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeGrammarItems(query: ItemQuery): Flow<List<TrackedItem<GrammarPoint>>> {
        val text = BrowseText.of(query)
        return contentDao.observeGrammarItems(
            query.lessonId, query.status?.name, query.markedOnly, text.pattern, text.raw, text.limit,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeVocabulary(lessonId: String?): Flow<List<Vocabulary>> =
        contentDao.observeVocabulary(lessonId).map { rows -> rows.map { it.toDomain() } }

    override fun observeKanji(lessonId: String?): Flow<List<Kanji>> =
        contentDao.observeKanji(lessonId).map { rows -> rows.map { it.toDomain() } }

    override fun observeGrammar(lessonId: String?): Flow<List<GrammarPoint>> =
        contentDao.observeGrammar(lessonId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPracticeItem(ref: ItemRef): PracticeItem? = when (ref.type) {
        // Examples linked to this record's id only, as on its detail screen.
        ItemType.VOCABULARY -> contentDao.getVocabulary(ref.id)?.let { v ->
            PracticeItem.VocabularyItem(v.toDomain(), contentDao.getVocabularyExamples(v.id).map { it.toDomain() })
        }
        ItemType.KANJI -> contentDao.getKanji(ref.id)?.let { PracticeItem.KanjiItem(it.toDomain(), getKanjiWords(it.id)) }
        ItemType.GRAMMAR -> contentDao.getGrammar(ref.id)?.let { g ->
            PracticeItem.GrammarItem(g.toDomain(), contentDao.getGrammarExamples(g.id).map { it.toDomain() })
        }
    }

    /** The pack's example words, then the learner's own examples, then the learner's linked words. */
    override suspend fun getKanjiWords(kanjiId: String): List<KanjiWord> =
        (contentDao.getKanjiExamples(kanjiId) + contentDao.getOwnKanjiExamples(kanjiId))
            .map { KanjiWord(it.japanese, it.furigana, it.translationVi) } +
            contentDao.getLinkedVocabulary(kanjiId).map { KanjiWord(it.expression, it.furigana, it.meaningVi, vocabularyId = it.id) }

    override suspend fun getVocabularyDetail(id: String): VocabularyDetail? =
        contentDao.getVocabularyDetail(id)?.toDomain()

    override suspend fun getKanjiDetail(id: String): KanjiDetail? =
        contentDao.getKanjiDetail(id)?.toDomain()

    override suspend fun getGrammarDetail(id: String): GrammarDetail? =
        contentDao.getGrammarDetail(id)?.toDomain()

    override suspend fun searchVocabulary(query: String, lessonId: String?, limit: Int, offset: Int): List<Vocabulary> =
        search(query, limit, offset) { q, pattern, l, o ->
            contentDao.searchVocabulary(q, pattern, lessonId, l, o).map { it.toDomain() }
        }

    override suspend fun searchKanji(query: String, lessonId: String?, limit: Int, offset: Int): List<Kanji> =
        search(query, limit, offset) { q, pattern, l, o ->
            contentDao.searchKanji(q, pattern, lessonId, l, o).map { it.toDomain() }
        }

    override suspend fun searchGrammar(query: String, lessonId: String?, limit: Int, offset: Int): List<GrammarPoint> =
        search(query, limit, offset) { q, pattern, l, o ->
            contentDao.searchGrammar(q, pattern, lessonId, l, o).map { it.toDomain() }
        }

    private inline fun <T> search(
        query: String,
        limit: Int,
        offset: Int,
        run: (query: String, pattern: String, limit: Int, offset: Int) -> List<T>,
    ): List<T> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return run(trimmed, SqlLike.contains(SearchText.normalize(trimmed)), limit.coerceIn(1, MAX_PAGE_SIZE), offset.coerceAtLeast(0))
    }

    /** Text part of a browse query: no pattern and no limit when the text is blank. */
    private class BrowseText(val raw: String?, val pattern: String?, val limit: Int) {
        companion object {
            fun of(query: ItemQuery): BrowseText {
                val trimmed = query.text.trim()
                return if (trimmed.isEmpty()) {
                    BrowseText(null, null, NO_LIMIT)
                } else {
                    BrowseText(trimmed, SqlLike.contains(SearchText.normalize(trimmed)), MAX_PAGE_SIZE)
                }
            }

            private const val NO_LIMIT = -1
        }
    }
}
