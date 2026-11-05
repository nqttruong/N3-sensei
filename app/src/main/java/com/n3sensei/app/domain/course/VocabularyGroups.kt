package com.n3sensei.app.domain.course

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * At most [VocabularyGroups.MAX_WORDS] consecutive words of one source lesson: "Bài 3" when
 * the lesson has 30 words or fewer, otherwise "Bài 3.1", "Bài 3.2"…
 *
 * @property key stable id of the group (`<lesson id>#<part>`), the same after a restart as
 *   long as the lesson's words are the same.
 * @property parentNumber position of the source lesson in the list (1, 2…), as in the
 *   sub-group labels of Học → Bài học.
 */
data class VocabularyGroup(
    val key: String,
    val lessonId: String,
    val parentNumber: Int,
    val part: Int,
    val partCount: Int,
    val sourceTitle: String,
    val words: List<TrackedItem<Vocabulary>>,
) {
    /** "3" or "3.2". */
    val label: String get() = if (partCount == 1) "$parentNumber" else "$parentNumber.$part"

    /** Words started (no longer new), from each word's own progress record. */
    fun started(): Int = words.count { it.status != LearningStatus.NEW }
}

/**
 * "Học → Từ vựng": the vocabulary of every source lesson, in its existing order, cut into
 * groups of at most 30 words. Nothing is stored or copied: a group is a view over the words'
 * ids, rebuilt from the content, so progress stays with the words and needs no mapping.
 * Lessons are never merged to fill a group; a lesson without words has no group.
 */
object VocabularyGroups {
    const val MAX_WORDS = CoursePlan.SUBGROUP_SIZE

    /** [vocabulary] in list order (pack, lesson, position). The learner's own words are not grouped. */
    fun build(lessons: List<LessonSummary>, vocabulary: List<TrackedItem<Vocabulary>>): List<VocabularyGroup> {
        val titles = lessons.associate { it.id to it.title }
        val byLesson = LinkedHashMap<String, MutableList<TrackedItem<Vocabulary>>>()
        vocabulary.filter { it.item.lessonId != UserWords.LESSON_ID }
            .forEach { byLesson.getOrPut(it.item.lessonId) { mutableListOf() } += it }
        return byLesson.entries.flatMapIndexed { index, (lessonId, words) ->
            val parts = words.chunked(MAX_WORDS)
            parts.mapIndexed { p, chunk ->
                VocabularyGroup("$lessonId#${p + 1}", lessonId, index + 1, p + 1, parts.size, titles[lessonId].orEmpty(), chunk)
            }
        }
    }

    fun observe(contentRepository: ContentRepository): Flow<List<VocabularyGroup>> = combine(
        contentRepository.observeLessons(),
        contentRepository.observeVocabularyItems(ItemQuery()),
    ) { lessons, vocabulary -> build(lessons, vocabulary) }
}
