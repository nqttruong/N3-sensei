package com.n3sensei.app.testing

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory [ProgressRepository] for ViewModel tests. Marks and submitted reviews are real
 * (including request-id de-duplication); summary/overview values are set by the test.
 */
class FakeProgressRepository(
    var summary: ProgressSummary = ProgressSummary(0, 0, 0, 0, 0, 0),
) : ProgressRepository {
    val marks = MutableStateFlow<Set<ItemRef>>(emptySet())
    val progress = mutableMapOf<ItemRef, LearningProgress>()
    val records = mutableListOf<ReviewRecord>()
    var summaryRequestedAt: Instant? = null

    /** Due, new and last-forgotten items, each list in its query order (all types). */
    var dueItems: List<ItemRef> = emptyList()
    var newItems: List<ItemRef> = emptyList()
    var forgottenItems: List<ItemRef> = emptyList()
    var overview = ReviewOverview(emptyMap(), 0, TodayCounts(0, 0), 0)

    /** Number of upcoming submitReview calls that throw, to simulate a failed write. */
    var failNextSubmits = 0
    var submitCalls = 0

    override fun observeItemState(item: ItemRef): Flow<ItemUserState> =
        marks.map { ItemUserState(LearningStatus.from(progress[item]?.state), item in it, progress[item]?.dueAt, progress[item]?.stepIndex) }

    override suspend fun setMarked(item: ItemRef, marked: Boolean) =
        marks.update { if (marked) it + item else it - item }

    override fun observeMarkedCount(): Flow<Int> = marks.map { it.size }

    override suspend fun submitReview(
        item: ItemRef,
        requestId: String,
        newItemLimit: NewItemLimit?,
        compute: (current: LearningProgress?) -> ScheduledReview,
    ): SubmitOutcome {
        submitCalls++
        if (failNextSubmits > 0) {
            failNextSubmits--
            error("disk full")
        }
        if (records.any { it.requestId == requestId }) return SubmitOutcome.DUPLICATE
        // Same rule as the database: a first answer only while its type's cap for the day is not reached.
        if (progress[item] == null && newItemLimit != null) {
            val introduced = records.count {
                it.wasNew && it.item.type == item.type && !it.reviewedAt.isBefore(newItemLimit.dayStart) && it.reviewedAt.isBefore(newItemLimit.dayEnd)
            }
            if (introduced >= newItemLimit.limit) return SubmitOutcome.NEW_LIMIT_REACHED
        }
        val scheduled = compute(progress[item])
        progress[item] = scheduled.progress
        records += scheduled.record
        return SubmitOutcome.APPLIED
    }

    override suspend fun getProgress(item: ItemRef): LearningProgress? = progress[item]
    override suspend fun getDueItems(now: Instant, limit: Int): List<ItemRef> = dueItems.take(limit)
    override suspend fun getNewItems(limit: Int): List<ItemRef> = newItems.take(limit)
    override suspend fun getDueItemsOfType(now: Instant, type: ItemType, limit: Int) = dueItems.filter { it.type == type }.take(limit)
    override suspend fun getNewItemsOfType(type: ItemType, limit: Int) = newItems.filter { it.type == type }.take(limit)
    override suspend fun getForgottenItemsOfType(type: ItemType, limit: Int) = forgottenItems.filter { it.type == type }.take(limit)
    override suspend fun getForgottenItems(limit: Int) = forgottenItems.take(limit)
    override fun observeReviewOverview(now: Instant, dayStart: Instant, dayEnd: Instant): Flow<ReviewOverview> = flowOf(overview)

    override fun observeSummary(now: Instant): Flow<ProgressSummary> {
        summaryRequestedAt = now
        return flowOf(summary)
    }

    override fun observeProgress(item: ItemRef) = flowOf(progress[item])
    override fun observeDue(now: Instant, limit: Int) = flowOf(emptyList<LearningProgress>())
    override fun observeDueCount(now: Instant) = flowOf(0)
    override fun observeRatingCounts() = flowOf(emptyMap<ReviewRating, Int>())
    override suspend fun recordReview(progress: LearningProgress, record: ReviewRecord) = Unit
    override suspend fun getReviewHistory(item: ItemRef, limit: Int) = records.filter { it.item == item }.reversed().take(limit)
}
