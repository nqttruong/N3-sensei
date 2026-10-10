package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ProgressSummary
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class OfflineProgressRepository(
    private val progressDao: ProgressDao,
) : ProgressRepository {

    override fun observeProgress(item: ItemRef): Flow<LearningProgress?> =
        progressDao.observe(item.type, item.id).map { it?.toDomain() }

    override fun observeDue(now: Instant, limit: Int): Flow<List<LearningProgress>> =
        progressDao.observeDue(now.toEpochMilli(), limit.coerceAtLeast(1)).map { rows -> rows.map { it.toDomain() } }

    override fun observeDueCount(now: Instant): Flow<Int> =
        progressDao.observeDueCount(now.toEpochMilli())

    override fun observeSummary(now: Instant): Flow<ProgressSummary> =
        progressDao.observeSummary(now.toEpochMilli()).map {
            ProgressSummary(it.trackedItems, it.learning, it.inReview, it.dueNow, it.totalReviews)
        }

    override fun observeRatingCounts(): Flow<Map<ReviewRating, Int>> =
        progressDao.observeRatingCounts().map { rows -> rows.associate { it.rating to it.count } }

    override suspend fun recordReview(progress: LearningProgress, record: ReviewRecord) {
        require(progress.item == record.item) { "Progress and record refer to different items" }
        progressDao.recordReview(progress.toEntity(), record.toEntity())
    }

    override suspend fun getReviewHistory(item: ItemRef, limit: Int): List<ReviewRecord> =
        progressDao.getHistory(item.type, item.id, limit.coerceAtLeast(1)).map { it.toDomain() }
}
