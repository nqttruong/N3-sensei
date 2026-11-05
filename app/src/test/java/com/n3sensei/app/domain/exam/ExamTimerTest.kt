package com.n3sensei.app.domain.exam

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamTimerTest {

    private val attempt = ExamAttempt(
        id = "a", examSetId = "s", examTitle = "Đề", startedAt = 10_000, submittedAt = null, timeLimitSeconds = 1_800,
        durationSeconds = null, correctCount = null, questionCount = 6, status = AttemptStatus.IN_PROGRESS,
    )

    @Test
    fun timeLeft_comesFromTheClock_soTimeSpentAwayCounts() {
        assertEquals(1_810_000L, ExamTimer.deadline(attempt))
        assertEquals(1_800_000L, ExamTimer.remainingMillis(attempt, 10_000))
        // An hour later, whether the app was open or not.
        assertEquals(1_200_000L, ExamTimer.remainingMillis(attempt, 610_000))
        assertFalse(ExamTimer.isExpired(attempt, 610_000))
    }

    @Test
    fun theDeadlineItself_isExpired_andNothingIsLeftAfterIt() {
        assertFalse(ExamTimer.isExpired(attempt, 1_809_999))
        assertEquals(1L, ExamTimer.remainingMillis(attempt, 1_809_999))
        assertTrue(ExamTimer.isExpired(attempt, 1_810_000))
        assertEquals(0L, ExamTimer.remainingMillis(attempt, 1_810_000))
        assertEquals(0L, ExamTimer.remainingMillis(attempt, 99_000_000))
    }

    @Test
    fun aClockSetBackBeforeTheStart_neverShowsMoreThanTheLimit() {
        assertEquals(1_800_000L, ExamTimer.remainingMillis(attempt, 0))
        assertFalse(ExamTimer.isExpired(attempt, 0))
    }

    @Test
    fun noLimit_neverExpires() {
        val untimed = attempt.copy(timeLimitSeconds = 0)
        assertNull(ExamTimer.deadline(untimed))
        assertNull(ExamTimer.remainingMillis(untimed, Long.MAX_VALUE))
        assertFalse(ExamTimer.isExpired(untimed, Long.MAX_VALUE))
    }

    @Test
    fun ticks_comeAtOnce_thenEveryPeriod() = runTest {
        val times = mutableListOf<Long>()
        ExamTimer.ticks(1_000).take(3).collect { times += currentTime }
        assertEquals(listOf(0L, 1_000L, 2_000L), times.map { it - times.first() })
    }
}
