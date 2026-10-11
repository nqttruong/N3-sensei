package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.srs.ReviewLadder.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLadderTest {

    private fun i(text: String) = ReviewInterval.parse(text)!!

    @Test
    fun defaultLadder_isTheRequiredSevenSteps() {
        assertEquals("20m,1h,1d,2d,1w,2w,1mo", ReviewLadder.DEFAULT.encode())
        assertEquals(2, ReviewLadder.DEFAULT.reviewStartIndex) // first step of a day or more
        assertTrue(ReviewLadder.validate(ReviewLadder.DEFAULT.intervals).isEmpty())
    }

    @Test
    fun validation_reportsEmptyOrderAndLength() {
        assertEquals(listOf(Problem.EMPTY), ReviewLadder.validate(emptyList()).map { it.problem })
        assertEquals(
            listOf(ReviewLadder.Validation(Problem.NOT_INCREASING, 2)),
            ReviewLadder.validate(listOf(i("1h"), i("1d"), i("24h"))),
        )
        assertEquals(listOf(Problem.TOO_LONG), ReviewLadder.validate(listOf(i("1d"), i("13mo"))).map { it.problem })
        assertEquals(listOf(Problem.TOO_MANY_STEPS), ReviewLadder.validate((1..13).map { i("${it}d") }).map { it.problem })
    }

    @Test
    fun decodeOrDefault_fallsBackForBadStoredValues() {
        assertEquals(ReviewLadder.DEFAULT, ReviewLadder.decodeOrDefault(null))
        assertEquals(ReviewLadder.DEFAULT, ReviewLadder.decodeOrDefault("garbage"))
        assertEquals(ReviewLadder.DEFAULT, ReviewLadder.decodeOrDefault("1d,1h"))
        assertEquals("10m,1d", ReviewLadder.decodeOrDefault("10m,1d").encode())
    }

    @Test
    fun clampStep_keepsStoredStepsInsideAShortenedLadder() {
        val short = ReviewLadder.of(listOf(i("10m"), i("1d"), i("3d")))
        assertEquals(2, short.clampStep(6))
        assertEquals(0, short.clampStep(-1))
    }
}
