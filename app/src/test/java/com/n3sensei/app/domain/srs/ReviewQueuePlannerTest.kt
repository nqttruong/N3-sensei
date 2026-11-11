package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ItemType.GRAMMAR
import com.n3sensei.app.domain.model.ItemType.KANJI
import com.n3sensei.app.domain.model.ItemType.VOCABULARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReviewQueuePlannerTest {

    private fun v(id: String) = ItemRef(VOCABULARY, id)
    private fun k(id: String) = ItemRef(KANJI, id)
    private fun g(id: String) = ItemRef(GRAMMAR, id)
    private fun many(type: ItemType, n: Int) = (1..n).map { ItemRef(type, "${type.name.first()}$it") }
    private fun today(vocabulary: Int = 0, kanji: Int = 0, grammar: Int = 0, reviews: Int = 0) =
        TodayCounts(vocabulary + kanji + grammar, reviews, mapOf(VOCABULARY to vocabulary, KANJI to kanji, GRAMMAR to grammar))

    private val pool = many(VOCABULARY, 50) + many(KANJI, 50) + many(GRAMMAR, 50)
    private fun countByType(queue: List<ItemRef>) = ItemType.entries.associateWith { t -> queue.count { it.type == t } }

    @Test
    fun theDefaults_are20Vocabulary_10Kanji_10Grammar() {
        val limits = DailyLimits()
        assertEquals(listOf(20, 10, 10), ItemType.entries.map(limits::newPerDay))
        // All of them on the same day: 40 new items, no shared cap.
        assertEquals(mapOf(VOCABULARY to 20, KANJI to 10, GRAMMAR to 10), countByType(ReviewQueuePlanner.plan(emptyList(), pool, limits, today())))
        assertEquals(40, limits.newPerDayTotal)
    }

    @Test
    fun customLimits_areEachTypesOwn_withNoLimitOnTheirSum() {
        val limits = DailyLimits(newVocabulary = 100, newKanji = 100, newGrammar = 100)
        assertEquals(mapOf(VOCABULARY to 50, KANJI to 50, GRAMMAR to 50), countByType(ReviewQueuePlanner.plan(emptyList(), pool, limits, today())))
        assertEquals(mapOf(VOCABULARY to 5, KANJI to 30, GRAMMAR to 1), countByType(ReviewQueuePlanner.plan(emptyList(), pool, DailyLimits(5, 30, 1), today())))
    }

    @Test
    fun zero_turnsOneTypeOff_theOthersGoOn() {
        assertEquals(mapOf(VOCABULARY to 0, KANJI to 10, GRAMMAR to 10), countByType(ReviewQueuePlanner.plan(emptyList(), pool, DailyLimits(newVocabulary = 0), today())))
        assertEquals(mapOf(VOCABULARY to 20, KANJI to 0, GRAMMAR to 10), countByType(ReviewQueuePlanner.plan(emptyList(), pool, DailyLimits(newKanji = 0), today())))
        assertEquals(mapOf(VOCABULARY to 20, KANJI to 10, GRAMMAR to 0), countByType(ReviewQueuePlanner.plan(emptyList(), pool, DailyLimits(newGrammar = 0), today())))
    }

    @Test
    fun whatEachTypeIntroducedToday_reducesThatTypeOnly() {
        // The example: 15 / 3 / 10 already introduced with 20 / 10 / 10.
        val t = today(vocabulary = 15, kanji = 3, grammar = 10)
        val limits = DailyLimits()
        assertEquals(listOf(5, 7, 0), ItemType.entries.map { ReviewQueuePlanner.remainingNew(limits, t, it) })
        assertEquals(mapOf(VOCABULARY to 5, KANJI to 7, GRAMMAR to 0), countByType(ReviewQueuePlanner.plan(emptyList(), pool, limits, t)))
        // More than the cap (the cap was lowered later): nothing left, never negative.
        assertEquals(0, ReviewQueuePlanner.remainingNew(DailyLimits(newVocabulary = 5), t, VOCABULARY))
    }

    @Test
    fun aTypeWithoutNewContent_leavesItsAllowanceUnused_nothingMovesToTheOthers() {
        val few = listOf(k("k1"), k("k2")) + many(VOCABULARY, 50) + many(GRAMMAR, 50)
        assertEquals(mapOf(VOCABULARY to 20, KANJI to 2, GRAMMAR to 10), countByType(ReviewQueuePlanner.plan(emptyList(), few, DailyLimits(), today())))
    }

    @Test
    fun dueItemsFirst_inTheirOrder_andTheyUseNoNewAllowance() {
        val due = listOf(k("d1"), v("d2"), g("d3"))
        val queue = ReviewQueuePlanner.plan(due, pool, DailyLimits(newVocabulary = 1, newKanji = 1, newGrammar = 1, reviewsPerDay = 10), today())
        assertEquals(due, queue.take(3))
        assertEquals(mapOf(VOCABULARY to 2, KANJI to 2, GRAMMAR to 2), countByType(queue))
        // Reviews keep their own (shared) cap.
        assertEquals(listOf(k("d1")), ReviewQueuePlanner.plan(due, emptyList(), DailyLimits(reviewsPerDay = 1), today()))
        assertEquals(0, ReviewQueuePlanner.remainingReviews(DailyLimits(reviewsPerDay = 10), today(reviews = 12)))
    }

    @Test
    fun duplicates_andItemsAlreadyDue_areNotCountedAsNew() {
        val queue = ReviewQueuePlanner.plan(listOf(v("a")), listOf(v("a"), v("a"), v("b")), DailyLimits(newVocabulary = 5), today())
        assertEquals(listOf(v("a"), v("b")), queue)
    }

    @Test
    fun eachCap_is0to100_onItsOwn() {
        DailyLimits(newVocabulary = 100, newKanji = 100, newGrammar = 100) // 300 in all: fine
        assertThrows(IllegalArgumentException::class.java) { DailyLimits(newKanji = 101) }
        assertThrows(IllegalArgumentException::class.java) { DailyLimits(newGrammar = -1) }
    }
}
