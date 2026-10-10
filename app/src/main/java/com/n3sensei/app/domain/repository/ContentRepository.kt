package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ContentTotals
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.model.VocabularyDetail
import kotlinx.coroutines.flow.Flow

/** Read access to shared learning content. */
interface ContentRepository {
    fun observeLessons(): Flow<List<LessonSummary>>

    /** Emits null when the lesson does not exist. */
    fun observeLesson(id: String): Flow<LessonSummary?>

    fun observeContentTotals(): Flow<ContentTotals>

    fun observeSources(): Flow<List<ContentSource>>

    /** Items of one lesson in lesson order, or of all lessons when [lessonId] is null. */
    fun observeVocabulary(lessonId: String? = null): Flow<List<Vocabulary>>
    fun observeKanji(lessonId: String? = null): Flow<List<Kanji>>
    fun observeGrammar(lessonId: String? = null): Flow<List<GrammarPoint>>

    /** Returns null when the item does not exist (e.g. removed by a content update). */
    suspend fun getVocabularyDetail(id: String): VocabularyDetail?
    suspend fun getKanjiDetail(id: String): KanjiDetail?
    suspend fun getGrammarDetail(id: String): GrammarDetail?

    /**
     * Substring search, optionally within one lesson; a blank [query] returns an empty list.
     * [limit] is clamped to 1..[MAX_PAGE_SIZE]; use [offset] for further pages.
     */
    suspend fun searchVocabulary(query: String, lessonId: String? = null, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): List<Vocabulary>
    suspend fun searchKanji(query: String, lessonId: String? = null, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): List<Kanji>
    suspend fun searchGrammar(query: String, lessonId: String? = null, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): List<GrammarPoint>

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
        const val MAX_PAGE_SIZE = 100
    }
}
