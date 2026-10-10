package com.n3sensei.app.domain.practice

import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTest {

    private val items = listOf(
        PracticeItem.VocabularyItem(TestContent.vocabulary("v1", "l", "経験")),
        PracticeItem.KanjiItem(TestContent.kanji("k1", "l", "経")),
        PracticeItem.GrammarItem(TestContent.grammar("g1", "l", "〜ばかり")),
    )

    @Test
    fun ratingBeforeReveal_isIgnored() {
        val session = PracticeSession(items)
        assertSame(session, session.rate(ReviewRating.GOOD))
    }

    @Test
    fun revealThenRate_advancesAndHidesAnswer() {
        val next = PracticeSession(items).revealAnswer().rate(ReviewRating.HARD)
        assertEquals(1, next.currentIndex)
        assertFalse(next.isAnswerRevealed)
        assertEquals(ReviewRating.HARD, next.ratings[items[0].ref])
    }

    @Test
    fun finishingAllCards_producesTalliedResult() {
        var session = PracticeSession(items)
        listOf(ReviewRating.AGAIN, ReviewRating.GOOD, ReviewRating.GOOD).forEach { session = session.revealAnswer().rate(it) }

        assertTrue(session.isFinished)
        assertNull(session.current)
        assertEquals(PracticeResult(total = 3, again = 1, hard = 0, good = 2, easy = 0, durationSeconds = 42), session.result(42))
        assertEquals(3, session.result(0).rated)
    }

    @Test
    fun finishedSession_ignoresFurtherInput() {
        var session = PracticeSession(items.take(1)).revealAnswer().rate(ReviewRating.EASY)
        assertSame(session, session.revealAnswer())
        assertSame(session, session.rate(ReviewRating.AGAIN))
    }

    @Test
    fun negativeDuration_isClampedToZero() {
        assertEquals(0, PracticeSession(emptyList()).result(-5).durationSeconds)
    }
}
