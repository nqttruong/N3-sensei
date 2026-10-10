package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.SqlLike
import com.n3sensei.app.data.local.dao.ContentDao
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ContentTotals
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.model.VocabularyDetail
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ContentRepository.Companion.MAX_PAGE_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineContentRepository(
    private val contentDao: ContentDao,
) : ContentRepository {

    override fun observeLessons(): Flow<List<LessonSummary>> =
        contentDao.observeLessonSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeLesson(id: String): Flow<LessonSummary?> =
        contentDao.observeLessonSummary(id).map { it?.toDomain() }

    override fun observeContentTotals(): Flow<ContentTotals> =
        contentDao.observeContentTotals().map { ContentTotals(it.lessons, it.vocabulary, it.kanji, it.grammar) }

    override fun observeSources(): Flow<List<ContentSource>> =
        contentDao.observeSources().map { rows -> rows.map { it.toDomain() } }

    override fun observeVocabulary(lessonId: String?): Flow<List<Vocabulary>> =
        contentDao.observeVocabulary(lessonId).map { rows -> rows.map { it.toDomain() } }

    override fun observeKanji(lessonId: String?): Flow<List<Kanji>> =
        contentDao.observeKanji(lessonId).map { rows -> rows.map { it.toDomain() } }

    override fun observeGrammar(lessonId: String?): Flow<List<GrammarPoint>> =
        contentDao.observeGrammar(lessonId).map { rows -> rows.map { it.toDomain() } }

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
        return run(trimmed, SqlLike.contains(trimmed), limit.coerceIn(1, MAX_PAGE_SIZE), offset.coerceAtLeast(0))
    }
}
