package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.course.CourseBuilder
import com.n3sensei.app.domain.course.VocabularyGroups
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Which lessons of one kind reminders take from. */
sealed interface LessonSelection {
    /** Every lesson, including lessons added later. */
    data object All : LessonSelection

    /** Only these lessons ([LessonOption.key]); empty: none, so nothing of this kind. */
    data class Only(val keys: Set<String>) : LessonSelection

    fun allows(key: String?): Boolean = when (this) {
        All -> true
        is Only -> key != null && key in keys
    }
}

/** The lessons chosen for each kind of reminder content. */
data class ReminderLessons(
    val vocabulary: LessonSelection = LessonSelection.All,
    val kanji: LessonSelection = LessonSelection.All,
    val grammar: LessonSelection = LessonSelection.All,
) {
    fun of(type: ItemType): LessonSelection = when (type) {
        ItemType.VOCABULARY -> vocabulary
        ItemType.KANJI -> kanji
        ItemType.GRAMMAR -> grammar
    }

    fun with(type: ItemType, selection: LessonSelection): ReminderLessons = when (type) {
        ItemType.VOCABULARY -> copy(vocabulary = selection)
        ItemType.KANJI -> copy(kanji = selection)
        ItemType.GRAMMAR -> copy(grammar = selection)
    }
}

/**
 * One lesson to choose from, as the learner sees it in Học: a word group of Học → Từ vựng
 * ("1.1", "1.2", "3": a sub-group stays its own lesson), "Từ của tôi", or a lesson of
 * Học → Bài học for kanji and grammar.
 */
data class LessonOption(
    /** Stable key: the word group's key (`<lesson id>#<part>`), [ReminderLessonCatalog.MY_WORDS], or the lesson number. */
    val key: String,
    /** "1.2", "3"…; empty for "Từ của tôi". */
    val label: String,
    /** Title of the source lesson, when known. */
    val title: String?,
    val itemCount: Int,
)

/**
 * The lessons of each kind and where every item belongs, rebuilt from the content each time
 * (the same lists as Học, never stored), so a content update changes the choices at once.
 */
data class ReminderLessonCatalog(
    val options: Map<ItemType, List<LessonOption>>,
    /** The lesson key of each item; items in no lesson are absent (only allowed under "All"). */
    val lessonOf: Map<ItemRef, String>,
    /** Items never studied, in course order, by kind. */
    val newItems: Map<ItemType, List<ItemRef>>,
) {
    fun allows(ref: ItemRef, lessons: ReminderLessons): Boolean = lessons.of(ref.type).allows(lessonOf[ref])

    fun optionsOf(type: ItemType): List<LessonOption> = options[type].orEmpty()

    /** [selection] with keys no longer in the content dropped; [LessonSelection.All] stays as it is. */
    fun current(type: ItemType, selection: LessonSelection): LessonSelection = when (selection) {
        LessonSelection.All -> LessonSelection.All
        is LessonSelection.Only -> LessonSelection.Only(selection.keys intersect optionsOf(type).map { it.key }.toSet())
    }

    /** Toggles one lesson; selecting every lesson again becomes [LessonSelection.All] (so new lessons follow). */
    fun toggle(type: ItemType, selection: LessonSelection, key: String): LessonSelection {
        val all = optionsOf(type).map { it.key }.toSet()
        val chosen = when (val now = current(type, selection)) {
            LessonSelection.All -> all
            is LessonSelection.Only -> now.keys
        }
        val next = if (key in chosen) chosen - key else chosen + key
        return if (next == all) LessonSelection.All else LessonSelection.Only(next)
    }

    companion object {
        const val MY_WORDS = "my-words"
        val EMPTY = ReminderLessonCatalog(emptyMap(), emptyMap(), emptyMap())

        fun build(
            lessons: List<LessonSummary>,
            kanji: List<TrackedItem<Kanji>>,
            vocabulary: List<TrackedItem<Vocabulary>>,
            grammar: List<TrackedItem<GrammarPoint>>,
        ): ReminderLessonCatalog {
            val lessonOf = HashMap<ItemRef, String>()
            val groups = VocabularyGroups.build(lessons, vocabulary)
            groups.forEach { g -> g.words.forEach { lessonOf[ItemRef(ItemType.VOCABULARY, it.item.id)] = g.key } }
            val myWords = vocabulary.filter { it.item.lessonId == UserWords.LESSON_ID }
            myWords.forEach { lessonOf[ItemRef(ItemType.VOCABULARY, it.item.id)] = MY_WORDS }
            val course = CourseBuilder.build(lessons, kanji, vocabulary, grammar)
            course.forEach { lesson ->
                lesson.kanji.forEach { lessonOf[ItemRef(ItemType.KANJI, it.item.id)] = lesson.number.toString() }
                lesson.grammar.forEach { lessonOf[ItemRef(ItemType.GRAMMAR, it.item.id)] = lesson.number.toString() }
            }
            val vocabularyOptions = groups.map { LessonOption(it.key, it.label, it.sourceTitle.ifEmpty { null }, it.words.size) } +
                listOfNotNull(myWords.takeIf { it.isNotEmpty() }?.let { LessonOption(MY_WORDS, "", null, it.size) })
            val options = mapOf(
                ItemType.VOCABULARY to vocabularyOptions,
                ItemType.KANJI to course.filter { it.kanji.isNotEmpty() }.map { LessonOption(it.number.toString(), it.number.toString(), null, it.kanji.size) },
                ItemType.GRAMMAR to course.filter { it.grammar.isNotEmpty() }.map { LessonOption(it.number.toString(), it.number.toString(), null, it.grammar.size) },
            )
            fun <T> newOf(type: ItemType, items: List<TrackedItem<T>>, id: (T) -> String) =
                items.filter { it.status == LearningStatus.NEW }.map { ItemRef(type, id(it.item)) }
            val newItems = mapOf(
                ItemType.VOCABULARY to newOf(ItemType.VOCABULARY, groups.flatMap { it.words } + myWords) { it.id },
                ItemType.KANJI to newOf(ItemType.KANJI, course.flatMap { it.kanji }) { it.id },
                ItemType.GRAMMAR to newOf(ItemType.GRAMMAR, course.flatMap { it.grammar }) { it.id },
            )
            return ReminderLessonCatalog(options, lessonOf, newItems)
        }

        fun observe(content: ContentRepository): Flow<ReminderLessonCatalog> = combine(
            content.observeLessons(),
            content.observeKanjiItems(ItemQuery()),
            content.observeVocabularyItems(ItemQuery()),
            content.observeGrammarItems(ItemQuery()),
        ) { lessons, kanji, vocabulary, grammar -> build(lessons, kanji, vocabulary, grammar) }
    }
}
