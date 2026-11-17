package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.contrast.ContrastFilter
import com.n3sensei.app.domain.contrast.ContrastProgress
import com.n3sensei.app.domain.contrast.ContrastReview
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Counts for the Contrast Lab home. */
data class ContrastOverview(val dueCount: Int, val scheduledCount: Int, val nextDueAt: Instant?)

/**
 * Progress and review schedule of each group (one row per group, like learning_progress) and the
 * history of scheduling events. Written only through [ContrastPracticeRepository.recordAnswer],
 * so scheduling is atomic with the answer. Independent of learning_progress / review_records.
 *
 * Groups that are retired or gone from the installed pack keep their progress but are never due.
 */
interface ContrastProgressRepository {
    suspend fun progress(groupId: String): ContrastProgress?

    suspend fun allProgress(): List<ContrastProgress>

    /** Published groups due at [now], most overdue first. */
    suspend fun dueGroups(now: Instant, limit: Int): List<String>

    /** Published groups without progress matching [filter], in pack order. */
    suspend fun newGroups(limit: Int, filter: ContrastFilter = ContrastFilter()): List<String>

    /** Groups whose first review happened in [start, end) (for the daily new-group limit). */
    suspend fun newGroupsIntroducedBetween(start: Instant, end: Instant): Int

    suspend fun reviews(groupId: String): List<ContrastReview>

    fun observeOverview(now: Instant): Flow<ContrastOverview>
}
