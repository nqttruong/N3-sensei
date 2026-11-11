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
        today = TodayCounts(newAnswered = 15, reviewsAnswered = 180),
        newAvailable = 50,
        overdueByType = mapOf(ItemType.VOCABULARY to 5, ItemType.KANJI to 1),
        doneTodayByType = mapOf(ItemType.VOCABULARY to 120, ItemType.KANJI to 40, ItemType.GRAMMAR to 7),
        newByType = mapOf(ItemType.VOCABULARY to 40, ItemType.GRAMMAR to 10),
        forgottenByType = mapOf(ItemType.KANJI to 25),
    )

    @Test
    fun eachType_hasItsOwnCounts_andSessionWithinTodaysLimits() {
        // 20 reviews and 5 new items left today.
        val ui = ReviewUiState.of(overview, DailyLimits(newPerDay = 20, reviewsPerDay = 200))
        val byType = ui.types.associateBy { it.type }
        assertEquals(TypeReview(ItemType.VOCABULARY, due = 30, overdue = 5, doneToday = 120, newAvailable = 40, sessionSize = 20 + 5), byType[ItemType.VOCABULARY])
        assertEquals(TypeReview(ItemType.KANJI, due = 4, overdue = 1, doneToday = 40, newAvailable = 0, sessionSize = 4), byType[ItemType.KANJI])
        assertEquals(TypeReview(ItemType.GRAMMAR, due = 0, overdue = 0, doneToday = 7, newAvailable = 10, sessionSize = 5), byType[ItemType.GRAMMAR])
        // Mixed: all due (34) and new (50), within the same limits.
        assertEquals(25, ui.sessionSize)
        assertEquals(20, ui.dueOnlySize)
        assertEquals(20, ui.forgottenSize)
        assertEquals(listOf(ItemType.VOCABULARY, ItemType.KANJI, ItemType.GRAMMAR), ui.types.map { it.type })
    }

    @Test
    fun limitsUsedUp_nothingToStart() {
        val ui = ReviewUiState.of(overview.copy(today = TodayCounts(20, 200)), DailyLimits(newPerDay = 20, reviewsPerDay = 200))
        assertEquals(listOf(0, 0, 0), ui.types.map { it.sessionSize })
        assertEquals(0, ui.forgottenSize)
        assertEquals(true, ui.limitReached)
    }
}
