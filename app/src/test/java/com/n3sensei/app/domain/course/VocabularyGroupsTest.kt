package com.n3sensei.app.domain.course

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyGroupsTest {

    /** Lessons a…g with 0, 1, 30, 31, 60, 61 and 125 words, in this order. */
    private val sizes = listOf("a" to 0, "b" to 1, "c" to 30, "d" to 31, "e" to 60, "f" to 61, "g" to 125)
    private val lessons = sizes.mapIndexed { i, (id, n) -> TestContent.lesson(id, i + 1, vocabulary = n).copy(title = "Nguồn $id") }
    private val words = sizes.flatMap { (lesson, n) ->
        (1..n).map { TrackedItem(TestContent.vocabulary("$lesson$it", lesson, "語"), LearningStatus.NEW, false) }
    }
    private val groups = VocabularyGroups.build(lessons, words)

    @Test
    fun everyWordOnce_inOrder_atMost30PerGroup() {
        assertEquals(words, groups.flatMap { it.words })
        assertEquals(words.size, groups.flatMap { g -> g.words.map { it.item.id } }.toSet().size)
        assertTrue(groups.all { it.words.size in 1..30 })
    }

    @Test
    fun groupsOf30OrFewerStayWhole_largerOnesSplitInto30sThenTheRest() {
        // A lesson without words has no group, and is not counted in the numbering.
        assertEquals(
            listOf(
                "1" to 1, "2" to 30,
                "3.1" to 30, "3.2" to 1,
                "4.1" to 30, "4.2" to 30,
                "5.1" to 30, "5.2" to 30, "5.3" to 1,
                "6.1" to 30, "6.2" to 30, "6.3" to 30, "6.4" to 30, "6.5" to 5,
            ),
            groups.map { it.label to it.words.size },
        )
        // Never merged: every group holds words of one lesson only, and keeps that lesson's title.
        assertTrue(groups.all { g -> g.words.all { it.item.lessonId == g.lessonId } })
        assertEquals(listOf("Nguồn g"), groups.filter { it.label.startsWith("6.") }.map { it.sourceTitle }.distinct())
        assertEquals(listOf("g31", "g60"), groups.single { it.key == "g#2" }.words.let { listOf(it.first().item.id, it.last().item.id) })
    }

    @Test
    fun keysAreStable_andTheLearnersOwnWordsAreNotGrouped() {
        val mine = TrackedItem(TestContent.vocabulary(UserWords.ID_PREFIX + "1", UserWords.LESSON_ID, "私"), LearningStatus.NEW, false)
        val again = VocabularyGroups.build(lessons, words + mine)
        assertEquals(groups.map { it.key }, again.map { it.key })
        assertEquals(groups, again)
    }

    @Test
    fun progressComesFromEachWordsOwnStatus() {
        val studied = words.map { if (it.item.id in setOf("d31", "e1", "e2")) it.copy(status = LearningStatus.LEARNING) else it }
        val byLabel = VocabularyGroups.build(lessons, studied).associateBy { it.label }
        assertEquals(1, byLabel.getValue("3.2").started())
        assertEquals(0, byLabel.getValue("3.1").started())
        assertEquals(2, byLabel.getValue("4.1").started())
    }

    @Test
    fun noWords_noGroups() {
        assertEquals(emptyList<VocabularyGroup>(), VocabularyGroups.build(lessons, emptyList()))
    }
}
