package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.ReviewSessionDao
import com.n3sensei.app.data.local.entity.ReviewSessionEntity
import com.n3sensei.app.domain.forecast.ReviewSessionRepository
import com.n3sensei.app.domain.forecast.ReviewSessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class OfflineReviewSessionRepository(private val dao: ReviewSessionDao) : ReviewSessionRepository {

    override suspend fun start(id: String, planned: Int, at: Instant) =
        dao.insert(ReviewSessionEntity(id, at.toEpochMilli(), at.toEpochMilli(), planned, 0, finished = false))

    override suspend fun updateAnswered(id: String, answered: Int, at: Instant) =
        dao.updateAnswered(id, answered, at.toEpochMilli())

    override suspend fun finish(id: String, at: Instant) = dao.finish(id, at.toEpochMilli())

    override fun observeRecent(limit: Int): Flow<List<ReviewSessionSummary>> =
        dao.observeRecent(limit).map { rows ->
            rows.map {
                ReviewSessionSummary(it.id, Instant.ofEpochMilli(it.startedAt), Instant.ofEpochMilli(it.lastActivityAt), it.planned, it.answered, it.finished)
            }
        }
}
