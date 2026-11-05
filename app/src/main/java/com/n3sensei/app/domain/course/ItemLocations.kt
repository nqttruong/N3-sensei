package com.n3sensei.app.domain.course

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Where an item is studied in the app, from the same lists the learner sees (not from ids):
 * - a word: its group of Học → Từ vựng ("1.2") and its lesson of Học → Bài học;
 * - a kanji or grammar point: its lesson of Học → Bài học;
 * - the learner's own word: "Từ của tôi" (no lesson);
 * - anything not found in those lists: no lesson ([Kind.NONE]); never guessed.
 */
data class ItemLocation(
    val kind: Kind,
    /** Lesson of Học → Bài học (1, 2…), when the item is in one. */
    val courseLesson: Int? = null,
    /** Group of Học → Từ vựng ("1.2" or "3"), words only. */
    val vocabularyGroup: String? = null,
    /** Title of the item's source lesson (its pack lesson), when known. */
    val sourceTitle: String? = null,
) {
    enum class Kind { VOCABULARY, KANJI, GRAMMAR, MY_WORDS, NONE }

    companion object {
        val NONE = ItemLocation(Kind.NONE)
        val MY_WORDS = ItemLocation(Kind.MY_WORDS)
    }
}

/** A word written and read the same way as another one, in another lesson. */
data class SameWord(val vocabularyId: String, val location: ItemLocation)

/** Locations of every item, rebuilt from the content whenever it changes. Nothing is stored. */
class ItemLocations(
    private val byRef: Map<ItemRef, ItemLocation>,
    private val vocabulary: Map<String, Vocabulary>,
) {
    fun of(ref: ItemRef): ItemLocation = byRef[ref] ?: ItemLocation.NONE

    /**
     * Other records of the same word (same spelling and reading): packs may each have it, in
     * different lessons. The learner's own words are included, as "Từ của tôi".
     */
    fun sameWord(vocabularyId: String): List<SameWord> {
        val word = vocabulary[vocabularyId] ?: return emptyList()
        return vocabulary.values
            .filter { it.id != vocabularyId && it.expression == word.expression && it.reading == word.reading }
            .map { SameWord(it.id, of(ItemRef(ItemType.VOCABULARY, it.id))) }
    }

    companion object {
        val EMPTY = ItemLocations(emptyMap(), emptyMap())

        fun build(
            lessons: List<LessonSummary>,
            kanji: List<TrackedItem<Kanji>>,
            vocabulary: List<TrackedItem<Vocabulary>>,
            grammar: List<TrackedItem<GrammarPoint>>,
        ): ItemLocations {
            val titles = lessons.associate { it.id to it.title }
            val map = HashMap<ItemRef, ItemLocation>()
            val course = CourseBuilder.build(lessons, kanji, vocabulary, grammar)
            val courseOfWord = HashMap<String, Int>()
            course.forEach { lesson ->
                lesson.kanji.forEach {
                    map[ItemRef(ItemType.KANJI, it.item.id)] =
                        ItemLocation(ItemLocation.Kind.KANJI, courseLesson = lesson.number, sourceTitle = titles[it.item.lessonId])
                }
                lesson.grammar.forEach {
                    map[ItemRef(ItemType.GRAMMAR, it.item.id)] =
                        ItemLocation(ItemLocation.Kind.GRAMMAR, courseLesson = lesson.number, sourceTitle = titles[it.item.lessonId])
                }
                lesson.words.forEach { courseOfWord[it.item.id] = lesson.number }
            }
            VocabularyGroups.build(lessons, vocabulary).forEach { group ->
                group.words.forEach {
                    map[ItemRef(ItemType.VOCABULARY, it.item.id)] = ItemLocation(
                        ItemLocation.Kind.VOCABULARY,
                        courseLesson = courseOfWord[it.item.id],
                        vocabularyGroup = group.label,
                        sourceTitle = group.sourceTitle.ifEmpty { null },
                    )
                }
            }
            // The learner's own words (made by "Thêm từ của tôi", see UserWords.ID_PREFIX); any other
            // row in that lesson has no lesson to show.
            vocabulary.filter { it.item.lessonId == UserWords.LESSON_ID && it.item.id.startsWith(UserWords.ID_PREFIX) }
                .forEach { map[ItemRef(ItemType.VOCABULARY, it.item.id)] = ItemLocation.MY_WORDS }
            return ItemLocations(map, vocabulary.associate { it.item.id to it.item })
        }

        fun observe(contentRepository: ContentRepository): Flow<ItemLocations> = combine(
            contentRepository.observeLessons(),
            contentRepository.observeKanjiItems(ItemQuery()),
            contentRepository.observeVocabularyItems(ItemQuery()),
            contentRepository.observeGrammarItems(ItemQuery()),
        ) { lessons, kanji, vocabulary, grammar -> build(lessons, kanji, vocabulary, grammar) }
    }
}
