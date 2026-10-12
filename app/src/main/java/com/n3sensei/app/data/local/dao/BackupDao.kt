package com.n3sensei.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.backup.toBackup
import com.n3sensei.app.data.backup.toEntity
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.local.entity.ReviewSessionEntity
import com.n3sensei.app.data.local.model.ItemRefRow
import com.n3sensei.app.domain.backup.BackupData
import com.n3sensei.app.domain.backup.BackupMerge
import com.n3sensei.app.domain.backup.BackupSection
import com.n3sensei.app.domain.backup.LocalKeys
import com.n3sensei.app.domain.backup.MergeCount
import com.n3sensei.app.domain.backup.RecordKey
import com.n3sensei.app.domain.backup.ReviewRecordBackup
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating

data class ProgressKeyRow(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class RecordKeyRow(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    val rating: ReviewRating,
    @ColumnInfo(name = "request_id") val requestId: String?,
)

/** Learner data for backup (read) and restore (merge, one transaction, never deletes). */
@Dao
abstract class BackupDao {

    @Query("SELECT * FROM learning_progress ORDER BY item_type, item_id")
    protected abstract suspend fun allProgress(): List<LearningProgressEntity>

    @Query("SELECT * FROM review_records WHERE id > :afterId ORDER BY id LIMIT :limit")
    protected abstract suspend fun recordPage(afterId: Long, limit: Int): List<ReviewRecordEntity>

    @Query("SELECT * FROM item_marks ORDER BY item_type, item_id")
    protected abstract suspend fun allMarks(): List<ItemMarkEntity>

    @Query("SELECT * FROM review_sessions ORDER BY started_at, id")
    protected abstract suspend fun allSessions(): List<ReviewSessionEntity>

    /**
     * Consistent copy of all learner data in one read transaction. Progress, marks and sessions
     * (bounded by the content size) come at once through [onBase]; the review history, which
     * grows without bound, comes in pages through [onRecords] so it is never all in memory.
     */
    @Transaction
    open suspend fun export(onBase: suspend (BackupData) -> Unit, onRecords: suspend (List<ReviewRecordBackup>) -> Unit) {
        onBase(
            BackupData(
                progress = allProgress().map { it.toBackup() },
                marks = allMarks().map { it.toBackup() },
                sessions = allSessions().map { it.toBackup() },
            ),
        )
        var after = 0L
        while (true) {
            val page = recordPage(after, PAGE_SIZE)
            if (page.isEmpty()) break
            onRecords(page.map { it.toBackup() })
            after = page.last().id
        }
    }

    @Query("SELECT item_type, item_id, updated_at FROM learning_progress")
    protected abstract suspend fun progressKeys(): List<ProgressKeyRow>

    @Query("SELECT request_id FROM review_records WHERE request_id IS NOT NULL")
    protected abstract suspend fun requestIds(): List<String>

    /** Answers recorded before database v3 have no request id: they are matched by item, time and rating. */
    @Query("SELECT item_type, item_id, reviewed_at, rating, request_id FROM review_records WHERE request_id IS NULL")
    protected abstract suspend fun legacyRecordKeys(): List<RecordKeyRow>

    @Query("SELECT DISTINCT item_type, item_id FROM review_records")
    protected abstract suspend fun itemsWithHistory(): List<ItemRefRow>

    @Query("SELECT item_type, item_id FROM item_marks")
    protected abstract suspend fun markKeys(): List<ItemRefRow>

    @Query("SELECT id FROM review_sessions")
    protected abstract suspend fun sessionIds(): List<String>

    @Transaction
    open suspend fun localKeys(): LocalKeys {
        return LocalKeys(
            progressUpdatedAt = progressKeys().associate { ItemRef(it.itemType, it.itemId) to it.updatedAt },
            requestIds = requestIds().toHashSet(),
            recordKeys = legacyRecordKeys().mapTo(HashSet()) { RecordKey(ItemRef(it.itemType, it.itemId), it.reviewedAt, it.rating) },
            itemsWithHistory = itemsWithHistory().mapTo(HashSet()) { ItemRef(it.itemType, it.itemId) },
            marks = markKeys().mapTo(HashSet()) { ItemRef(it.itemType, it.itemId) },
            sessionIds = sessionIds().toHashSet(),
        )
    }

    @Query(
        """
        SELECT 'VOCABULARY' AS item_type, id AS item_id FROM vocabulary
        UNION ALL SELECT 'KANJI', id FROM kanji
        UNION ALL SELECT 'GRAMMAR', id FROM grammar
        """,
    )
    protected abstract suspend fun contentRefRows(): List<ItemRefRow>

    suspend fun contentRefs(): Set<ItemRef> = contentRefRows().mapTo(HashSet()) { ItemRef(it.itemType, it.itemId) }

    @Upsert
    protected abstract suspend fun upsertProgress(rows: List<LearningProgressEntity>)

    @Insert
    protected abstract suspend fun insertRecords(rows: List<ReviewRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMarks(rows: List<ItemMarkEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSessions(rows: List<ReviewSessionEntity>)

    /** The earliest answer of an item is its first answer as a new item (same rule as migration 2 → 3). */
    @Query(
        """
        UPDATE review_records SET was_new = CASE WHEN id = (
            SELECT r.id FROM review_records r WHERE r.item_type = :type AND r.item_id = :id
            ORDER BY r.reviewed_at, r.id LIMIT 1
        ) THEN 1 ELSE 0 END
        WHERE item_type = :type AND item_id = :id
        """,
    )
    protected abstract suspend fun recomputeFirstAnswer(type: ItemType, id: String)

    /**
     * Merges [data] by [BackupMerge] against the database as it is inside this transaction.
     * Any failure rolls everything back.
     */
    @Transaction
    open suspend fun merge(data: BackupData): Map<BackupSection, MergeCount> {
        val plan = BackupMerge.plan(data, localKeys())
        upsertProgress(plan.progressToWrite.map { it.toEntity() })
        // In chunks: a long history is converted a page at a time, not all at once.
        plan.recordsToAdd.chunked(PAGE_SIZE).forEach { chunk -> insertRecords(chunk.map { it.toEntity() }) }
        plan.itemsToRecompute.forEach { recomputeFirstAnswer(it.type, it.id) }
        insertMarks(plan.marksToAdd.map { it.toEntity() })
        insertSessions(plan.sessionsToAdd.map { it.toEntity() })
        return plan.counts
    }

    private companion object {
        const val PAGE_SIZE = 1_000
    }
}
