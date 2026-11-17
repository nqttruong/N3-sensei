package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.model.TypeCountRow
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ItemUserState
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.NewItemLimit
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class OfflineProgressRepository(
    private val progressDao: ProgressDao,
    private val clock: Clock,
    /**
     * Lessons review sessions take new kanji from (the learner's choice, see
     * [com.n3sensei.app.domain.srs.KanjiReviewLessons]); null: every lesson. Only new kanji are
     * concerned: kanji with progress stay due and reviewed whatever is chosen.
     */
    private val newKanjiLessons: Flow<Set<String>>? = null,
) : ProgressRepository {

    override fun observeItemState(item: ItemRef): Flow<ItemUserState> =
        progressDao.observeItemState(item.type, item.id).map {
            ItemUserState(LearningStatus.from(it.progressState), it.isMarked, it.dueAt?.let(Instant::ofEpochMilli), it.stepIndex)
        }

    override suspend fun submitReview(
        item: ItemRef,
        requestId: String,
        newItemLimit: NewItemLimit?,
        compute: (current: LearningProgress?) -> ScheduledReview,
    ): SubmitOutcome {
        val result = progressDao.applyReview(
            item.type, item.id, requestId,
            newLimit = newItemLimit?.limit,
            dayStart = newItemLimit?.dayStart?.toEpochMilli() ?: 0,
            dayEnd = newItemLimit?.dayEnd?.toEpochMilli() ?: 0,
        ) { current ->
            val scheduled = compute(current?.toDomain())
            require(scheduled.progress.item == item && scheduled.record.item == item) { "Scheduled review for another item" }
            require(scheduled.record.requestId == requestId) { "Record must carry the request id" }
            scheduled.progress.toEntity() to scheduled.record.toEntity()
        }
        return when (result) {
            ProgressDao.ApplyResult.APPLIED -> SubmitOutcome.APPLIED
            ProgressDao.ApplyResult.DUPLICATE -> SubmitOutcome.DUPLICATE
            ProgressDao.ApplyResult.NEW_LIMIT_REACHED -> SubmitOutcome.NEW_LIMIT_REACHED
        }
    }

    override suspend fun getProgress(item: ItemRef): LearningProgress? = progressDao.get(item.type, item.id)?.toDomain()

    override suspend fun getDueItems(now: Instant, limit: Int): List<ItemRef> =
        progressDao.getDueRefs(now.toEpochMilli(), limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getNewItems(limit: Int): List<ItemRef> =
        progressDao.getNewRefs(limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getDueItemsOfType(now: Instant, type: ItemType, limit: Int): List<ItemRef> =
        progressDao.getDueRefsOfType(now.toEpochMilli(), type, limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getNewItemsOfType(type: ItemType, limit: Int): List<ItemRef> {
        val rows = if (type == ItemType.KANJI && newKanjiLessons != null) {
            val lessons = newKanjiLessons.first()
            if (lessons.isEmpty()) emptyList() else progressDao.getNewKanjiRefsInLessons(lessons.toList(), limit.coerceAtLeast(0))
        } else {
            progressDao.getNewRefsOfType(type, limit.coerceAtLeast(0))
        }
        return rows.map { ItemRef(it.itemType, it.itemId) }
    }

    /** New items by type; new kanji only from [newKanjiLessons]. */
    private fun observeNewByType(): Flow<List<TypeCountRow>> {
        val lessons = newKanjiLessons ?: return progressDao.observeNewByType()
        return combine(progressDao.observeNewByType(), progressDao.observeNewKanjiByLesson(), lessons) { byType, kanji, allowed ->
            byType.filter { it.itemType != ItemType.KANJI } +
                TypeCountRow(ItemType.KANJI, kanji.filter { it.lessonId in allowed }.sumOf { it.count })
        }
    }

    override suspend fun getForgottenItemsOfType(type: ItemType, limit: Int): List<ItemRef> =
        progressDao.getForgottenRefsOfType(type, limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override suspend fun getForgottenItems(limit: Int): List<ItemRef> =
        progressDao.getForgottenRefs(limit.coerceAtLeast(0)).map { ItemRef(it.itemType, it.itemId) }

    override fun observeReviewOverview(now: Instant, dayStart: Instant, dayEnd: Instant): Flow<ReviewOverview> = combine(
        combine(
            progressDao.observeDueByType(now.toEpochMilli()),
            progressDao.observeOverdueByType(dayStart.toEpochMilli()),
            progressDao.observeTodayCounts(dayStart.toEpochMilli(), dayEnd.toEpochMilli()),
            observeNewByType(),
        ) { due, overdue, today, new -> Triple(due to overdue, today, new) },
        progressDao.observeDoneByType(dayStart.toEpochMilli(), dayEnd.toEpochMilli()),
        progressDao.observeForgottenByType(),
        progressDao.observeNewByTypeBetween(dayStart.toEpochMilli(), dayEnd.toEpochMilli()),
    ) { (dueOverdue, today, new), done, forgotten, newToday ->
        val (due, overdue) = dueOverdue
        val overdueByType = overdue.byType()
        val newByType = new.byType()
        ReviewOverview(
            dueByType = due.byType(),
            overdue = overdueByType.values.sum(),
            today = TodayCounts(today.newAnswered, today.reviewsAnswered, newToday.byType()),
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
