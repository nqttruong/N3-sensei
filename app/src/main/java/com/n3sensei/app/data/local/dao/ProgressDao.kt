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
import com.n3sensei.app.data.local.model.LessonCountRow
import com.n3sensei.app.data.local.model.TypeCountRow
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.flow.Flow

// Matches progress rows whose content item still exists. Item type literals are ItemType names.
private const val CONTENT_EXISTS = """
    ((p.item_type = 'VOCABULARY' AND EXISTS (SELECT 1 FROM vocabulary v WHERE v.id = p.item_id))
    OR (p.item_type = 'KANJI' AND EXISTS (SELECT 1 FROM kanji k WHERE k.id = p.item_id))
    OR (p.item_type = 'GRAMMAR' AND EXISTS (SELECT 1 FROM grammar g WHERE g.id = p.item_id)))
"""

// The item's most recent answer (latest time, then latest row) was "Quên".
private const val LAST_ANSWER_FORGOTTEN = """
    (SELECT r.rating FROM review_records r WHERE r.item_type = p.item_type AND r.item_id = p.item_id
        ORDER BY r.reviewed_at DESC, r.id DESC LIMIT 1) = 'AGAIN'
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

    /** Android sync reads learner events only; it never writes remote state into Room progress. */
    @Query("SELECT * FROM review_records WHERE id > :afterId AND request_id IS NOT NULL ORDER BY id LIMIT :limit")
    abstract suspend fun syncEventsAfter(afterId: Long, limit: Int): List<ReviewRecordEntity>

    /** Learner history not yet sent to JLPT AI Review (the upload queue is this cursor over review_records). */
    @Query("SELECT COUNT(*) FROM review_records WHERE id > :afterId AND request_id IS NOT NULL")
    abstract suspend fun countSyncEventsAfter(afterId: Long): Int

    /** Progress update and its history record succeed or fail together. */
    @Transaction
    open suspend fun recordReview(progress: LearningProgressEntity, record: ReviewRecordEntity): Long {
        upsert(progress)
        return insertRecord(record)
    }

    @Query("SELECT COUNT(*) FROM review_records WHERE request_id = :requestId")
    abstract suspend fun countRecordsWithRequest(requestId: String): Int

    /** Items of [type] introduced (first answered) in [dayStart, dayEnd). */
    @Query("SELECT COUNT(*) FROM review_records WHERE was_new = 1 AND item_type = :type AND reviewed_at >= :dayStart AND reviewed_at < :dayEnd")
    abstract suspend fun countNewOfType(type: ItemType, dayStart: Long, dayEnd: Long): Int

    enum class ApplyResult { APPLIED, DUPLICATE, NEW_LIMIT_REACHED }

    /**
     * Read-compute-write of one answer in a single transaction. Writes nothing when
     * [requestId] was already recorded (the unique index on request_id backs this up if two
     * transactions race), or when the item has no progress yet (a first answer) and its type
     * already introduced [newLimit] items in [dayStart, dayEnd): the count and the write are in
     * the same transaction, which SQLite runs one at a time, so concurrent sessions cannot go
     * over the cap.
     */
    @Transaction
    open suspend fun applyReview(
        type: ItemType,
        id: String,
        requestId: String,
        newLimit: Int?,
        dayStart: Long,
        dayEnd: Long,
        compute: (LearningProgressEntity?) -> Pair<LearningProgressEntity, ReviewRecordEntity>,
    ): ApplyResult {
        if (countRecordsWithRequest(requestId) > 0) return ApplyResult.DUPLICATE
        val current = get(type, id)
        if (current == null && newLimit != null && countNewOfType(type, dayStart, dayEnd) >= newLimit) return ApplyResult.NEW_LIMIT_REACHED
        val (progress, record) = compute(current)
        insertRecord(record)
        upsert(progress)
        return ApplyResult.APPLIED
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

    /** [getDueRefs] for one type: its own schedule, oldest due first. */
    @Query(
        """
        SELECT p.item_type, p.item_id FROM learning_progress p
        WHERE p.item_type = :type AND p.due_at IS NOT NULL AND p.due_at <= :now AND $CONTENT_EXISTS
        ORDER BY p.due_at, p.item_id
        LIMIT :limit
        """,
    )
    abstract suspend fun getDueRefsOfType(now: Long, type: ItemType, limit: Int): List<ItemRefRow>

    /** [getNewRefs] for one type, in course order. */
    @Query(
        """
        SELECT item_type, item_id FROM (
            SELECT 'VOCABULARY' AS item_type, v.id AS item_id, l.pack_id AS pack, l.number AS lesson, v.sort_order AS pos
            FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id
            WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'VOCABULARY' AND p.item_id = v.id)
                AND $STUDIED_WORD
            UNION ALL
            SELECT 'KANJI', k.id, l.pack_id, l.number, k.sort_order
            FROM kanji k JOIN lessons l ON l.id = k.lesson_id
            WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'KANJI' AND p.item_id = k.id)
            UNION ALL
            SELECT 'GRAMMAR', g.id, l.pack_id, l.number, g.sort_order
            FROM grammar g JOIN lessons l ON l.id = g.lesson_id
            WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'GRAMMAR' AND p.item_id = g.id)
        )
        WHERE item_type = :type
        ORDER BY pack, lesson, pos
        LIMIT :limit
        """,
    )
    abstract suspend fun getNewRefsOfType(type: ItemType, limit: Int): List<ItemRefRow>

    /**
     * Items of [type] whose last answer was "Quên" (AGAIN), due or not yet; those due first,
     * the earliest due first.
     */
    @Query(
        """
        SELECT p.item_type, p.item_id FROM learning_progress p
        WHERE p.item_type = :type AND $CONTENT_EXISTS AND $LAST_ANSWER_FORGOTTEN
        ORDER BY p.due_at IS NULL, p.due_at, p.item_id
        LIMIT :limit
        """,
    )
    abstract suspend fun getForgottenRefsOfType(type: ItemType, limit: Int): List<ItemRefRow>

    @Query(
        """
        SELECT p.item_type, COUNT(*) AS count FROM learning_progress p
        WHERE $CONTENT_EXISTS AND $LAST_ANSWER_FORGOTTEN
        GROUP BY p.item_type
        """,
    )
    abstract fun observeForgottenByType(): Flow<List<TypeCountRow>>

    /** [getForgottenRefsOfType] for all types together, in the same order (due ones first, earliest due first). */
    @Query(
        """
        SELECT p.item_type, p.item_id FROM learning_progress p
        WHERE $CONTENT_EXISTS AND $LAST_ANSWER_FORGOTTEN
        ORDER BY p.due_at IS NULL, p.due_at, p.item_type, p.item_id
        LIMIT :limit
        """,
    )
    abstract suspend fun getForgottenRefs(limit: Int): List<ItemRefRow>

    /** Items of each type introduced (first answered) in [dayStart, dayEnd). */
    @Query(
        """
        SELECT item_type, COUNT(*) AS count FROM review_records
        WHERE was_new = 1 AND reviewed_at >= :dayStart AND reviewed_at < :dayEnd
        GROUP BY item_type
        """,
    )
    abstract fun observeNewByTypeBetween(dayStart: Long, dayEnd: Long): Flow<List<TypeCountRow>>

    @Query(
        """
        SELECT p.item_type, COUNT(*) AS count FROM learning_progress p
        WHERE p.due_at IS NOT NULL AND p.due_at < :dayStart AND $CONTENT_EXISTS
        GROUP BY p.item_type
        """,
    )
    abstract fun observeOverdueByType(dayStart: Long): Flow<List<TypeCountRow>>

    /** Distinct items of each type answered in [dayStart, dayEnd) (new or review). */
    @Query(
        """
        SELECT r.item_type, COUNT(DISTINCT r.item_id) AS count FROM review_records r
        WHERE r.reviewed_at >= :dayStart AND r.reviewed_at < :dayEnd
        GROUP BY r.item_type
        """,
    )
    abstract fun observeDoneByType(dayStart: Long, dayEnd: Long): Flow<List<TypeCountRow>>

    /**
     * New kanji (never answered) of these lessons, the lower JLPT level first (N3 before N2),
     * then pack, lesson and position: what a review session introduces when lessons are chosen.
     */
    @Query(
        """
        SELECT 'KANJI' AS item_type, k.id AS item_id FROM kanji k JOIN lessons l ON l.id = k.lesson_id
        WHERE k.lesson_id IN (:lessonIds)
            AND NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'KANJI' AND p.item_id = k.id)
        ORDER BY l.jlpt_level DESC, l.pack_id, l.number, k.sort_order
        LIMIT :limit
        """,
    )
    abstract suspend fun getNewKanjiRefsInLessons(lessonIds: List<String>, limit: Int): List<ItemRefRow>

    /** New kanji (never answered) per lesson; lessons with none are absent. */
    @Query(
        """
        SELECT k.lesson_id AS lesson_id, COUNT(*) AS count FROM kanji k
        WHERE NOT EXISTS (SELECT 1 FROM learning_progress p WHERE p.item_type = 'KANJI' AND p.item_id = k.id)
        GROUP BY k.lesson_id
        """,
    )
    abstract fun observeNewKanjiByLesson(): Flow<List<LessonCountRow>>

    /** [observeNewAvailable] broken down by type. */
    @Query(
        """
        SELECT 'VOCABULARY' AS item_type, COUNT(*) AS count FROM vocabulary v WHERE NOT EXISTS
            (SELECT 1 FROM learning_progress p WHERE p.item_type = 'VOCABULARY' AND p.item_id = v.id) AND $STUDIED_WORD
        UNION ALL
        SELECT 'KANJI', COUNT(*) FROM kanji k WHERE NOT EXISTS
            (SELECT 1 FROM learning_progress p WHERE p.item_type = 'KANJI' AND p.item_id = k.id)
        UNION ALL
        SELECT 'GRAMMAR', COUNT(*) FROM grammar g WHERE NOT EXISTS
            (SELECT 1 FROM learning_progress p WHERE p.item_type = 'GRAMMAR' AND p.item_id = g.id)
        """,
    )
    abstract fun observeNewByType(): Flow<List<TypeCountRow>>


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
