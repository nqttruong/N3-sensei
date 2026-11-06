package com.n3sensei.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.flow.Flow

/**
 * Keys (`TYPE:id`) of items that exist in a non-seed content pack. A review record or progress
 * row counts only when its item is in this set: answers to seed/test data and to items removed
 * by a content update (or a failed import) are left out of every statistic.
 *
 * Written as one `IN (subquery)` on purpose: SQLite evaluates the set once, so the period filter
 * on `reviewed_at` keeps using its index. The former per-type `OR … EXISTS` form made SQLite scan
 * the whole history through the `item_type` index, even for "today" (see docs/QA.md).
 */
internal const val VALID_KEYS = """
    SELECT 'VOCABULARY:' || v.id FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE cp.is_seed = 0
    UNION ALL SELECT 'KANJI:' || k.id FROM kanji k JOIN lessons l ON l.id = k.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE cp.is_seed = 0
    UNION ALL SELECT 'GRAMMAR:' || g.id FROM grammar g JOIN lessons l ON l.id = g.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE cp.is_seed = 0
"""

internal const val VALID_RECORD = "(r.item_type || ':' || r.item_id IN ($VALID_KEYS))"

/** Same rule as [VALID_RECORD] for a progress row aliased `p`. */
internal const val VALID_PROGRESS = "(p.item_type || ':' || p.item_id IN ($VALID_KEYS))"

/** [from] is never null: "all time" passes [Long.MIN_VALUE], so the bound stays index-friendly. */
internal const val IN_PERIOD = "r.reviewed_at >= :from AND r.reviewed_at < :to"

private const val BUCKET = "(r.reviewed_at / 900000) * 900000"

data class ActivityRow(
    val answers: Int,
    @ColumnInfo(name = "distinct_items") val distinctItems: Int,
    @ColumnInfo(name = "new_items") val newItems: Int,
    @ColumnInfo(name = "review_answers") val reviewAnswers: Int,
    @ColumnInfo(name = "distinct_reviewed") val distinctReviewed: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
)

data class DueRow(val due: Int, val overdue: Int)

data class BucketRow(
    @ColumnInfo(name = "bucket_start") val bucketStart: Long,
    val answers: Int,
    @ColumnInfo(name = "new_items") val newItems: Int,
)

data class StatusRow(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "new_count") val newCount: Int,
    val learning: Int,
    val review: Int,
    val mastered: Int,
)

data class AnalyticsTypeCountRow(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    val count: Int,
)

data class PriorityRow(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "lapse_count") val lapseCount: Int,
    val label: String?,
)

data class ScheduleRow(
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "due_at") val dueAt: Long,
)

data class TroubleRow(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    val again: Int,
    val hard: Int,
    @ColumnInfo(name = "last_at") val lastAt: Long,
    val label: String?,
)

/** Aggregations for the Statistics screen. Everything is counted in SQL; nothing is loaded in bulk. */
@Dao
interface AnalyticsDao {

    @Query(
        """
        SELECT COUNT(*) AS answers,
            COUNT(DISTINCT r.item_type || ':' || r.item_id) AS distinct_items,
            COALESCE(SUM(r.was_new), 0) AS new_items,
            COALESCE(SUM(CASE WHEN r.was_new = 0 THEN 1 ELSE 0 END), 0) AS review_answers,
            COUNT(DISTINCT CASE WHEN r.was_new = 0 THEN r.item_type || ':' || r.item_id END) AS distinct_reviewed,
            COALESCE(SUM(CASE WHEN r.was_new = 0 AND r.rating = 'AGAIN' THEN 1 ELSE 0 END), 0) AS again,
            COALESCE(SUM(CASE WHEN r.was_new = 0 AND r.rating = 'HARD' THEN 1 ELSE 0 END), 0) AS hard,
            COALESCE(SUM(CASE WHEN r.was_new = 0 AND r.rating = 'GOOD' THEN 1 ELSE 0 END), 0) AS good,
            COALESCE(SUM(CASE WHEN r.was_new = 0 AND r.rating = 'EASY' THEN 1 ELSE 0 END), 0) AS easy
        FROM review_records r
        WHERE $IN_PERIOD AND $VALID_RECORD
        """,
    )
    fun observeActivity(from: Long, to: Long): Flow<ActivityRow>

