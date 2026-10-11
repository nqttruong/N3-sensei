package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueuePlannerTest {

    private fun v(id: String) = ItemRef(ItemType.VOCABULARY, id)
    private fun k(id: String) = ItemRef(ItemType.KANJI, id)

    @Test
    fun dueFirst_thenNew_withinRemainingLimits() {
        val due = listOf(v("d1"), k("d2"), v("d3"))
        val new = listOf(v("n1"), v("n2"), v("n3"))
        val queue = ReviewQueuePlanner.plan(due, new, DailyLimits(newPerDay = 2, reviewsPerDay = 2), TodayCounts(0, 0))
        assertEquals(listOf(v("d1"), k("d2"), v("n1"), v("n2")), queue)
    }

    @Test
    fun todaysAnswersReduceWhatIsLeft() {
        val limits = DailyLimits(newPerDay = 5, reviewsPerDay = 10)
        val today = TodayCounts(newAnswered = 4, reviewsAnswered = 9)
        assertEquals(1, ReviewQueuePlanner.remainingReviews(limits, today))
        assertEquals(1, ReviewQueuePlanner.remainingNew(limits, today))
        assertEquals(0, ReviewQueuePlanner.remainingNew(limits, TodayCounts(newAnswered = 7, reviewsAnswered = 0)))
    }

    @Test
    fun zeroNewPerDay_reviewsOnly_andDuplicatesAreDropped() {
        val queue = ReviewQueuePlanner.plan(listOf(v("a"), v("a")), listOf(v("a"), v("b")), DailyLimits(0, 100), TodayCounts(0, 0))
        assertEquals(listOf(v("a")), queue)
    }
}
