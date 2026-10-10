package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ReviewRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The learner's personal progress and review history. */
interface ProgressRepository {
    fun observeProgress(item: ItemRef): Flow<LearningProgress?>

    /**
     * Items due at or before [now], oldest first. Progress whose content no longer exists is
     * excluded.
     */
    fun observeDue(now: Instant, limit: Int): Flow<List<LearningProgress>>

    fun observeDueCount(now: Instant): Flow<Int>

    /** Saves the updated progress and its review record atomically. */
    suspend fun recordReview(progress: LearningProgress, record: ReviewRecord)

    /** Most recent first. */
    suspend fun getReviewHistory(item: ItemRef, limit: Int): List<ReviewRecord>
}
