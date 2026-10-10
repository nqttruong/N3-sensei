package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.flow.Flow

// Matches progress rows whose content item still exists. Item type literals are ItemType names.
private const val CONTENT_EXISTS = """
    ((p.item_type = 'VOCABULARY' AND EXISTS (SELECT 1 FROM vocabulary v WHERE v.id = p.item_id))
    OR (p.item_type = 'KANJI' AND EXISTS (SELECT 1 FROM kanji k WHERE k.id = p.item_id))
    OR (p.item_type = 'GRAMMAR' AND EXISTS (SELECT 1 FROM grammar g WHERE g.id = p.item_id)))
"""

@Dao
abstract class ProgressDao {

    @Query("SELECT * FROM learning_progress WHERE item_type = :type AND item_id = :id")
    abstract fun observe(type: ItemType, id: String): Flow<LearningProgressEntity?>

    @Query("SELECT * FROM learning_progress WHERE item_type = :type AND item_id = :id")
    abstract suspend fun get(type: ItemType, id: String): LearningProgressEntity?

    @Upsert
    abstract suspend fun upsert(progress: LearningProgressEntity)

    @Insert
    abstract suspend fun insertRecord(record: ReviewRecordEntity): Long

    /** Progress update and its history record succeed or fail together. */
    @Transaction
    open suspend fun recordReview(progress: LearningProgressEntity, record: ReviewRecordEntity): Long {
        upsert(progress)
        return insertRecord(record)
    }

    @Query(
        """
        SELECT p.* FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND p.due_at <= :now AND $CONTENT_EXISTS
        ORDER BY p.due_at, p.item_type, p.item_id
        LIMIT :limit
        """,
    )
    abstract fun observeDue(now: Long, limit: Int): Flow<List<LearningProgressEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND p.due_at <= :now AND $CONTENT_EXISTS
        """,
    )
    abstract fun observeDueCount(now: Long): Flow<Int>

    @Query(
        """
        SELECT * FROM review_records WHERE item_type = :type AND item_id = :id
        ORDER BY reviewed_at DESC, id DESC LIMIT :limit
        """,
    )
    abstract suspend fun getHistory(type: ItemType, id: String, limit: Int): List<ReviewRecordEntity>
}
