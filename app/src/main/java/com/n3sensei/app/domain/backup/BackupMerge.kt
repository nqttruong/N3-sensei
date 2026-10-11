package com.n3sensei.app.domain.backup

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ReviewRating

/** Identity of an answer recorded without a request id (before database v3). */
data class RecordKey(val ref: ItemRef, val reviewedAt: Long, val rating: ReviewRating)

/** What the database already holds, as far as merging is concerned. */
data class LocalKeys(
    val progressUpdatedAt: Map<ItemRef, Long> = emptyMap(),
    val requestIds: Set<String> = emptySet(),
    val recordKeys: Set<RecordKey> = emptySet(),
    val marks: Set<ItemRef> = emptySet(),
    val sessionIds: Set<String> = emptySet(),
)

data class MergePlan(
    val progressToWrite: List<ProgressBackup>,
    val recordsToAdd: List<ReviewRecordBackup>,
    val marksToAdd: List<MarkBackup>,
    val sessionsToAdd: List<SessionBackup>,
    /**
     * Items whose history now comes from two sources (this device and the backup): their
     * "first answer" flag is recomputed from the merged history. Other items keep the flags
     * exactly as stored.
     */
    val itemsToRecompute: Set<ItemRef>,
    val counts: Map<BackupSection, MergeCount>,
)

/**
 * Merge strategy (never deletes anything):
 * - progress: the newer row wins by `updatedAt` (time of the last answer); a tie keeps the local row;
 * - review history: union; an answer already present (same request id, or same item, time and
 *   rating) is not added again, so restoring the same file twice changes nothing; where both
 *   sides had answers for an item, its earliest answer becomes its "first answer";
 * - marks and sessions: union by key.
 */
object BackupMerge {
    fun plan(data: BackupData, local: LocalKeys): MergePlan {
        val progress = mutableListOf<ProgressBackup>()
        var added = 0
        var updated = 0
        data.progress.forEach { p ->
            val localUpdated = local.progressUpdatedAt[p.ref]
            when {
                localUpdated == null -> { progress += p; added++ }
                p.updatedAt > localUpdated -> { progress += p; updated++ }
            }
        }
        val records = data.reviewRecords.filter { r ->
            val present = (r.requestId != null && r.requestId in local.requestIds) || RecordKey(r.ref, r.reviewedAt, r.rating) in local.recordKeys
            !present
        }
        val localHistory = local.recordKeys.mapTo(HashSet()) { it.ref }
        val marks = data.marks.filter { it.ref !in local.marks }
        val sessions = data.sessions.filter { it.id !in local.sessionIds }
        return MergePlan(
            progressToWrite = progress,
            recordsToAdd = records,
            marksToAdd = marks,
            sessionsToAdd = sessions,
            itemsToRecompute = records.mapTo(HashSet()) { it.ref }.filterTo(HashSet()) { it in localHistory },
            counts = mapOf(
                BackupSection.PROGRESS to MergeCount(added, updated, data.progress.size - added - updated),
                BackupSection.REVIEW_RECORDS to MergeCount(records.size, 0, data.reviewRecords.size - records.size),
                BackupSection.MARKS to MergeCount(marks.size, 0, data.marks.size - marks.size),
                BackupSection.SESSIONS to MergeCount(sessions.size, 0, data.sessions.size - sessions.size),
            ),
        )
    }
}
