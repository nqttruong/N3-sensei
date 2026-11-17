package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ContrastSchedulerTest {

    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val scheduler = ContrastScheduler()
    private val t0 = Instant.parse("2026-11-17T01:00:00Z")
    private fun at(local: String, zone: ZoneId) = LocalDateTime.parse(local).atZone(zone).toInstant()
    private fun local(i: Instant, zone: ZoneId) = i.atZone(zone).toLocalDateTime().toString()
    private fun tally(c: Int, i: Int, s: Int) = BlockTally(c, i, s)
    private fun progress(step: Int, reviews: Int = 1, lapses: Int = 0) = ContrastProgress("g", step, t0, t0, reviews, lapses, t0, t0)

    @Test
    fun ratingPolicy() {
        assertEquals(ReviewRating.GOOD, scheduler.ratingOf(tally(3, 0, 0)))
        assertEquals(ReviewRating.HARD, scheduler.ratingOf(tally(2, 1, 0))) // 67 %
        assertEquals(ReviewRating.AGAIN, scheduler.ratingOf(tally(1, 2, 0))) // 33 %
        assertEquals(ReviewRating.AGAIN, scheduler.ratingOf(tally(0, 3, 0)))
        // All answered right but one skipped: not GOOD (something was skipped), accuracy 100 % → HARD.
        assertEquals(ReviewRating.HARD, scheduler.ratingOf(tally(2, 0, 1)))
        // Skipped ≥ half: no rating, schedule unchanged.
        assertNull(scheduler.ratingOf(tally(1, 0, 2)))
        assertNull(scheduler.ratingOf(tally(0, 0, 3)))
        assertNull(scheduler.ratingOf(tally(1, 1, 2)))
        assertThrows(IllegalArgumentException::class.java) { scheduler.ratingOf(tally(0, 0, 0)) }
    }

    @Test
    fun newGroup_firstBlock() {
        val good = scheduler.schedule("g", "s", null, tally(3, 0, 0), t0, vietnam, 1)!!
        assertEquals(1, good.progress.stepIndex) // 3 days
        assertEquals(t0.plusSeconds(3 * 86_400), good.progress.dueAt)
        assertNull(good.review.stepBefore)
        assertEquals(1, good.progress.reviewCount)
        val again = scheduler.schedule("g", "s", null, tally(0, 3, 0), t0, vietnam, 1)!!
        assertEquals(0, again.progress.stepIndex) // 1 day
        assertEquals(0, again.progress.lapseCount) // a new group cannot lapse
    }

    @Test
    fun steps_followTheContrastLadder_notTheLearnersOne() {
        assertEquals(listOf(1, 3, 7, 14, 30, 60), ContrastConfig.DEFAULT_LADDER.intervals.map { it.amount })
        assertTrue(ContrastConfig.DEFAULT_LADDER.intervals.all { it.unit == IntervalUnit.DAYS })
        val up = scheduler.schedule("g", "s", progress(2), tally(3, 0, 0), t0, vietnam, 1)!!
        assertEquals(3, up.progress.stepIndex) // 7 days → 14 days
        val stay = scheduler.schedule("g", "s", progress(2), tally(2, 1, 0), t0, vietnam, 1)!!
        assertEquals(2, stay.progress.stepIndex)
        val top = scheduler.schedule("g", "s", progress(5), tally(3, 0, 0), t0, vietnam, 1)!!
        assertEquals(5, top.progress.stepIndex) // capped at the last step
    }

    @Test
    fun lapse_onlyWhenAgainFromALongTermStep() {
        assertEquals(1, scheduler.schedule("g", "s", progress(2), tally(0, 3, 0), t0, vietnam, 1)!!.progress.lapseCount) // from 7 days
        assertEquals(0, scheduler.schedule("g", "s", progress(1), tally(0, 3, 0), t0, vietnam, 1)!!.progress.lapseCount) // from 3 days
        val back = scheduler.schedule("g", "s", progress(4, reviews = 5, lapses = 2), tally(0, 3, 0), t0, vietnam, 1)!!
        assertEquals(0, back.progress.stepIndex)
        assertEquals(3, back.progress.lapseCount)
        assertEquals(6, back.progress.reviewCount)
    }

    @Test
    fun aStepBeyondAShorterLadder_isClamped() {
        val short = ContrastScheduler(ContrastConfig(ladder = ReviewLadder.of(listOf(ReviewInterval(1, IntervalUnit.DAYS), ReviewInterval(2, IntervalUnit.DAYS)))))
        val pending = short.schedule("g", "s", progress(5), tally(2, 1, 0), t0, vietnam, 1)!!
        assertEquals(1, pending.review.stepBefore)
        assertEquals(1, pending.progress.stepIndex)
    }

    @Test
    fun tooManySkips_noReviewNoProgress() {
        assertNull(scheduler.schedule("g", "s", progress(2), tally(1, 0, 2), t0, vietnam, 1))
    }

    @Test
    fun calendarDays_acrossDaylightSaving_andMidnight() {
        // 1 day later at the same wall-clock time, even over the Berlin DST change (25-hour day).
        val start = at("2026-10-24T08:00", berlin)
        val pending = scheduler.schedule("g", "s", null, tally(0, 3, 0), start, berlin, 1)!!
        assertEquals("2026-10-25T08:00", local(pending.progress.dueAt, berlin))
        val lateNight = at("2026-11-17T23:59", vietnam)
        assertEquals("2026-11-20T23:59", local(scheduler.schedule("g", "s", null, tally(3, 0, 0), lateNight, vietnam, 1)!!.progress.dueAt, vietnam))
    }

    @Test
    fun dueBoundary_isInclusive() {
        val p = progress(0).copy(dueAt = t0)
        assertTrue(scheduler.isDue(p, t0))
        assertTrue(!scheduler.isDue(p, t0.minusMillis(1)))
    }

    @Test
    fun reviewKeepsTheContentVersionAndDerivedRating() {
        val pending = scheduler.schedule("g", "s", progress(1), tally(2, 1, 0), t0, vietnam, contentVersion = 7)!!
        assertEquals(7, pending.review.contentVersion)
        assertEquals(ReviewRating.HARD, pending.review.rating)
        assertEquals(3 to 2, pending.review.answered to pending.review.correct)
    }

    @Test
    fun experimentalConfig_rejectsNonsense() {
        assertThrows(IllegalArgumentException::class.java) { ContrastConfig(questionsPerBlock = 0) }
        assertThrows(IllegalArgumentException::class.java) { RatingPolicy(hardMinAccuracy = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { MasteryPolicy(minAnswered = 0) }
    }
}
