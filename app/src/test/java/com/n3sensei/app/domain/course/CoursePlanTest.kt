package com.n3sensei.app.domain.course

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePlanTest {

    @Test
    fun fullLessons_take24Kanji30Words7Grammar_inSourceOrder() {
        val lessons = CoursePlan.lessons((1..50).toList(), (1..65).toList(), (1..15).toList())
        assertEquals(3, lessons.size)
        assertEquals((1..24).toList(), lessons[0].first)
        assertEquals((1..30).toList(), lessons[0].second)
        assertEquals((1..7).toList(), lessons[0].third)
        assertEquals((25..48).toList(), lessons[1].first)
        assertEquals((31..60).toList(), lessons[1].second)
        assertEquals((8..14).toList(), lessons[1].third)
        // Last lesson: what is left of each list, nothing invented.
        assertEquals(listOf(49, 50), lessons[2].first)
        assertEquals((61..65).toList(), lessons[2].second)
        assertEquals(listOf(15), lessons[2].third)
    }

    @Test
    fun listsOfDifferentLengths_continueUntilEveryItemIsPlaced_withoutDuplicates() {
        val kanji = (1..30).toList()
        val words = (1..200).toList()
        val grammar = (1..3).toList()
        val lessons = CoursePlan.lessons(kanji, words, grammar)
        assertEquals(7, lessons.size) // 200 words / 30
        assertEquals(kanji, lessons.flatMap { it.first })
        assertEquals(words, lessons.flatMap { it.second })
        assertEquals(grammar, lessons.flatMap { it.third })
        assertTrue(lessons.drop(2).all { it.first.isEmpty() && it.third.isEmpty() })
    }

    @Test
    fun noContent_noLessons() {
        assertTrue(CoursePlan.lessons(emptyList<Int>(), emptyList<Int>(), emptyList<Int>()).isEmpty())
    }

    @Test
    fun subgroups_ofAtMost30_numberedPerSourceGroup() {
        fun labels(vararg sizes: Int) = CoursePlan.subgroupLabels(sizes.withIndex().flatMap { (g, n) -> List(n) { "g$g" } })
        assertEquals(emptyList<String>(), labels(0))
        assertEquals(listOf("1"), labels(1))
        assertEquals(List(30) { "1" }, labels(30))
        assertEquals(List(30) { "1.1" } + "1.2", labels(31))
        assertEquals(List(30) { "1.1" } + List(30) { "1.2" }, labels(60))
        assertEquals(List(30) { "1.1" } + List(30) { "1.2" } + "1.3", labels(61))
        val big = labels(125)
        assertEquals(listOf("1.1", "1.2", "1.3", "1.4", "1.5"), big.distinct())
        assertEquals(listOf(30, 30, 30, 30, 5), big.groupingBy { it }.eachCount().values.toList())
        // Groups are numbered in order of appearance; each is split on its own.
        assertEquals(List(30) { "1.1" } + List(5) { "1.2" } + List(12) { "2" }, labels(35, 12))
    }

    @Test
    fun builder_sectionsFollowSubgroups_acrossALessonBoundary_andSkipMyWords() {
        val words = (1..35).map { TestContent.vocabulary("a$it", "la", "語") } +
            (1..40).map { TestContent.vocabulary("b$it", "lb", "語") } +
            TestContent.vocabulary("mine", UserWords.LESSON_ID, "私")
        val lessons = CourseBuilder.build(
            listOf(TestContent.lesson("la", 1), TestContent.lesson("lb", 2)),
            emptyList(),
            words.map { TrackedItem(it, LearningStatus.NEW, false) },
            emptyList(),
        )
        assertEquals(3, lessons.size)
        assertEquals(listOf("1.1"), lessons[0].vocabulary.map { it.label })
        // Lesson 2 = the last 5 words of group 1 (1.2) and the first 25 of group 2 (2.1).
        assertEquals(listOf("1.2" to 5, "2.1" to 25), lessons[1].vocabulary.map { it.label to it.words.size })
        assertEquals(listOf("2.1" to 5, "2.2" to 10), lessons[2].vocabulary.map { it.label to it.words.size })
        assertTrue(lessons.flatMap { it.words }.none { it.item.id == "mine" })
        assertEquals(75, lessons.sumOf { it.words.size })
    }
}
