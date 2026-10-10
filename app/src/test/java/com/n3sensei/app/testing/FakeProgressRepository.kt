package com.n3sensei.app.testing

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemUserState
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.ProgressSummary
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory [ProgressRepository] for ViewModel tests: marks are real, progress is fixed. */
class FakeProgressRepository(
    var summary: ProgressSummary = ProgressSummary(0, 0, 0, 0, 0),
) : ProgressRepository {
    val marks = MutableStateFlow<Set<ItemRef>>(emptySet())
    var summaryRequestedAt: Instant? = null

    override fun observeItemState(item: ItemRef): Flow<ItemUserState> =
        marks.map { ItemUserState(LearningStatus.NEW, item in it) }

    override suspend fun setMarked(item: ItemRef, marked: Boolean) =
        marks.update { if (marked) it + item else it - item }

    override fun observeMarkedCount(): Flow<Int> = marks.map { it.size }

    override fun observeSummary(now: Instant): Flow<ProgressSummary> {
        summaryRequestedAt = now
        return flowOf(summary)
    }

    override fun observeProgress(item: ItemRef) = flowOf<LearningProgress?>(null)
    override fun observeDue(now: Instant, limit: Int) = flowOf(emptyList<LearningProgress>())
    override fun observeDueCount(now: Instant) = flowOf(0)
    override fun observeRatingCounts() = flowOf(emptyMap<ReviewRating, Int>())
    override suspend fun recordReview(progress: LearningProgress, record: ReviewRecord) = Unit
    override suspend fun getReviewHistory(item: ItemRef, limit: Int) = emptyList<ReviewRecord>()
}
