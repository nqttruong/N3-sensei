package com.n3sensei.app.domain.course

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.model.ItemQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * "Học → Bài học": lessons that each take the next 24 kanji, 30 vocabulary items and 7
 * grammar points, each list in its own existing order (the order of Học → Kanji / Từ vựng /
 * Ngữ pháp: pack, lesson, position). Nothing is stored: a lesson is a view over the items'
 * stable ids, rebuilt from the content, so progress stays with the items.
 */
object CoursePlan {
    const val KANJI_PER_LESSON = 24
    const val VOCABULARY_PER_LESSON = 30
    const val GRAMMAR_PER_LESSON = 7

    /** Size of a vocabulary sub-group ("1.1", "1.2"…) inside a source group. */
    const val SUBGROUP_SIZE = 30

    /** Lesson n holds chunk n of every list; lessons continue while any list has items. */
    fun <K, V, G> lessons(kanji: List<K>, vocabulary: List<V>, grammar: List<G>): List<Triple<List<K>, List<V>, List<G>>> {
        val k = kanji.chunked(KANJI_PER_LESSON)
        val v = vocabulary.chunked(VOCABULARY_PER_LESSON)
        val g = grammar.chunked(GRAMMAR_PER_LESSON)
        val count = maxOf(k.size, v.size, g.size)
        return List(count) { i -> Triple(k.getOrElse(i) { emptyList() }, v.getOrElse(i) { emptyList() }, g.getOrElse(i) { emptyList() }) }
    }

    /**
     * Sub-group label of every item of a list whose items belong to source groups ([groupOf],
     * in list order): group n's items 1–30 are "n.1", 31–60 "n.2"… A group of 30 or fewer is
     * just "n". Groups are numbered 1, 2… in the order they first appear.
     */
    fun subgroupLabels(groupOf: List<String>): List<String> {
        val sizes = groupOf.groupingBy { it }.eachCount()
        val number = LinkedHashMap<String, Int>()
        val position = HashMap<String, Int>()
        return groupOf.map { group ->
            val n = number.getOrPut(group) { number.size + 1 }
            val p = position.merge(group, 1, Int::plus)!! - 1
            if (sizes.getValue(group) <= SUBGROUP_SIZE) "$n" else "$n.${p / SUBGROUP_SIZE + 1}"
        }
    }
}

/** Consecutive vocabulary items of a lesson from the same sub-group. */
data class VocabularySection(
    /** "1.2", or "6" for a source group of 30 words or fewer. */
    val label: String,
    /** Title of the source group (its pack lesson). */
    val groupTitle: String,
    val words: List<TrackedItem<Vocabulary>>,
)

data class CourseLesson(
    val number: Int,
    val kanji: List<TrackedItem<Kanji>>,
    val vocabulary: List<VocabularySection>,
    val grammar: List<TrackedItem<GrammarPoint>>,
) {
    val words: List<TrackedItem<Vocabulary>> get() = vocabulary.flatMap { it.words }
    val isEmpty: Boolean get() = kanji.isEmpty() && vocabulary.isEmpty() && grammar.isEmpty()

    /** Items started (no longer new), per kind: progress comes from each item's own record. */
    fun startedKanji() = kanji.count { it.status != LearningStatus.NEW }
    fun startedWords() = words.count { it.status != LearningStatus.NEW }
    fun startedGrammar() = grammar.count { it.status != LearningStatus.NEW }
}

/** Builds the lessons from the content as it is now, and again whenever it changes. */
class CourseBuilder(private val contentRepository: ContentRepository) {

    fun observe(): Flow<List<CourseLesson>> = combine(
        contentRepository.observeLessons(),
        contentRepository.observeKanjiItems(ItemQuery()),
        contentRepository.observeVocabularyItems(ItemQuery()),
        contentRepository.observeGrammarItems(ItemQuery()),
    ) { lessons, kanji, vocabulary, grammar -> build(lessons, kanji, vocabulary, grammar) }

    companion object {
        /** Level of the course (Học → Bài học); kanji of other levels are left out. */
        const val COURSE_LEVEL = KanjiReviewLessons.COURSE_LEVEL

        /**
         * The learner's own words ("Từ của tôi") are not part of the course: they change at any
         * time and would shift every lesson after them. Neither are kanji of another JLPT level
         * than the course's (Kanji N2): they are studied by their own lessons in Học → Kanji.
         */
        fun build(
            lessons: List<LessonSummary>,
            kanji: List<TrackedItem<Kanji>>,
            vocabulary: List<TrackedItem<Vocabulary>>,
            grammar: List<TrackedItem<GrammarPoint>>,
        ): List<CourseLesson> {
            val words = vocabulary.filter { it.item.lessonId != UserWords.LESSON_ID }
            val levels = lessons.associate { it.id to it.jlptLevel }
            val courseKanji = kanji.filter { (levels[it.item.lessonId] ?: COURSE_LEVEL) == COURSE_LEVEL }
            val labels = CoursePlan.subgroupLabels(words.map { it.item.lessonId })
            val titles = lessons.associate { it.id to it.title }
            val labelled = words.mapIndexed { i, w -> Triple(labels[i], titles[w.item.lessonId].orEmpty(), w) }
            return CoursePlan.lessons(courseKanji, labelled, grammar).mapIndexed { i, (k, v, g) ->
                val sections = mutableListOf<VocabularySection>()
                v.forEach { (label, title, word) ->
                    val last = sections.lastOrNull()
                    if (last != null && last.label == label) {
                        sections[sections.lastIndex] = last.copy(words = last.words + word)
                    } else {
                        sections += VocabularySection(label, title, listOf(word))
                    }
                }
                CourseLesson(i + 1, k, sections, g)
            }
        }
    }
}
