package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.AnalyticsDao
import com.n3sensei.app.domain.analytics.ActivityTotals
import com.n3sensei.app.domain.analytics.DueCounts
import com.n3sensei.app.domain.analytics.PriorityItem
import com.n3sensei.app.domain.analytics.StatusByType
import com.n3sensei.app.domain.analytics.StatusCounts
import com.n3sensei.app.domain.analytics.TimeBucket
import com.n3sensei.app.domain.analytics.TroubleItem
import com.n3sensei.app.domain.forecast.ScheduledItem
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.repository.AnalyticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class OfflineAnalyticsRepository(private val dao: AnalyticsDao) : AnalyticsRepository {

    override fun observeActivity(from: Instant?, to: Instant): Flow<ActivityTotals> =
        dao.observeActivity(from.orAllTime(), to.toEpochMilli()).map { r ->
            ActivityTotals(
                answers = r.answers,
                distinctItems = r.distinctItems,
                newItems = r.newItems,
                reviewAnswers = r.reviewAnswers,
                distinctReviewedItems = r.distinctReviewed,
                reviewRatings = mapOf(
                    ReviewRating.AGAIN to r.again,
                    ReviewRating.HARD to r.hard,
                    ReviewRating.GOOD to r.good,
                    ReviewRating.EASY to r.easy,
                ).filterValues { it > 0 },
            )
        }

    override fun observeTimeBuckets(from: Instant, to: Instant): Flow<List<TimeBucket>> =
        dao.observeBuckets(from.toEpochMilli(), to.toEpochMilli()).map { rows ->
            rows.map { TimeBucket(Instant.ofEpochMilli(it.bucketStart), it.answers, it.newItems) }
        }

    override fun observeStudyBuckets(): Flow<List<Instant>> =
        dao.observeStudyBuckets().map { rows -> rows.map(Instant::ofEpochMilli) }

    override fun observeStatusByType(): Flow<StatusByType> =
        dao.observeStatusByType().map { rows ->
            val byType = rows.associate { it.itemType to StatusCounts(it.newCount, it.learning, it.review, it.mastered) }
            ItemType.entries.associateWith { byType[it] ?: StatusCounts.EMPTY }
        }

    override fun observeDue(now: Instant, dayStart: Instant): Flow<DueCounts> =
        dao.observeDue(now.toEpochMilli(), dayStart.toEpochMilli()).map { DueCounts(it.due, it.overdue) }

    override fun observeDueBuckets(from: Instant, to: Instant): Flow<List<TimeBucket>> =
        dao.observeDueBuckets(from.toEpochMilli(), to.toEpochMilli()).map { rows ->
            rows.map { TimeBucket(Instant.ofEpochMilli(it.bucketStart), it.answers, 0) }
        }

    override fun observeAnswersByType(from: Instant?, to: Instant): Flow<Map<ItemType, Int>> =
        dao.observeAnswersByType(from.orAllTime(), to.toEpochMilli()).map { rows ->
            val counts = rows.associate { it.itemType to it.count }
            ItemType.entries.associateWith { counts[it] ?: 0 }
        }

    override fun observePriorityItems(now: Instant, limit: Int): Flow<List<PriorityItem>> =
        dao.observePriorityItems(now.toEpochMilli(), limit).map { rows ->
            rows.mapNotNull { r -> r.label?.let { PriorityItem(ItemRef(r.itemType, r.itemId), it, Instant.ofEpochMilli(r.dueAt), r.lapseCount) } }
        }

    override fun observeSchedule(): Flow<List<ScheduledItem>> =
        dao.observeSchedule().map { rows -> rows.map { ScheduledItem(it.stepIndex, Instant.ofEpochMilli(it.dueAt)) } }

    override fun observeTroubleItems(from: Instant?, to: Instant, limit: Int): Flow<List<TroubleItem>> =
        dao.observeTroubleItems(from.orAllTime(), to.toEpochMilli(), limit).map { rows ->
            rows.mapNotNull { r ->
                r.label?.let { TroubleItem(ItemRef(r.itemType, r.itemId), it, r.again, r.hard, Instant.ofEpochMilli(r.lastAt)) }
            }
        }
}

/** "No lower bound" as a value, so the SQL keeps a plain indexed range. */
private fun Instant?.orAllTime(): Long = this?.toEpochMilli() ?: Long.MIN_VALUE
