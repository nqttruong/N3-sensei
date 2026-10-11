package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRating.AGAIN
import com.n3sensei.app.domain.model.ReviewRating.EASY
import com.n3sensei.app.domain.model.ReviewRating.GOOD
import com.n3sensei.app.domain.model.ReviewRating.HARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SpacedRepetitionSchedulerTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val item = ItemRef(ItemType.VOCABULARY, "v1")
    private val scheduler = SpacedRepetitionScheduler(ReviewLadder.DEFAULT)

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private fun local(instant: Instant?): String = instant!!.atZone(zone).toLocalDateTime().toString()

    private fun answer(current: LearningProgress?, rating: ReviewRating, time: String, s: SpacedRepetitionScheduler = scheduler) =
        s.schedule(item, current, rating, at(time), zone, requestId = "$time-$rating")

    // --- New items: the four ratings ---

    @Test
    fun newItem_againAndHard_startAtTwentyMinutes() {
        listOf(AGAIN, HARD).forEach { rating ->
            val r = answer(null, rating, "2026-10-11T08:00")
            assertEquals(0, r.progress.stepIndex)
            assertEquals("2026-10-11T08:20", local(r.progress.dueAt))
            assertEquals(ProgressState.LEARNING, r.progress.state)
            assertEquals(0, r.progress.lapseCount) // forgetting something never learned is not a lapse
        }
    }

    @Test
    fun newItem_goodGoesToOneHour_easyToOneDay() {
        val good = answer(null, GOOD, "2026-10-11T08:00").progress
        assertEquals(1, good.stepIndex)
        assertEquals("2026-10-11T09:00", local(good.dueAt))
        assertEquals(ProgressState.LEARNING, good.state)

        val easy = answer(null, EASY, "2026-10-11T08:00").progress
        assertEquals(2, easy.stepIndex)
        assertEquals("2026-10-12T08:00", local(easy.dueAt))
        assertEquals(ProgressState.REVIEW, easy.state)
    }

    @Test
    fun newItem_recordIsMarkedAsNew_andProgressStartsCounting() {
        val r = answer(null, GOOD, "2026-10-11T08:00")
        assertTrue(r.record.wasNew)
        assertEquals(0L, r.record.intervalBeforeMinutes)
        assertEquals(60L, r.record.intervalAfterMinutes)
        assertEquals(1, r.progress.reviewCount)
        assertEquals(at("2026-10-11T08:00"), r.progress.createdAt)
        assertEquals(at("2026-10-11T08:00"), r.progress.lastReviewedAt)
        assertEquals("2026-10-11T08:00-GOOD", r.record.requestId)
    }

    // --- Items already reviewed: the four ratings from step 3 (2 days) ---

    private val onStep3 = LearningProgress(
        item = item, state = ProgressState.REVIEW, stepIndex = 3, intervalMinutes = 2 * 24 * 60,
        dueAt = at("2026-10-11T08:00"), lastReviewedAt = at("2026-10-09T08:00"),
        reviewCount = 4, lapseCount = 0, createdAt = at("2026-10-01T08:00"), updatedAt = at("2026-10-09T08:00"),
    )

    @Test
    fun reviewedItem_eachRating() {
        val again = answer(onStep3, AGAIN, "2026-10-11T08:30").progress
        assertEquals(0, again.stepIndex)
        assertEquals("2026-10-11T08:50", local(again.dueAt))
        assertEquals(ProgressState.LEARNING, again.state)
        assertEquals(1, again.lapseCount)

        val hard = answer(onStep3, HARD, "2026-10-11T08:30").progress
        assertEquals(3, hard.stepIndex)
        assertEquals("2026-10-13T08:30", local(hard.dueAt))

        val good = answer(onStep3, GOOD, "2026-10-11T08:30").progress
        assertEquals(4, good.stepIndex)
        assertEquals("2026-10-18T08:30", local(good.dueAt))

        val easy = answer(onStep3, EASY, "2026-10-11T08:30").progress
        assertEquals(5, easy.stepIndex)
        assertEquals("2026-10-25T08:30", local(easy.dueAt))

        listOf(hard, good, easy).forEach {
            assertEquals(0, it.lapseCount)
            assertEquals(5, it.reviewCount)
            assertEquals(onStep3.createdAt, it.createdAt)
        }
    }

    @Test
    fun reviewedItem_recordKeepsBeforeAndAfter() {
        val r = answer(onStep3, GOOD, "2026-10-11T08:30")
        assertFalse(r.record.wasNew)
        assertEquals(2 * 24 * 60L, r.record.intervalBeforeMinutes)
        assertEquals(7 * 24 * 60L, r.record.intervalAfterMinutes)
        assertEquals(r.progress.dueAt, r.record.dueAtAfter)
    }

    @Test
    fun againWhileStillLearning_isNotALapse() {
        val learning = answer(null, GOOD, "2026-10-11T08:00").progress
        assertEquals(0, answer(learning, AGAIN, "2026-10-11T09:00").progress.lapseCount)
    }

    // --- Consecutive reviews: full timeline (also documented in docs/SRS.md) ---

    @Test
    fun consecutiveReviews_followTheLadder() {
        var p: LearningProgress? = null
        val timeline = listOf(
            Triple(GOOD, "2026-10-11T08:00", "2026-10-11T09:00"),
            Triple(GOOD, "2026-10-11T09:05", "2026-10-12T09:05"),
            Triple(GOOD, "2026-10-12T09:30", "2026-10-14T09:30"),
            Triple(HARD, "2026-10-14T20:00", "2026-10-16T20:00"),
            Triple(EASY, "2026-10-16T21:00", "2026-10-30T21:00"),
            Triple(AGAIN, "2026-10-30T22:00", "2026-10-30T22:20"),
            Triple(GOOD, "2026-10-30T22:25", "2026-10-30T23:25"),
            Triple(GOOD, "2026-10-30T23:30", "2026-10-31T23:30"),
            Triple(EASY, "2026-11-01T07:00", "2026-11-08T07:00"),
            Triple(GOOD, "2026-11-08T07:10", "2026-11-22T07:10"),
            Triple(GOOD, "2026-11-22T07:20", "2026-12-22T07:20"),
            Triple(GOOD, "2026-12-22T07:30", "2027-01-22T07:30"),
        )
        val steps = mutableListOf<Int>()
        timeline.forEach { (rating, time, expectedDue) ->
            p = answer(p, rating, time).progress
            assertEquals("after $rating at $time", expectedDue, local(p!!.dueAt))
            steps += p!!.stepIndex
        }
        assertEquals(listOf(1, 2, 3, 3, 5, 0, 1, 2, 4, 5, 6, 6), steps)
        assertEquals(ProgressState.MASTERED, p!!.state)
        assertEquals(12, p!!.reviewCount)
        assertEquals(1, p!!.lapseCount)
    }

    @Test
    fun topStep_isCapped_andMastered() {
        val top = onStep3.copy(stepIndex = 6, state = ProgressState.MASTERED)
        val r = answer(top, EASY, "2027-01-31T10:00").progress
        assertEquals(6, r.stepIndex)
        assertEquals(ProgressState.MASTERED, r.state)
        assertEquals("2027-02-28T10:00", local(r.dueAt)) // calendar month, clamped
        assertEquals(1, answer(top, AGAIN, "2027-01-31T10:00").progress.lapseCount)
    }

    // --- Changing the configured intervals ---

    @Test
    fun changedLadder_appliesFromTheNextAnswer_withStoredStep() {
        val custom = SpacedRepetitionScheduler(
            ReviewLadder.of(listOf("10m", "4h", "1d", "3d", "10d", "1mo", "2mo").map { ReviewInterval.parse(it)!! }),
        )
        // The item keeps its step (3); GOOD moves it to step 4 of the *new* ladder.
        val r = answer(onStep3, GOOD, "2026-10-11T08:30", custom).progress
        assertEquals(4, r.stepIndex)
        assertEquals("2026-10-21T08:30", local(r.dueAt))
    }

    @Test
    fun shortenedLadder_clampsTheStoredStep() {
        val short = SpacedRepetitionScheduler(ReviewLadder.of(listOf("20m", "1d", "1w").map { ReviewInterval.parse(it)!! }))
        val stepFive = onStep3.copy(stepIndex = 5)
        val hard = answer(stepFive, HARD, "2026-10-11T08:30", short).progress
        assertEquals(2, hard.stepIndex)
        assertEquals("2026-10-18T08:30", local(hard.dueAt))
        assertEquals(ProgressState.MASTERED, hard.state)
    }

    @Test
    fun preview_matchesSchedule() {
        ReviewRating.entries.forEach { rating ->
            val previewed = scheduler.preview(onStep3, rating)
            val scheduled = answer(onStep3, rating, "2026-10-11T08:30").progress
            assertEquals(ReviewLadder.DEFAULT[scheduled.stepIndex], previewed)
        }
        assertEquals(ReviewInterval(20, IntervalUnit.MINUTES), scheduler.preview(null, AGAIN))
    }

    @Test
    fun answerTimeEarlierThanLastReview_stillSchedulesFromAnswerTime() {
        // System clock moved back: due time is computed from the (earlier) answer time.
        val r = answer(onStep3, GOOD, "2026-10-01T08:00").progress
        assertEquals("2026-10-08T08:00", local(r.dueAt))
        assertEquals(7 * 24 * 60L, r.intervalMinutes)
    }

    @Test
    fun progressOfAnotherItem_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            scheduler.schedule(ItemRef(ItemType.KANJI, "k1"), onStep3, GOOD, at("2026-10-11T08:00"), zone, "x")
        }
    }
}