    /** Items due now, and the part already due before [dayStart] (local midnight). */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN p.due_at <= :now THEN 1 ELSE 0 END), 0) AS due,
            COALESCE(SUM(CASE WHEN p.due_at < :dayStart THEN 1 ELSE 0 END), 0) AS overdue
        FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND $VALID_PROGRESS
        """,
    )
    fun observeDue(now: Long, dayStart: Long): Flow<DueRow>

    @Query(
        """
        SELECT $BUCKET AS bucket_start, COUNT(*) AS answers, COALESCE(SUM(r.was_new), 0) AS new_items
        FROM review_records r
        WHERE r.reviewed_at >= :from AND r.reviewed_at < :to AND $VALID_RECORD
        GROUP BY bucket_start ORDER BY bucket_start
        """,
    )
    fun observeBuckets(from: Long, to: Long): Flow<List<BucketRow>>

    /** Scheduled due times within [from, to), in 15-minute blocks (for the forecast). */
    @Query(
        """
        SELECT (p.due_at / 900000) * 900000 AS bucket_start, COUNT(*) AS answers, 0 AS new_items
        FROM learning_progress p
        WHERE p.due_at >= :from AND p.due_at < :to AND $VALID_PROGRESS
        GROUP BY bucket_start ORDER BY bucket_start
        """,
    )
    fun observeDueBuckets(from: Long, to: Long): Flow<List<BucketRow>>

    @Query(
        """
        SELECT r.item_type, COUNT(*) AS count FROM review_records r
        WHERE $IN_PERIOD AND $VALID_RECORD
        GROUP BY r.item_type
        """,
    )
    fun observeAnswersByType(from: Long, to: Long): Flow<List<AnalyticsTypeCountRow>>

    /**
     * Due items to review first: overdue before due, the longest-waiting first, then the most
     * often forgotten.
     */
    @Query(
        """
        SELECT p.item_type, p.item_id, p.due_at, p.lapse_count,
            COALESCE(
                (SELECT expression FROM vocabulary WHERE p.item_type = 'VOCABULARY' AND id = p.item_id),
                (SELECT character FROM kanji WHERE p.item_type = 'KANJI' AND id = p.item_id),
                (SELECT pattern FROM grammar WHERE p.item_type = 'GRAMMAR' AND id = p.item_id)
            ) AS label
        FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND p.due_at <= :now AND $VALID_PROGRESS
        ORDER BY p.due_at ASC, p.lapse_count DESC, p.item_id
        LIMIT :limit
        """,
    )
    fun observePriorityItems(now: Long, limit: Int): Flow<List<PriorityRow>>

    /** Step and due time of every scheduled item (bounded by the content size, two columns). */
    @Query("SELECT p.step_index, p.due_at FROM learning_progress p WHERE p.due_at IS NOT NULL AND $VALID_PROGRESS")
    fun observeSchedule(): Flow<List<ScheduleRow>>

    @Query("SELECT DISTINCT $BUCKET AS bucket_start FROM review_records r WHERE $VALID_RECORD ORDER BY bucket_start")
    fun observeStudyBuckets(): Flow<List<Long>>

    @Query(
        """
        SELECT 'VOCABULARY' AS item_type,
            COALESCE(SUM(CASE WHEN p.item_id IS NULL THEN 1 ELSE 0 END), 0) AS new_count,
            COALESCE(SUM(CASE WHEN p.state = 'LEARNING' THEN 1 ELSE 0 END), 0) AS learning,
            COALESCE(SUM(CASE WHEN p.state = 'REVIEW' THEN 1 ELSE 0 END), 0) AS review,
            COALESCE(SUM(CASE WHEN p.state = 'MASTERED' THEN 1 ELSE 0 END), 0) AS mastered
        FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id JOIN content_packs cp ON cp.id = l.pack_id
        LEFT JOIN learning_progress p ON p.item_type = 'VOCABULARY' AND p.item_id = v.id
        WHERE cp.is_seed = 0 AND $STUDIED_WORD
        UNION ALL
        SELECT 'KANJI',
            COALESCE(SUM(CASE WHEN p.item_id IS NULL THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN p.state = 'LEARNING' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN p.state = 'REVIEW' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN p.state = 'MASTERED' THEN 1 ELSE 0 END), 0)
        FROM kanji k JOIN lessons l ON l.id = k.lesson_id JOIN content_packs cp ON cp.id = l.pack_id
        LEFT JOIN learning_progress p ON p.item_type = 'KANJI' AND p.item_id = k.id
        WHERE cp.is_seed = 0
        UNION ALL
        SELECT 'GRAMMAR',
            COALESCE(SUM(CASE WHEN p.item_id IS NULL THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN p.state = 'LEARNING' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN p.state = 'REVIEW' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN p.state = 'MASTERED' THEN 1 ELSE 0 END), 0)
        FROM grammar g JOIN lessons l ON l.id = g.lesson_id JOIN content_packs cp ON cp.id = l.pack_id
        LEFT JOIN learning_progress p ON p.item_type = 'GRAMMAR' AND p.item_id = g.id
        WHERE cp.is_seed = 0
        """,
    )
    fun observeStatusByType(): Flow<List<StatusRow>>

    @Query(
        """
        SELECT r.item_type, r.item_id,
            SUM(CASE WHEN r.rating = 'AGAIN' THEN 1 ELSE 0 END) AS again,
            SUM(CASE WHEN r.rating = 'HARD' THEN 1 ELSE 0 END) AS hard,
            MAX(r.reviewed_at) AS last_at,
            COALESCE(
                (SELECT expression FROM vocabulary WHERE r.item_type = 'VOCABULARY' AND id = r.item_id),
                (SELECT character FROM kanji WHERE r.item_type = 'KANJI' AND id = r.item_id),
                (SELECT pattern FROM grammar WHERE r.item_type = 'GRAMMAR' AND id = r.item_id)
            ) AS label
        FROM review_records r
        WHERE r.rating IN ('AGAIN', 'HARD') AND $IN_PERIOD AND $VALID_RECORD
        GROUP BY r.item_type, r.item_id
        ORDER BY (again + hard) DESC, again DESC, last_at DESC, r.item_id
        LIMIT :limit
        """,
    )
    fun observeTroubleItems(from: Long, to: Long, limit: Int): Flow<List<TroubleRow>>
}
