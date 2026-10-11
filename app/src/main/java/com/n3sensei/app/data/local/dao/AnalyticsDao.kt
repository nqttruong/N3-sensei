package com.n3sensei.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.flow.Flow

/**
 * A review record counts only when its item still exists in a non-seed content pack: answers
 * to seed/test data and to items removed by a content update (or a failed import) are left
 * out of every statistic.
 */
private const val VALID_RECORD = """
    ((r.item_type = 'VOCABULARY' AND EXISTS (SELECT 1 FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE v.id = r.item_id AND cp.is_seed = 0))
    OR (r.item_type = 'KANJI' AND EXISTS (SELECT 1 FROM kanji k JOIN lessons l ON l.id = k.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE k.id = r.item_id AND cp.is_seed = 0))
    OR (r.item_type = 'GRAMMAR' AND EXISTS (SELECT 1 FROM grammar g JOIN lessons l ON l.id = g.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE g.id = r.item_id AND cp.is_seed = 0)))
"""

/** Same rule as [VALID_RECORD] for a progress row aliased `p`. */
private const val VALID_PROGRESS = """
    ((p.item_type = 'VOCABULARY' AND EXISTS (SELECT 1 FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE v.id = p.item_id AND cp.is_seed = 0))
    OR (p.item_type = 'KANJI' AND EXISTS (SELECT 1 FROM kanji k JOIN lessons l ON l.id = k.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE k.id = p.item_id AND cp.is_seed = 0))
    OR (p.item_type = 'GRAMMAR' AND EXISTS (SELECT 1 FROM grammar g JOIN lessons l ON l.id = g.lesson_id
        JOIN content_packs cp ON cp.id = l.pack_id WHERE g.id = p.item_id AND cp.is_seed = 0)))
"""

private const val IN_PERIOD = "(:from IS NULL OR r.reviewed_at >= :from) AND r.reviewed_at < :to"

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
    fun observeActivity(from: Long?, to: Long): Flow<ActivityRow>

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
        WHERE cp.is_seed = 0
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
    fun observeTroubleItems(from: Long?, to: Long, limit: Int): Flow<List<TroubleRow>>
}
