package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ItemUserState
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.NewItemLimit
import com.n3sensei.app.domain.model.ProgressSummary
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.srs.ScheduledReview
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

    /** Totals over all progress whose content still exists, with due items counted at [now]. */
    fun observeSummary(now: Instant): Flow<ProgressSummary>

    /** Number of review records per rating; ratings never used are absent. */
    fun observeRatingCounts(): Flow<Map<ReviewRating, Int>>

    /** Status and "needs attention" mark of one item. */
    fun observeItemState(item: ItemRef): Flow<ItemUserState>

    /** Marks or unmarks an item as needing attention. Idempotent. */
    suspend fun setMarked(item: ItemRef, marked: Boolean)

    fun observeMarkedCount(): Flow<Int>

    /**
     * Applies one answer atomically: reads the current progress, lets [compute] (the domain
     * scheduler) derive the new progress and record, and writes both in one transaction.
     * A second call with the same [requestId] (double tap, retry) changes nothing. With
     * [newItemLimit], a first answer to a never-answered item is refused (nothing written) once
     * its type introduced that many items in the day, counted inside the same transaction.
     */
    suspend fun submitReview(
        item: ItemRef,
        requestId: String,
        newItemLimit: NewItemLimit? = null,
        compute: (current: LearningProgress?) -> ScheduledReview,
    ): SubmitOutcome

    suspend fun getProgress(item: ItemRef): LearningProgress?

    /** Due items (content still present), oldest due first. */
    suspend fun getDueItems(now: Instant, limit: Int): List<ItemRef>

    /** Items never answered, in course order (pack, lesson, type, position). */
    suspend fun getNewItems(limit: Int): List<ItemRef>

    /** Due items of one [type] only (its own schedule), oldest due first. */
    suspend fun getDueItemsOfType(now: Instant, type: ItemType, limit: Int): List<ItemRef>

    /** Never-answered items of one [type], in course order. */
    suspend fun getNewItemsOfType(type: ItemType, limit: Int): List<ItemRef>

    /** Items of [type] whose last answer was "Quên", due or not yet; the due ones first. */
    suspend fun getForgottenItemsOfType(type: ItemType, limit: Int): List<ItemRef>

    /** [getForgottenItemsOfType] for all types together: the due ones first, the earliest due first. */
    suspend fun getForgottenItems(limit: Int): List<ItemRef>

    /** Counts for the Review screen; [dayStart]/[dayEnd] bound "today" in local time. */
    fun observeReviewOverview(now: Instant, dayStart: Instant, dayEnd: Instant): Flow<ReviewOverview>

    /** Saves the updated progress and its review record atomically (no idempotency check). */
    suspend fun recordReview(progress: LearningProgress, record: ReviewRecord)

    /** Most recent first. */
    suspend fun getReviewHistory(item: ItemRef, limit: Int): List<ReviewRecord>
}
