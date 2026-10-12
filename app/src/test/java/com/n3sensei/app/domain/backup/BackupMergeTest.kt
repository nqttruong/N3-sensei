package com.n3sensei.app.domain.backup

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    private val a = ItemRef(ItemType.VOCABULARY, "a")
    private val b = ItemRef(ItemType.VOCABULARY, "b")
    private val c = ItemRef(ItemType.KANJI, "c")

    private fun progress(ref: ItemRef, updatedAt: Long) = ProgressBackup(ref, ProgressState.REVIEW, 2, 1440, updatedAt + 1, updatedAt, 2, 0, 0, updatedAt)
    private fun record(ref: ItemRef, at: Long, id: String?, rating: ReviewRating = ReviewRating.GOOD) =
        ReviewRecordBackup(ref, rating, at, 0, 60, at + 60_000, id, wasNew = false)

    private val data = BackupData(
        progress = listOf(progress(a, 100), progress(b, 200), progress(c, 300)),
        reviewRecords = listOf(record(a, 100, "r1"), record(a, 50, null), record(b, 200, "r2"), record(c, 300, "r3")),
        marks = listOf(MarkBackup(a, 1), MarkBackup(b, 1)),
        sessions = listOf(SessionBackup("s1", 1, 2, 1, 1, true), SessionBackup("s2", 1, 2, 1, 1, true)),
    )

    @Test
    fun emptyDatabase_addsEverything() {
        val plan = BackupMerge.plan(data, LocalKeys())
        assertEquals(MergeCount(added = 3), plan.counts[BackupSection.PROGRESS])
        assertEquals(MergeCount(added = 4), plan.counts[BackupSection.REVIEW_RECORDS])
        assertEquals(MergeCount(added = 2), plan.counts[BackupSection.MARKS])
        assertEquals(MergeCount(added = 2), plan.counts[BackupSection.SESSIONS])
        // No local history: the flags in the file are kept as they are.
        assertTrue(plan.itemsToRecompute.isEmpty())
    }

    @Test
    fun progressConflicts_newerWins_tieKeepsLocal() {
        val local = LocalKeys(progressUpdatedAt = mapOf(a to 150L, b to 200L, c to 250L))
        val plan = BackupMerge.plan(data, local)
        // a: local newer → kept; b: tie → kept; c: backup newer → replaces.
        assertEquals(listOf(c), plan.progressToWrite.map { it.ref })
        assertEquals(MergeCount(added = 0, updated = 1, unchanged = 2), plan.counts[BackupSection.PROGRESS])
    }

    @Test
    fun existingAnswers_areNotAddedAgain_byRequestIdOrByItemTimeAndRating() {
        val local = LocalKeys(
            requestIds = setOf("r1"),
            recordKeys = setOf(RecordKey(a, 50, ReviewRating.GOOD), RecordKey(c, 300, ReviewRating.GOOD)),
        )
        val plan = BackupMerge.plan(data, local)
        assertEquals(listOf("r2"), plan.recordsToAdd.map { it.requestId })
        // b had no local answers; a and c gained nothing.
        assertTrue(plan.itemsToRecompute.isEmpty())
        assertEquals(MergeCount(added = 1, unchanged = 3), plan.counts[BackupSection.REVIEW_RECORDS])
    }

    @Test
    fun sameTimeDifferentRating_isADifferentAnswer() {
        val local = LocalKeys(recordKeys = setOf(RecordKey(a, 50, ReviewRating.AGAIN)), itemsWithHistory = setOf(a))
        val plan = BackupMerge.plan(data.copy(reviewRecords = listOf(record(a, 50, null))), local)
        assertEquals(1, plan.recordsToAdd.size)
        // a now has answers from both sides: its first answer is recomputed.
        assertEquals(setOf(a), plan.itemsToRecompute)
    }

    @Test
    fun restoringTwice_changesNothingTheSecondTime() {
        val afterFirst = LocalKeys(
            progressUpdatedAt = data.progress.associate { it.ref to it.updatedAt },
            requestIds = data.reviewRecords.mapNotNull { it.requestId }.toSet(),
            recordKeys = data.reviewRecords.filter { it.requestId == null }.map { RecordKey(it.ref, it.reviewedAt, it.rating) }.toSet(),
            itemsWithHistory = data.reviewRecords.map { it.ref }.toSet(),
            marks = data.marks.map { it.ref }.toSet(),
            sessionIds = data.sessions.map { it.id }.toSet(),
        )
        val plan = BackupMerge.plan(data, afterFirst)
        assertTrue(plan.counts.values.all { it.written == 0 })
        assertTrue(plan.progressToWrite.isEmpty() && plan.recordsToAdd.isEmpty() && plan.marksToAdd.isEmpty() && plan.sessionsToAdd.isEmpty())
    }

    @Test
    fun marksAndSessions_unionByKey() {
        val plan = BackupMerge.plan(data, LocalKeys(marks = setOf(a), sessionIds = setOf("s2")))
        assertEquals(listOf(b), plan.marksToAdd.map { it.ref })
        assertEquals(listOf("s1"), plan.sessionsToAdd.map { it.id })
    }
}
