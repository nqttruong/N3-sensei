package com.n3sensei.app.domain.course

import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemLocationsTest {

    private fun <T> t(item: T) = TrackedItem(item, LearningStatus.NEW, false)

    private val lessons = listOf(
        TestContent.lesson("a", 1, vocabulary = 31).copy(title = "Nguồn A"),
        TestContent.lesson("b", 2, vocabulary = 2).copy(title = "Nguồn B"),
        TestContent.lesson("k", 3, kanji = 25),
        TestContent.lesson("g", 4, grammar = 8),
    )
    private val words = (1..31).map { t(TestContent.vocabulary("a$it", "a", "語$it", "ご$it")) } +
        // "経験" is in lesson b and among the learner's words: two records of one word.
        t(TestContent.vocabulary("b1", "b", "経験", "けいけん")) +
        t(TestContent.vocabulary("b2", "b", "増える", "ふえる")) +
        t(TestContent.vocabulary(UserWords.ID_PREFIX + "1", UserWords.LESSON_ID, "経験", "けいけん")) +
        // In the learner's lesson but not made by "Thêm từ của tôi".
        t(TestContent.vocabulary("stray", UserWords.LESSON_ID, "迷子", "まいご"))
    private val kanji = (1..25).map { t(TestContent.kanji("k$it", "k", "字")) }
    private val grammar = (1..8).map { t(TestContent.grammar("g$it", "g", "〜$it")) }
    private val locations = ItemLocations.build(lessons, kanji, words, grammar)

    private fun word(id: String) = locations.of(ItemRef(ItemType.VOCABULARY, id))

    @Test
    fun wordsHaveTheirGroup_theirCourseLesson_andTheirSourceTitle() {
        assertEquals(ItemLocation(ItemLocation.Kind.VOCABULARY, courseLesson = 1, vocabularyGroup = "1.1", sourceTitle = "Nguồn A"), word("a1"))
        // Word 31 of lesson a: the second group of a, and the second lesson of Học → Bài học.
        assertEquals(ItemLocation(ItemLocation.Kind.VOCABULARY, courseLesson = 2, vocabularyGroup = "1.2", sourceTitle = "Nguồn A"), word("a31"))
        assertEquals("2", word("b1").vocabularyGroup)
    }

    @Test
    fun kanjiAndGrammarHaveTheirCourseLesson() {
        assertEquals(ItemLocation(ItemLocation.Kind.KANJI, courseLesson = 1, sourceTitle = "Bài 3"), locations.of(ItemRef(ItemType.KANJI, "k24")))
        assertEquals(2, locations.of(ItemRef(ItemType.KANJI, "k25")).courseLesson)
        assertEquals(1, locations.of(ItemRef(ItemType.GRAMMAR, "g7")).courseLesson)
        assertEquals(2, locations.of(ItemRef(ItemType.GRAMMAR, "g8")).courseLesson)
    }

    @Test
    fun theLearnersWords_areMyWords_andNothingIsGuessedForTheRest() {
        assertEquals(ItemLocation.MY_WORDS, word(UserWords.ID_PREFIX + "1"))
        assertEquals(ItemLocation.NONE, word("stray"))
        assertEquals(ItemLocation.NONE, word("not-in-any-list"))
        assertEquals(ItemLocation.NONE, locations.of(ItemRef(ItemType.KANJI, "gone")))
    }

    @Test
    fun aWordInSeveralPlaces_listsTheOtherPlaces() {
        assertEquals(listOf(SameWord(UserWords.ID_PREFIX + "1", ItemLocation.MY_WORDS)), locations.sameWord("b1"))
        assertEquals(listOf("b1"), locations.sameWord(UserWords.ID_PREFIX + "1").map { it.vocabularyId })
        // Same spelling, other reading or other spelling: not the same word.
        assertEquals(emptyList<SameWord>(), locations.sameWord("b2"))
        assertEquals(emptyList<SameWord>(), locations.sameWord("unknown"))
    }
}
