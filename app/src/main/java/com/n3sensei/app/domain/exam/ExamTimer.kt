package com.n3sensei.app.domain.exam

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The exam countdown. What is left is computed from the wall clock and the attempt's start, never
 * counted down in memory, so time keeps running while the app is closed or the process is gone,
 * and a resumed attempt shows the real time left. A time limit of 0 means no limit.
 */
object ExamTimer {

    fun deadline(attempt: ExamAttempt): Long? =
        if (attempt.timeLimitSeconds > 0) attempt.startedAt + attempt.timeLimitSeconds * 1_000L else null

    /**
     * Milliseconds left at [nowMillis], 0 when the time is up; null without a limit. Never more
     * than the limit, even if the device clock was set back before the start.
     */
    fun remainingMillis(attempt: ExamAttempt, nowMillis: Long): Long? =
        deadline(attempt)?.let { (it - nowMillis).coerceIn(0, attempt.timeLimitSeconds * 1_000L) }

    fun isExpired(attempt: ExamAttempt, nowMillis: Long): Boolean = deadline(attempt)?.let { nowMillis >= it } ?: false

    /** When to refresh the countdown on screen: at once, then every [periodMillis]. Only a refresh signal. */
    fun ticks(periodMillis: Long = 1_000): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }
}
