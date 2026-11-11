package com.n3sensei.app.testing

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.ContentPackInfo
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ContentTotals
import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Tag
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.model.VocabularyDetail
import com.n3sensei.app.domain.practice.PracticeItem
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

    val tags = MutableStateFlow<List<Tag>>(emptyList())
    val packs = MutableStateFlow<List<ContentPackInfo>>(emptyList())

    /** Marked item ids (any type); browse results report these as marked. */
    val marked = MutableStateFlow<Set<String>>(emptySet())

    val browseQueries = mutableListOf<ItemQuery>()

    /** Words of each kanji, by kanji id (none when absent). */
    val kanjiWords = mutableMapOf<String, List<KanjiWord>>()
    val grammarExamples = mutableMapOf<String, List<ExampleSentence>>()
    val vocabularyExamples = mutableMapOf<String, List<ExampleSentence>>()
    var kanjiWordCalls = 0

    var failDetails = false
    val searchCalls = mutableListOf<Pair<String, String?>>()

    override fun observeLessons(): Flow<List<LessonSummary>> = lessons
    override fun observeLesson(id: String): Flow<LessonSummary?> = lessons.map { all -> all.firstOrNull { it.id == id } }
    override fun observeContentTotals(): Flow<ContentTotals> =
        kotlinx.coroutines.flow.combine(lessons, vocabulary, kanji, grammar) { l, v, k, g ->
            ContentTotals(l.size, v.size, k.size, g.size)
        }
    override fun observeSources(): Flow<List<ContentSource>> = sources
    override fun observePacks(): Flow<List<ContentPackInfo>> = packs
    override fun observeTags(): Flow<List<Tag>> = tags

    override fun observeVocabularyItems(query: ItemQuery): Flow<List<TrackedItem<Vocabulary>>> =
        browse(query, vocabulary, { it.id }, { it.lessonId }) { v -> listOf(v.expression, v.reading, v.meaningVi) }

    override fun observeKanjiItems(query: ItemQuery): Flow<List<TrackedItem<Kanji>>> =
        browse(query, kanji, { it.id }, { it.lessonId }) { k -> listOf(k.character, k.meaningVi) }

    override fun observeGrammarItems(query: ItemQuery): Flow<List<TrackedItem<GrammarPoint>>> =
        browse(query, grammar, { it.id }, { it.lessonId }) { g -> listOf(g.pattern, g.meaningVi) }

    /** Status is always NEW here: fakes have no progress. A status filter other than NEW matches nothing. */
    private fun <T> browse(
        query: ItemQuery,
        source: MutableStateFlow<List<T>>,
        idOf: (T) -> String,
        lessonOf: (T) -> String,
        textOf: (T) -> List<String>,
    ): Flow<List<TrackedItem<T>>> {
        browseQueries += query
        return kotlinx.coroutines.flow.combine(source, marked) { items, marks ->
            items.asSequence()
                .filter { query.lessonId == null || lessonOf(it) == query.lessonId }
                .filter { query.text.isBlank() || textOf(it).any { t -> query.text.trim() in t } }
                .filter { !query.markedOnly || idOf(it) in marks }
                .filter { query.status == null || query.status == LearningStatus.NEW }
                // No user_words table here: the lesson and the generated id prefix stand for it.
                .filter { !query.userWordsOnly || (lessonOf(it) == UserWords.LESSON_ID && idOf(it).startsWith(UserWords.ID_PREFIX)) }
                .map { TrackedItem(it, LearningStatus.NEW, idOf(it) in marks) }
                .toList()
        }
    }

    override fun observeVocabulary(lessonId: String?) = vocabulary.map { it.inLesson(lessonId) { v -> v.lessonId } }
    override fun observeKanji(lessonId: String?) = kanji.map { it.inLesson(lessonId) { k -> k.lessonId } }
    override fun observeGrammar(lessonId: String?) = grammar.map { it.inLesson(lessonId) { g -> g.lessonId } }

    override suspend fun getPracticeItem(ref: ItemRef): PracticeItem? = when (ref.type) {
        ItemType.VOCABULARY -> vocabulary.value.firstOrNull { it.id == ref.id }?.let { PracticeItem.VocabularyItem(it, vocabularyExamples[it.id].orEmpty()) }
        ItemType.KANJI -> kanji.value.firstOrNull { it.id == ref.id }?.let { PracticeItem.KanjiItem(it, kanjiWords[it.id].orEmpty()) }
        ItemType.GRAMMAR -> grammar.value.firstOrNull { it.id == ref.id }?.let { PracticeItem.GrammarItem(it, grammarExamples[it.id].orEmpty()) }
    }

    override suspend fun getKanjiWords(kanjiId: String): List<KanjiWord> {
        kanjiWordCalls++
        return kanjiWords[kanjiId].orEmpty()
    }

    override suspend fun getVocabularyDetail(id: String): VocabularyDetail? {
        if (failDetails) error("database unavailable")
        return vocabulary.value.firstOrNull { it.id == id }?.let { VocabularyDetail(it, vocabularyExamples[id].orEmpty(), emptyList(), TestContent.source) }
    }

    override suspend fun getKanjiDetail(id: String): KanjiDetail? =
        kanji.value.firstOrNull { it.id == id }?.let { KanjiDetail(it, emptyList(), TestContent.source) }

    override suspend fun getGrammarDetail(id: String): GrammarDetail? =
        grammar.value.firstOrNull { it.id == id }?.let { GrammarDetail(it, grammarExamples[id].orEmpty(), TestContent.source) }

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
