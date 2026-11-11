package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.model.TypeCountRow
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ItemUserState
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.ProgressSummary
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.srs.ScheduledReview
import com.n3sensei.app.domain.srs.TodayCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class OfflineProgressRepository(
    private val progressDao: ProgressDao,
    private val clock: Clock,
) : ProgressRepository {

    override fun observeItemState(item: ItemRef): Flow<ItemUserState> =
        progressDao.observeItemState(item.type, item.id).map {
            ItemUserState(LearningStatus.from(it.progressState), it.isMarked, it.dueAt?.let(Instant::ofEpochMilli), it.stepIndex)
        }

    override suspend fun submitReview(
        item: ItemRef,
        requestId: String,
        compute: (current: LearningProgress?) -> ScheduledReview,
    ): SubmitOutcome {
        val applied = progressDao.applyReview(item.type, item.id, requestId) { current ->
            val scheduled = compute(current?.toDomain())
            require(scheduled.progress.item == item && scheduled.record.item == item) { "Scheduled review for another item" }
            require(scheduled.record.requestId == requestId) { "Record must carry the request id" }
            scheduled.progress.toEntity() to scheduled.record.toEntity()
        }
        return if (applied) SubmitOutcome.APPLIED else SubmitOutcome.DUPLICATE
    }

    override suspend fun getProgress(item: ItemRef): LearningProgress? = progressDao.get(item.type, item.id)?.toDomain()

    override suspend fun getDueItems(now: Instant, limit: Int): List<ItemRef> =
        progressDao.getDueRefs(now.toEpochMilli(), limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getNewItems(limit: Int): List<ItemRef> =
        progressDao.getNewRefs(limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getDueItemsOfType(now: Instant, type: ItemType, limit: Int): List<ItemRef> =
        progressDao.getDueRefsOfType(now.toEpochMilli(), type, limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getNewItemsOfType(type: ItemType, limit: Int): List<ItemRef> =
        progressDao.getNewRefsOfType(type, limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getForgottenItemsOfType(type: ItemType, limit: Int): List<ItemRef> =
        progressDao.getForgottenRefsOfType(type, limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override fun observeReviewOverview(now: Instant, dayStart: Instant, dayEnd: Instant): Flow<ReviewOverview> = combine(
        combine(
            progressDao.observeDueByType(now.toEpochMilli()),
            progressDao.observeOverdueByType(dayStart.toEpochMilli()),
            progressDao.observeTodayCounts(dayStart.toEpochMilli(), dayEnd.toEpochMilli()),
            progressDao.observeNewByType(),
        ) { due, overdue, today, new -> Triple(due to overdue, today, new) },
        progressDao.observeDoneByType(dayStart.toEpochMilli(), dayEnd.toEpochMilli()),
        progressDao.observeForgottenByType(),
    ) { (dueOverdue, today, new), done, forgotten ->
        val (due, overdue) = dueOverdue
        val overdueByType = overdue.byType()
        val newByType = new.byType()
        ReviewOverview(
            dueByType = due.byType(),
            overdue = overdueByType.values.sum(),
            today = TodayCounts(today.newAnswered, today.reviewsAnswered),
            newAvailable = newByType.values.sum(),
            overdueByType = overdueByType,
            doneTodayByType = done.byType(),
            newByType = newByType,
            forgottenByType = forgotten.byType(),
        )
    }

    private fun List<TypeCountRow>.byType(): Map<ItemType, Int> = filter { it.count > 0 }.associate { it.itemType to it.count }

    override suspend fun setMarked(item: ItemRef, marked: Boolean) {
        if (marked) {
            progressDao.insertMark(ItemMarkEntity(item.type, item.id, clock.millis()))
        } else {
            progressDao.deleteMark(item.type, item.id)
        }
    }

    override fun observeMarkedCount(): Flow<Int> = progressDao.observeMarkedCount()

    override fun observeProgress(item: ItemRef): Flow<LearningProgress?> =
        progressDao.observe(item.type, item.id).map { it?.toDomain() }

    override fun observeDue(now: Instant, limit: Int): Flow<List<LearningProgress>> =
        progressDao.observeDue(now.toEpochMilli(), limit.coerceAtLeast(1)).map { rows -> rows.map { it.toDomain() } }

    override fun observeDueCount(now: Instant): Flow<Int> =
        progressDao.observeDueCount(now.toEpochMilli())

    override fun observeSummary(now: Instant): Flow<ProgressSummary> =
        progressDao.observeSummary(now.toEpochMilli()).map {
            ProgressSummary(it.trackedItems, it.learning, it.inReview, it.mastered, it.dueNow, it.totalReviews)
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
