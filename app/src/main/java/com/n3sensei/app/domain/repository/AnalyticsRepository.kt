package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.analytics.ActivityTotals
import com.n3sensei.app.domain.analytics.DueCounts
import com.n3sensei.app.domain.analytics.StatusByType
import com.n3sensei.app.domain.analytics.TimeBucket
import com.n3sensei.app.domain.analytics.TroubleItem
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Aggregates over review history and progress, computed in the database. All queries exclude
 * seed/test content and items that no longer exist. [from] null means "since the beginning".
 */
interface AnalyticsRepository {
    fun observeActivity(from: Instant?, to: Instant): Flow<ActivityTotals>

    /** Answers grouped into 15-minute blocks within [from, to). */
    fun observeTimeBuckets(from: Instant, to: Instant): Flow<List<TimeBucket>>

    /** Start of every 15-minute block that contains at least one answer (for streaks). */
    fun observeStudyBuckets(): Flow<List<Instant>>

    fun observeStatusByType(): Flow<StatusByType>

    fun observeDue(now: Instant, dayStart: Instant): Flow<DueCounts>

    /** Scheduled due times in [from, to), in 15-minute blocks ([TimeBucket.answers] = items). */
    fun observeDueBuckets(from: Instant, to: Instant): Flow<List<TimeBucket>>

    /** Items with the most "Quên" + "Khó" answers in the period, most first. */
    fun observeTroubleItems(from: Instant?, to: Instant, limit: Int): Flow<List<TroubleItem>>
}
