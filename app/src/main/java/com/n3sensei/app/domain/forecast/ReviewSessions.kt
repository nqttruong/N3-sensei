package com.n3sensei.app.domain.forecast

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A scheduled review session as it was planned and how far it got (REAL). */
data class ReviewSessionSummary(
    val id: String,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val planned: Int,
    val answered: Int,
    val finished: Boolean,
) {
    val completion: Float get() = if (planned == 0) 0f else answered.toFloat() / planned
}

/** Records review sessions for the dashboard history. */
interface ReviewSessionRepository {
    suspend fun start(id: String, planned: Int, at: Instant)

    /** Sets (not increments) the number of answered cards, so repeated calls are harmless. */
    suspend fun updateAnswered(id: String, answered: Int, at: Instant)

    suspend fun finish(id: String, at: Instant)

    fun observeRecent(limit: Int): Flow<List<ReviewSessionSummary>>
}
