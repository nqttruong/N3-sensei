package com.n3sensei.app.testing

import com.n3sensei.app.domain.analytics.ActivityTotals
import com.n3sensei.app.domain.analytics.DueCounts
import com.n3sensei.app.domain.analytics.PriorityItem
import com.n3sensei.app.domain.analytics.StatusByType
import com.n3sensei.app.domain.analytics.StatusCounts
import com.n3sensei.app.domain.analytics.TimeBucket
import com.n3sensei.app.domain.analytics.TroubleItem
import com.n3sensei.app.domain.analytics.TypeActivity
import com.n3sensei.app.domain.forecast.ReviewSessionRepository
import com.n3sensei.app.domain.forecast.ReviewSessionSummary
import com.n3sensei.app.domain.forecast.ScheduledItem
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.AnalyticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

/** Analytics with values set by the test. */
class FakeAnalyticsRepository : AnalyticsRepository {
    var activity = ActivityTotals.EMPTY
    var due = DueCounts(0, 0)
    var status: StatusByType = ItemType.entries.associateWith { StatusCounts.EMPTY }
    var buckets: List<TimeBucket> = emptyList()
    var schedule: List<ScheduledItem> = emptyList()
    var priority: List<PriorityItem> = emptyList()
    var answersByType: Map<ItemType, Int> = ItemType.entries.associateWith { 0 }

    override fun observeActivity(from: Instant?, to: Instant) = flowOf(activity)
    override fun observeTimeBuckets(from: Instant, to: Instant) = flowOf(buckets.filter { !it.start.isBefore(from) && it.start.isBefore(to) })
    override fun observeStudyBuckets() = flowOf(buckets.map { it.start })
    override fun observeStatusByType() = flowOf(status)
    override fun observeDue(now: Instant, dayStart: Instant) = flowOf(due)
    override fun observeDueBuckets(from: Instant, to: Instant) = flowOf(emptyList<TimeBucket>())
    override fun observeTroubleItems(from: Instant?, to: Instant, limit: Int) = flowOf(emptyList<TroubleItem>())
    override fun observeAnswersByType(from: Instant?, to: Instant) = flowOf(answersByType)
    override fun observeActivityByType(from: Instant?, to: Instant) = flowOf(ItemType.entries.associateWith { TypeActivity.EMPTY })
    override fun observeTimeBucketsByType(from: Instant, to: Instant) = flowOf(emptyMap<ItemType, List<TimeBucket>>())
    override fun observePriorityItems(now: Instant, limit: Int) = flowOf(priority.take(limit))
    override fun observeSchedule() = flowOf(schedule)
}

class FakeReviewSessionRepository : ReviewSessionRepository {
    val sessions = MutableStateFlow<List<ReviewSessionSummary>>(emptyList())

    /** Makes every write fail, as a full disk would. */
    var failWrites = false

    override suspend fun start(id: String, planned: Int, at: Instant) {
        if (failWrites) throw IOException("disk full")
        if (sessions.value.none { it.id == id }) sessions.value = listOf(ReviewSessionSummary(id, at, at, planned, 0, false)) + sessions.value
    }

    override suspend fun updateAnswered(id: String, answered: Int, at: Instant) {
        if (failWrites) throw IOException("disk full")
        sessions.value = sessions.value.map { if (it.id == id) it.copy(answered = maxOf(it.answered, answered), lastActivityAt = at) else it }
    }

    override suspend fun finish(id: String, at: Instant) {
        if (failWrites) throw IOException("disk full")
        sessions.value = sessions.value.map { if (it.id == id) it.copy(finished = true, lastActivityAt = at) else it }
    }

    override fun observeRecent(limit: Int): Flow<List<ReviewSessionSummary>> = sessions.map { it.take(limit) }
}
