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
import com.n3sensei.app.data.local.model.ItemRefRow
import com.n3sensei.app.data.local.model.ItemStateRow
import com.n3sensei.app.data.local.model.ProgressSummaryRow
import com.n3sensei.app.data.local.model.RatingCountRow
import com.n3sensei.app.data.local.model.TodayCountsRow
import com.n3sensei.app.data.local.model.TypeCountRow
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
            EXISTS (SELECT 1 FROM item_marks WHERE item_type = :type AND item_id = :id) AS is_marked,
            (SELECT due_at FROM learning_progress WHERE item_type = :type AND item_id = :id) AS due_at,
            (SELECT step_index FROM learning_progress WHERE item_type = :type AND item_id = :id) AS step_index
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

    @Query("SELECT COUNT(*) FROM review_records WHERE request_id = :requestId")
    abstract suspend fun countRecordsWithRequest(requestId: String): Int

    /**
     * Read-compute-write of one answer in a single transaction. Returns false (and writes
     * nothing) when [requestId] was already recorded. The unique index on request_id backs
     * this up if two transactions race.
     */
    @Transaction
    open suspend fun applyReview(
        type: ItemType,
        id: String,
        requestId: String,
        compute: (LearningProgressEntity?) -> Pair<LearningProgressEntity, ReviewRecordEntity>,
    ): Boolean {
        if (countRecordsWithRequest(requestId) > 0) return false
        val (progress, record) = compute(get(type, id))
        insertRecord(record)
        upsert(progress)
        return true
    }

    @Query(
        """
        SELECT p.item_type, p.item_id FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND p.due_at <= :now AND $CONTENT_EXISTS
        ORDER BY p.due_at, p.item_type, p.item_id
        LIMIT :limit
        """,
    )
    abstract suspend fun getDueRefs(now: Long, limit: Int): List<ItemRefRow>

    /** Never-answered items in course order: pack, lesson, then vocabulary, kanji, grammar. */
    @Query(
        """
        SELECT item_type, item_id FROM (
            SELECT 'VOCABULARY' AS item_type, v.id AS item_id, l.pack_id AS pack, l.number AS lesson, 0 AS kind, v.sort_order AS pos
            FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id
            WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'VOCABULARY' AND p.item_id = v.id)
                AND $STUDIED_WORD
            UNION ALL
            SELECT 'KANJI', k.id, l.pack_id, l.number, 1, k.sort_order
            FROM kanji k JOIN lessons l ON l.id = k.lesson_id
            WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'KANJI' AND p.item_id = k.id)
            UNION ALL
            SELECT 'GRAMMAR', g.id, l.pack_id, l.number, 2, g.sort_order
            FROM grammar g JOIN lessons l ON l.id = g.lesson_id
            WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'GRAMMAR' AND p.item_id = g.id)
        )
        ORDER BY pack, lesson, kind, pos
        LIMIT :limit
        """,
    )
    abstract suspend fun getNewRefs(limit: Int): List<ItemRefRow>

    @Query(
        """
        SELECT p.item_type, COUNT(*) AS count FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND p.due_at <= :now AND $CONTENT_EXISTS
        GROUP BY p.item_type
        """,
    )
    abstract fun observeDueByType(now: Long): Flow<List<TypeCountRow>>

    @Query("SELECT COUNT(*) FROM learning_progress p WHERE p.due_at IS NOT NULL AND p.due_at < :dayStart AND $CONTENT_EXISTS")
    abstract fun observeOverdueCount(dayStart: Long): Flow<Int>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN was_new = 1 THEN 1 ELSE 0 END), 0) AS new_answered,
            COALESCE(SUM(CASE WHEN was_new = 0 THEN 1 ELSE 0 END), 0) AS reviews_answered
        FROM review_records WHERE reviewed_at >= :dayStart AND reviewed_at < :dayEnd
        """,
    )
    abstract fun observeTodayCounts(dayStart: Long, dayEnd: Long): Flow<TodayCountsRow>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM vocabulary v WHERE NOT EXISTS
                (SELECT 1 FROM learning_progress p WHERE p.item_type = 'VOCABULARY' AND p.item_id = v.id) AND $STUDIED_WORD)
            + (SELECT COUNT(*) FROM kanji k WHERE NOT EXISTS
                (SELECT 1 FROM learning_progress p WHERE p.item_type = 'KANJI' AND p.item_id = k.id))
            + (SELECT COUNT(*) FROM grammar g WHERE NOT EXISTS
                (SELECT 1 FROM learning_progress p WHERE p.item_type = 'GRAMMAR' AND p.item_id = g.id))
        """,
    )
    abstract fun observeNewAvailable(): Flow<Int>

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
            (SELECT COUNT(*) FROM learning_progress p WHERE p.state = 'MASTERED' AND $CONTENT_EXISTS) AS mastered,
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
