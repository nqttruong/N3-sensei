package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.local.model.ItemStateRow
import com.n3sensei.app.data.local.model.ProgressSummaryRow
import com.n3sensei.app.data.local.model.RatingCountRow
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

    @Query(
        """
        SELECT
            (SELECT state FROM learning_progress WHERE item_type = :type AND item_id = :id) AS progress_state,
            EXISTS (SELECT 1 FROM item_marks WHERE item_type = :type AND item_id = :id) AS is_marked
        """,
    )
    abstract fun observeItemState(type: ItemType, id: String): Flow<ItemStateRow>

    /** Ignores an existing mark so the original marked_at is kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMark(mark: ItemMarkEntity)

    @Query("DELETE FROM item_marks WHERE item_type = :type AND item_id = :id")
    abstract suspend fun deleteMark(type: ItemType, id: String)

    /** Marks whose content still exists. */
    @Query("SELECT COUNT(*) FROM item_marks p WHERE $CONTENT_EXISTS")
    abstract fun observeMarkedCount(): Flow<Int>

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
        SELECT
            (SELECT COUNT(*) FROM learning_progress p WHERE $CONTENT_EXISTS) AS tracked_items,
            (SELECT COUNT(*) FROM learning_progress p WHERE p.state = 'LEARNING' AND $CONTENT_EXISTS) AS learning,
            (SELECT COUNT(*) FROM learning_progress p WHERE p.state = 'REVIEW' AND $CONTENT_EXISTS) AS in_review,
            (SELECT COUNT(*) FROM learning_progress p
                WHERE p.due_at IS NOT NULL AND p.due_at <= :now AND $CONTENT_EXISTS) AS due_now,
            (SELECT COUNT(*) FROM review_records) AS total_reviews
        """,
    )
    abstract fun observeSummary(now: Long): Flow<ProgressSummaryRow>

    @Query("SELECT rating, COUNT(*) AS count FROM review_records GROUP BY rating")
    abstract fun observeRatingCounts(): Flow<List<RatingCountRow>>

    @Query(
        """
        SELECT * FROM review_records WHERE item_type = :type AND item_id = :id
        ORDER BY reviewed_at DESC, id DESC LIMIT :limit
        """,
    )
    abstract suspend fun getHistory(type: ItemType, id: String, limit: Int): List<ReviewRecordEntity>
}
