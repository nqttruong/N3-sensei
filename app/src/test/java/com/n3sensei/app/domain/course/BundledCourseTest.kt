package com.n3sensei.app.domain.course

import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackJsonParser
import com.n3sensei.app.data.importing.ContentPackValidator
import com.n3sensei.app.data.repository.toDomain
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.TrackedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The "Bài học" course over the packs bundled with the app, in the order the lists of Học →
 * Kanji / Từ vựng / Ngữ pháp use (pack id, lesson number, position).
 */
class BundledCourseTest {

    private val bundles = AndroidContentImportRepository.BUNDLED_PACK_ASSETS.map { path ->
        val text = File("src/main/assets/$path").readText()
        ContentPackValidator(importedAt = 0).validate((ContentPackJsonParser.parse(text) as ContentPackJsonParser.Result.Parsed).dto).bundle!!
    }.sortedBy { it.pack.id }

    private fun <T> tracked(item: T) = TrackedItem(item, LearningStatus.NEW, false)

    private val lessons = bundles.flatMap { b ->
        b.lessons.sortedBy { it.number }.map { l -> LessonSummary(l.id, l.packId, l.number, l.title, l.jlptLevel, l.description, 0, 0, 0) }
    }
    private val order = lessons.withIndex().associate { (i, l) -> l.id to i }
    private val kanji = bundles.flatMap { it.kanji }.sortedWith(compareBy({ order.getValue(it.lessonId) }, { it.sortOrder })).map { tracked(it.toDomain()) }
    private val words = bundles.flatMap { it.vocabulary }.sortedWith(compareBy({ order.getValue(it.lessonId) }, { it.sortOrder })).map { tracked(it.toDomain()) }
    private val grammar = bundles.flatMap { it.grammar }.sortedWith(compareBy({ order.getValue(it.lessonId) }, { it.sortOrder })).map { tracked(it.toDomain()) }
    private val course = CourseBuilder.build(lessons, kanji, words, grammar)
    private val levelOf = lessons.associate { it.id to it.jlptLevel }

    /** Học → Bài học is the N3 course: Kanji N2 has its own lessons in Học → Kanji. */
    private val courseKanji = kanji.filter { levelOf.getValue(it.item.lessonId) == "N3" }

    @Test
    fun everyItemIsInExactlyOneLesson_inListOrder() {
        assertEquals(courseKanji, course.flatMap { it.kanji })
        assertEquals(words, course.flatMap { it.words })
        assertEquals(grammar, course.flatMap { it.grammar })
        // 393 + 27 kanji, 880 + 30 words, 174 + 7 grammar points: words need the most lessons.
        assertEquals(420, courseKanji.size)
        // The 448 N2 kanji are in no lesson of the course, which stays as it was.
        assertEquals(448, kanji.size - courseKanji.size)
        assertTrue(course.flatMap { it.kanji }.none { levelOf.getValue(it.item.lessonId) == "N2" })
        assertEquals(910, words.size)
        assertEquals(181, grammar.size)
        assertEquals(31, course.size)
        assertEquals((1..31).toList(), course.map { it.number })
    }

    @Test
    fun lessonSizes_24_30_7_untilAListRunsOut() {
        assertEquals(List(17) { 24 } + 12 + List(13) { 0 }, course.map { it.kanji.size })
        assertEquals(List(30) { 30 } + 10, course.map { it.words.size })
        assertEquals(List(25) { 7 } + 6 + List(5) { 0 }, course.map { it.grammar.size })
    }

    @Test
    fun lessonOne_startsEveryList() {
        val first = course.first()
        assertEquals("意", first.kanji.first().item.character)
        assertEquals("n3g-g001", first.grammar.first().item.id)
        assertEquals("mimikara-n3-v001", first.words.first().item.id)
        assertEquals(listOf("1.1"), first.vocabulary.map { it.label })
    }

    @Test
    fun mimikaraGroups_splitInto30WordSubgroups() {
        // Group 1 (名詞, 120 words): 1.1–1.4 in lessons 1–4; group 3 (78 words) ends inside a lesson.
        assertEquals(listOf("1.1", "1.2", "1.3", "1.4"), course.take(4).flatMap { it.vocabulary.map { s -> s.label } })
        val sections = course.flatMap { it.vocabulary }
        assertTrue(sections.all { it.words.size <= 30 })
        val labels = sections.map { it.label }
        assertEquals(labels.distinct().size, labels.size - sections.zipWithNext().count { (a, b) -> a.label == b.label })
    }

    @Test
    fun vocabularyGroups_overTheBundledPacks_splitEachLessonInto30s_withTheCoursesLabels() {
        val groups = VocabularyGroups.build(lessons, words)
        assertEquals(words, groups.flatMap { it.words })
        assertTrue(groups.all { it.words.size in 1..30 })
        val perLesson = words.groupBy { it.item.lessonId }.mapValues { (_, w) -> (w.size + 29) / 30 }
        assertEquals(perLesson.values.sum(), groups.size)
        // Mimikara: lessons of 120, 100, 78, 112, 100, 40, 40, 45, 80, 80, 50, 35 words.
        assertEquals(
            listOf(4, 4, 3, 4, 4, 2, 2, 2, 3, 3, 2, 2),
            groups.filter { it.lessonId.startsWith("mimikara") }.groupBy { it.lessonId }.values.map { it.size },
        )
        // The same "n.k" a word has in the sections of Học → Bài học.
        val courseLabels = course.flatMap { l -> l.vocabulary.flatMap { s -> s.words.map { it.item.id to s.label } } }.toMap()
        groups.forEach { g -> g.words.forEach { assertEquals(courseLabels.getValue(it.item.id), g.label) } }
    }

    @Test
    fun everyBundledItemHasALocation_andWordsInTwoPacksPointToEachOther() {
        val locations = ItemLocations.build(lessons, kanji, words, grammar)
        val refs = kanji.map { com.n3sensei.app.domain.model.ItemRef(com.n3sensei.app.domain.model.ItemType.KANJI, it.item.id) } +
            words.map { com.n3sensei.app.domain.model.ItemRef(com.n3sensei.app.domain.model.ItemType.VOCABULARY, it.item.id) } +
            grammar.map { com.n3sensei.app.domain.model.ItemRef(com.n3sensei.app.domain.model.ItemType.GRAMMAR, it.item.id) }
        assertTrue(refs.none { locations.of(it).kind == ItemLocation.Kind.NONE })
        // Every N3 item is in a lesson of Học → Bài học; an N2 kanji is in its own N2 lesson.
        val n2 = kanji.filter { levelOf.getValue(it.item.lessonId) == "N2" }
            .map { com.n3sensei.app.domain.model.ItemRef(com.n3sensei.app.domain.model.ItemType.KANJI, it.item.id) }.toSet()
        assertTrue(refs.filter { it !in n2 }.all { locations.of(it).courseLesson != null })
        assertTrue(n2.all { locations.of(it).courseLesson == null && locations.of(it).levelLesson?.first == "N2" })
        assertEquals("N2" to 1, locations.of(com.n3sensei.app.domain.model.ItemRef(com.n3sensei.app.domain.model.ItemType.KANJI, "n2k-k04e95")).levelLesson)
        val shared = words.filter { locations.sameWord(it.item.id).isNotEmpty() }
        assertTrue(shared.isNotEmpty())
        shared.forEach { w -> locations.sameWord(w.item.id).forEach { other -> assertTrue(locations.sameWord(other.vocabularyId).any { it.vocabularyId == w.item.id }) } }
    }
}
