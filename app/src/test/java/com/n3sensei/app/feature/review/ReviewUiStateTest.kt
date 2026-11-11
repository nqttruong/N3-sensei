package com.n3sensei.app.feature.review

import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.TodayCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewUiStateTest {

    private val overview = ReviewOverview(
        dueByType = mapOf(ItemType.VOCABULARY to 30, ItemType.KANJI to 4),
        overdue = 6,
        today = TodayCounts(newAnswered = 15, reviewsAnswered = 180, newByType = mapOf(ItemType.VOCABULARY to 15)),
        newAvailable = 50,
        overdueByType = mapOf(ItemType.VOCABULARY to 5, ItemType.KANJI to 1),
        doneTodayByType = mapOf(ItemType.VOCABULARY to 120, ItemType.KANJI to 40, ItemType.GRAMMAR to 7),
        newByType = mapOf(ItemType.VOCABULARY to 40, ItemType.GRAMMAR to 10),
        forgottenByType = mapOf(ItemType.KANJI to 25),
    )

    @Test
    fun eachType_hasItsOwnNewAllowance_andSessionWithinTodaysLimits() {
        // 20 reviews left; vocabulary 20 − 15 = 5 new left, kanji 10 and grammar 10 untouched.
        val ui = ReviewUiState.of(overview, DailyLimits(newVocabulary = 20, newKanji = 10, newGrammar = 10, reviewsPerDay = 200))
        val byType = ui.types.associateBy { it.type }
        assertEquals(TypeReview(ItemType.VOCABULARY, due = 30, overdue = 5, doneToday = 120, newAvailable = 40, newToday = 15, newLimit = 20, sessionSize = 20 + 5), byType[ItemType.VOCABULARY])
        assertEquals(TypeReview(ItemType.KANJI, due = 4, overdue = 1, doneToday = 40, newAvailable = 0, newToday = 0, newLimit = 10, sessionSize = 4), byType[ItemType.KANJI])
        // Vocabulary nearly used up: grammar still gets its whole 10.
        assertEquals(TypeReview(ItemType.GRAMMAR, due = 0, overdue = 0, doneToday = 7, newAvailable = 10, newToday = 0, newLimit = 10, sessionSize = 10), byType[ItemType.GRAMMAR])
        // Mixed: 20 reviews + 5 words + 0 kanji (none left to learn) + 10 patterns.
        assertEquals(35, ui.sessionSize)
        assertEquals(15, ui.newToday)
        assertEquals(20, ui.dueOnlySize)
        assertEquals(20, ui.forgottenSize)
        assertEquals(listOf(ItemType.VOCABULARY, ItemType.KANJI, ItemType.GRAMMAR), ui.types.map { it.type })
    }

    @Test
    fun limitsUsedUp_nothingToStart() {
        val usedUp = TodayCounts(40, 200, mapOf(ItemType.VOCABULARY to 20, ItemType.KANJI to 10, ItemType.GRAMMAR to 10))
        val ui = ReviewUiState.of(overview.copy(today = usedUp), DailyLimits(newVocabulary = 20, newKanji = 10, newGrammar = 10, reviewsPerDay = 200))
        assertEquals(listOf(0, 0, 0), ui.types.map { it.sessionSize })
        assertEquals(0, ui.forgottenSize)
        assertEquals(true, ui.limitReached)
    }

    @Test
    fun oneTypeAtItsLimit_leavesTheOthersFull() {
        val grammarDone = TodayCounts(10, 0, mapOf(ItemType.GRAMMAR to 10))
        val ui = ReviewUiState.of(overview.copy(today = grammarDone), DailyLimits(newVocabulary = 20, newKanji = 10, newGrammar = 10))
        val byType = ui.types.associateBy { it.type }
        assertEquals(30 + 0, byType.getValue(ItemType.VOCABULARY).due)
        assertEquals(30.coerceAtMost(200) + 20, byType.getValue(ItemType.VOCABULARY).sessionSize) // all 20 new words
        assertEquals(0 + 4, byType.getValue(ItemType.KANJI).sessionSize)
        assertEquals(0, byType.getValue(ItemType.GRAMMAR).sessionSize)
    }
}
