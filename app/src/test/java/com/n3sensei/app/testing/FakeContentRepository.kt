package com.n3sensei.app.testing

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ContentRepository] for ViewModel tests. */
class FakeContentRepository : ContentRepository {
    val lessons = MutableStateFlow<List<LessonSummary>>(emptyList())
    val vocabulary = MutableStateFlow<List<Vocabulary>>(emptyList())
    val kanji = MutableStateFlow<List<Kanji>>(emptyList())
    val grammar = MutableStateFlow<List<GrammarPoint>>(emptyList())
    val sources = MutableStateFlow<List<ContentSource>>(emptyList())

    var failDetails = false
    val searchCalls = mutableListOf<Pair<String, String?>>()

    override fun observeLessons(): Flow<List<LessonSummary>> = lessons
    override fun observeLesson(id: String): Flow<LessonSummary?> = lessons.map { all -> all.firstOrNull { it.id == id } }
    override fun observeContentTotals(): Flow<ContentTotals> =
        kotlinx.coroutines.flow.combine(lessons, vocabulary, kanji, grammar) { l, v, k, g ->
            ContentTotals(l.size, v.size, k.size, g.size)
        }
    override fun observeSources(): Flow<List<ContentSource>> = sources

    override fun observeVocabulary(lessonId: String?) = vocabulary.map { it.inLesson(lessonId) { v -> v.lessonId } }
    override fun observeKanji(lessonId: String?) = kanji.map { it.inLesson(lessonId) { k -> k.lessonId } }
    override fun observeGrammar(lessonId: String?) = grammar.map { it.inLesson(lessonId) { g -> g.lessonId } }

    override suspend fun getVocabularyDetail(id: String): VocabularyDetail? {
        if (failDetails) error("database unavailable")
        return vocabulary.value.firstOrNull { it.id == id }?.let { VocabularyDetail(it, emptyList(), emptyList(), TestContent.source) }
    }

    override suspend fun getKanjiDetail(id: String): KanjiDetail? =
        kanji.value.firstOrNull { it.id == id }?.let { KanjiDetail(it, emptyList(), TestContent.source) }

    override suspend fun getGrammarDetail(id: String): GrammarDetail? =
        grammar.value.firstOrNull { it.id == id }?.let { GrammarDetail(it, emptyList(), TestContent.source) }

    override suspend fun searchVocabulary(query: String, lessonId: String?, limit: Int, offset: Int): List<Vocabulary> {
        searchCalls += query to lessonId
        return vocabulary.value.inLesson(lessonId) { it.lessonId }
            .filter { query in it.expression || query in it.reading || query in it.meaningVi }
            .take(limit)
    }

    override suspend fun searchKanji(query: String, lessonId: String?, limit: Int, offset: Int): List<Kanji> =
        kanji.value.inLesson(lessonId) { it.lessonId }.filter { query in it.character }.take(limit)

    override suspend fun searchGrammar(query: String, lessonId: String?, limit: Int, offset: Int): List<GrammarPoint> =
        grammar.value.inLesson(lessonId) { it.lessonId }.filter { query in it.pattern }.take(limit)

    private fun <T> List<T>.inLesson(lessonId: String?, lessonOf: (T) -> String) =
        if (lessonId == null) this else filter { lessonOf(it) == lessonId }
}
