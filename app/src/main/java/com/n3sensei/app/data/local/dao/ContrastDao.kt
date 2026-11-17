package com.n3sensei.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.ContrastAttemptEntity
import com.n3sensei.app.data.local.entity.ContrastExampleEntity
import com.n3sensei.app.data.local.entity.ContrastGroupEntity
import com.n3sensei.app.data.local.entity.ContrastItemEntity
import com.n3sensei.app.data.local.entity.ContrastNoteEntity
import com.n3sensei.app.data.local.entity.ContrastOptionEntity
import com.n3sensei.app.data.local.entity.ContrastPackEntity
import com.n3sensei.app.data.local.entity.ContrastProgressEntity
import com.n3sensei.app.data.local.entity.ContrastQuestionEntity
import com.n3sensei.app.data.local.entity.ContrastRetiredGroupEntity
import com.n3sensei.app.data.local.entity.ContrastReviewEntity
import com.n3sensei.app.data.local.entity.ContrastSessionEntity
import com.n3sensei.app.data.local.entity.ContrastSessionItemEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.RecordAnswerResult
import kotlinx.coroutines.flow.Flow

/** A group with its items and question ids (for list rows: labels and question count). */
data class ContrastGroupWithItems(
    @Embedded val group: ContrastGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id")
    val items: List<ContrastItemEntity>,
    @Relation(entity = ContrastQuestionEntity::class, parentColumn = "id", entityColumn = "group_id", projection = ["id"])
    val questionIds: List<String>,
)

/** M7 aggregates over contrast_attempts (no schema change: plain queries on the v16 tables). */
/** A published group found by source ids (entry points from lessons and item pages). */
data class ContrastGroupLinkRow(
    val id: String,
    val title: String,
    @ColumnInfo(name = "content_type") val contentType: ContrastContentType,
)

data class ContrastOutcomeRow(
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
)

data class ContrastGroupOutcomeRow(
    @ColumnInfo(name = "group_id") val groupId: String,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    @ColumnInfo(name = "last_wrong_at") val lastWrongAt: Long?,
)

data class ContrastTypeOutcomeRow(
    @ColumnInfo(name = "question_type") val questionType: ContrastQuestionType,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
)

data class ContrastDirectionRow(
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "expected_item_id") val expectedItemId: String,
    @ColumnInfo(name = "chosen_item_id") val chosenItemId: String,
    val count: Int,
    val sessions: Int,
    @ColumnInfo(name = "last_at") val lastAt: Long,
    @ColumnInfo(name = "sessions_since") val sessionsSince: Int,
)

data class ContrastSessionOutcomeRow(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "first_at") val firstAt: Long,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
)

data class ContrastLastAnswerRow(
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
)

data class ContrastOverviewRow(
    @ColumnInfo(name = "due_count") val dueCount: Int,
    @ColumnInfo(name = "scheduled_count") val scheduledCount: Int,
    @ColumnInfo(name = "next_due_at") val nextDueAt: Long?,
)

/** The rows of one installed pack, written together by [ContrastDao.replacePack]. */
data class ContrastPackRows(
    val pack: ContrastPackEntity,
    val groups: List<ContrastGroupEntity>,
    val items: List<ContrastItemEntity>,
    val notes: List<ContrastNoteEntity>,
    val examples: List<ContrastExampleEntity>,
    val questions: List<ContrastQuestionEntity>,
    val options: List<ContrastOptionEntity>,
    val retiredGroups: List<ContrastRetiredGroupEntity>,
)

/**
 * Contrast Lab tables (v16). Content is replaced pack by pack; learner tables have no foreign key
 * and are never deleted by content changes. "Published" groups are the ones in contrast_groups:
 * the importer (M4) only writes PUBLISHED content.
 */
@Dao
abstract class ContrastDao {

    // ---- Content: write (used by the importer in M4) ----

    @Query("DELETE FROM contrast_packs WHERE id = :packId")
    protected abstract suspend fun deletePack(packId: String)

    /** Content of other packs with the same group ids would collide: a group belongs to one pack. */
    @Query("DELETE FROM contrast_groups WHERE id IN (:groupIds)")
    protected abstract suspend fun deleteGroups(groupIds: List<String>)

    @Insert protected abstract suspend fun insertPack(pack: ContrastPackEntity)
    @Insert protected abstract suspend fun insertGroups(rows: List<ContrastGroupEntity>)
    @Insert protected abstract suspend fun insertItems(rows: List<ContrastItemEntity>)
    @Insert protected abstract suspend fun insertNotes(rows: List<ContrastNoteEntity>)
    @Insert protected abstract suspend fun insertExamples(rows: List<ContrastExampleEntity>)
    @Insert protected abstract suspend fun insertQuestions(rows: List<ContrastQuestionEntity>)
    @Insert protected abstract suspend fun insertOptions(rows: List<ContrastOptionEntity>)
    @Insert protected abstract suspend fun insertRetired(rows: List<ContrastRetiredGroupEntity>)

    /**
     * Replaces the content of [rows].pack in one transaction: the old content rows go (CASCADE
     * inside the content tables only), the new ones come in. Learner tables are not touched.
     * Any failure rolls the whole replacement back, so the old content stays.
     */
    @Transaction
    open suspend fun replacePack(rows: ContrastPackRows) {
        deletePack(rows.pack.id)
        deleteGroups(rows.groups.map { it.id })
        insertPack(rows.pack)
        insertGroups(rows.groups)
        insertItems(rows.items)
        insertNotes(rows.notes)
        insertExamples(rows.examples)
        insertQuestions(rows.questions)
        insertOptions(rows.options)
        insertRetired(rows.retiredGroups)
    }

    // ---- Content: read ----

    @Transaction
    @Query(
        """
        SELECT g.* FROM contrast_groups g JOIN contrast_packs p ON p.id = g.pack_id
        WHERE (:type IS NULL OR g.content_type = :type)
          AND (:level IS NULL OR g.jlpt_level = :level)
          AND (:pattern IS NULL OR g.search_text LIKE :pattern ESCAPE '\')
        ORDER BY p.id, g.sort_order
        """,
    )
    abstract fun observeGroups(type: ContrastContentType?, level: String?, pattern: String?): Flow<List<ContrastGroupWithItems>>

    /** Installed (published) groups with an item pointing to one of the given source rows; read-only. */
    @Query(
        """
        SELECT DISTINCT g.id, g.title, g.content_type FROM contrast_groups g
        JOIN contrast_packs p ON p.id = g.pack_id
        JOIN contrast_items i ON i.group_id = g.id
        WHERE (i.source_type = 'GRAMMAR' AND i.source_content_id IN (:grammar))
           OR (i.source_type = 'VOCABULARY' AND i.source_content_id IN (:vocabulary))
           OR (i.source_type = 'KANJI' AND i.source_content_id IN (:kanji))
        ORDER BY p.id, g.sort_order
        """,
    )
    abstract fun observeGroupsForSources(grammar: List<String>, vocabulary: List<String>, kanji: List<String>): Flow<List<ContrastGroupLinkRow>>

    @Transaction
    @Query("SELECT * FROM contrast_groups WHERE id = :groupId")
    abstract suspend fun groupWithItems(groupId: String): ContrastGroupWithItems?

    @Query("SELECT * FROM contrast_packs WHERE id = :packId")
    abstract suspend fun pack(packId: String): ContrastPackEntity?

    @Query("SELECT * FROM contrast_packs ORDER BY installed_at DESC, id LIMIT 1")
    abstract suspend fun installedPack(): ContrastPackEntity?

    @Query("SELECT id FROM contrast_groups WHERE pack_id = :packId ORDER BY sort_order")
    abstract suspend fun groupIds(packId: String): List<String>

    @Query("SELECT * FROM contrast_notes WHERE group_id = :groupId ORDER BY kind, sort_order")
    abstract suspend fun notes(groupId: String): List<ContrastNoteEntity>

    @Query("SELECT * FROM contrast_examples WHERE group_id = :groupId ORDER BY sort_order")
    abstract suspend fun examples(groupId: String): List<ContrastExampleEntity>

    @Query("SELECT * FROM contrast_questions WHERE group_id = :groupId ORDER BY sort_order")
    abstract suspend fun questions(groupId: String): List<ContrastQuestionEntity>

    @Query("SELECT * FROM contrast_questions WHERE id = :questionId")
    abstract suspend fun question(questionId: String): ContrastQuestionEntity?

    @Query("SELECT * FROM contrast_options WHERE question_id IN (:questionIds) ORDER BY question_id, sort_order")
    abstract suspend fun options(questionIds: List<String>): List<ContrastOptionEntity>

    @Query("SELECT * FROM contrast_retired_groups WHERE group_id = :groupId ORDER BY retired_in_version DESC LIMIT 1")
    abstract suspend fun retiredGroup(groupId: String): ContrastRetiredGroupEntity?

    // ---- Source rows (read-only) ----
    // The content tables hold the MERGED values (pack value with the learner's edit applied, see
    // docs/USER_CONTENT.md); the pack's own value of an edited field is user_content_edits.base_value.
    // Source fingerprints are computed on the pack values: ContentEdits.base(row, edits).

    @Query("SELECT * FROM grammar WHERE id IN (:ids)")
    abstract suspend fun grammarRows(ids: List<String>): List<GrammarEntity>

    @Query("SELECT * FROM vocabulary WHERE id IN (:ids)")
    abstract suspend fun vocabularyRows(ids: List<String>): List<VocabularyEntity>

    @Query("SELECT * FROM kanji WHERE id IN (:ids)")
    abstract suspend fun kanjiRows(ids: List<String>): List<KanjiEntity>

    @Query("SELECT * FROM user_content_edits WHERE item_type = :type AND item_id IN (:ids)")
    abstract suspend fun edits(type: ItemType, ids: List<String>): List<UserContentEditEntity>

    /**
     * Candidates for a snapshot when the referenced id is gone (exact field match on the pack values is
     * checked in Kotlin). An edited pattern / reading is matched on its pack value (base_value) too.
     */
    @Query(
        """
        SELECT * FROM grammar WHERE pattern = :pattern
           OR id IN (SELECT item_id FROM user_content_edits WHERE item_type = 'GRAMMAR' AND field = 'pattern' AND base_value = :pattern)
        """,
    )
    abstract suspend fun grammarByPattern(pattern: String): List<GrammarEntity>

    @Query(
        """
        SELECT * FROM vocabulary WHERE expression = :expression AND (reading = :reading
           OR id IN (SELECT item_id FROM user_content_edits WHERE item_type = 'VOCABULARY' AND field = 'reading' AND base_value = :reading))
        """,
    )
    abstract suspend fun vocabularyByExpression(expression: String, reading: String): List<VocabularyEntity>

    @Query("SELECT * FROM kanji WHERE character = :character")
    abstract suspend fun kanjiByCharacter(character: String): List<KanjiEntity>

    // ---- Sessions ----

    @Query("SELECT * FROM contrast_sessions WHERE status = 'ACTIVE' ORDER BY last_activity_at DESC LIMIT 1")
    abstract suspend fun activeSession(): ContrastSessionEntity?

    @Query("SELECT * FROM contrast_sessions WHERE id = :sessionId")
    abstract suspend fun session(sessionId: String): ContrastSessionEntity?

    @Query("SELECT * FROM contrast_session_items WHERE session_id = :sessionId ORDER BY position")
    abstract suspend fun sessionItems(sessionId: String): List<ContrastSessionItemEntity>

    @Query("UPDATE contrast_sessions SET status = 'ABANDONED' WHERE status = 'ACTIVE'")
    protected abstract suspend fun abandonActiveSessions()

    @Insert protected abstract suspend fun insertSession(session: ContrastSessionEntity)
    @Insert protected abstract suspend fun insertSessionItems(items: List<ContrastSessionItemEntity>)

    @Query("UPDATE contrast_sessions SET status = :status, last_activity_at = :now WHERE id = :sessionId")
    abstract suspend fun finishSession(sessionId: String, status: SessionStatus, now: Long)

    @Query("UPDATE contrast_sessions SET last_activity_at = :now WHERE id = :sessionId")
    protected abstract suspend fun touchSession(sessionId: String, now: Long)

    /** A new ACTIVE session; the ACTIVE one (if any) becomes ABANDONED in the same transaction. */
    @Transaction
    open suspend fun startSession(session: ContrastSessionEntity, items: List<ContrastSessionItemEntity>) {
        abandonActiveSessions()
        insertSession(session)
        insertSessionItems(items)
    }

    @Transaction
    open suspend fun appendSessionItems(sessionId: String, items: List<ContrastSessionItemEntity>, now: Long) {
        insertSessionItems(items)
        touchSession(sessionId, now)
    }

    // ---- Attempts ----

    @Query("SELECT * FROM contrast_attempts WHERE request_id = :requestId")
    abstract suspend fun attempt(requestId: String): ContrastAttemptEntity?

    @Query("SELECT * FROM contrast_attempts WHERE session_id = :sessionId AND position = :position")
    protected abstract suspend fun attemptAt(sessionId: String, position: Int): ContrastAttemptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAttempt(attempt: ContrastAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertReview(review: ContrastReviewEntity): Long

    @Upsert
    protected abstract suspend fun upsertProgress(progress: ContrastProgressEntity)

    /**
     * One answer and, when it completes a block, its review and the group's new progress, in ONE
     * transaction. A repeated request id writes nothing (DUPLICATE); another answer to the same
     * position writes nothing (ALREADY_ANSWERED). The unique (session_id, group_id) of reviews
     * makes a second scheduling of the same block fail, rolling the whole answer back.
     */
    @Transaction
    open suspend fun recordAnswer(
        attempt: ContrastAttemptEntity,
        review: ContrastReviewEntity?,
        progress: ContrastProgressEntity?,
    ): RecordAnswerResult {
        if (attempt(attempt.requestId) != null) return RecordAnswerResult.DUPLICATE
        if (attemptAt(attempt.sessionId, attempt.position) != null) return RecordAnswerResult.ALREADY_ANSWERED
        insertAttempt(attempt)
        if (review != null) {
            insertReview(review)
            upsertProgress(requireNotNull(progress) { "A review comes with its progress" })
        }
        touchSession(attempt.sessionId, attempt.answeredAt)
        return RecordAnswerResult.RECORDED
    }

    @Query("SELECT * FROM contrast_attempts WHERE session_id = :sessionId ORDER BY position")
    abstract suspend fun attemptsOfSession(sessionId: String): List<ContrastAttemptEntity>

    @Query("SELECT question_id, MAX(answered_at) AS answered_at FROM contrast_attempts WHERE group_id = :groupId GROUP BY question_id")
    abstract suspend fun lastAnswers(groupId: String): List<ContrastLastAnswerRow>

    @Query(
        """
        SELECT * FROM contrast_attempts
        WHERE (:from IS NULL OR answered_at >= :from) AND (:to IS NULL OR answered_at < :to)
        ORDER BY answered_at, session_id, position
        """,
    )
    abstract suspend fun attemptsBetween(from: Long?, to: Long?): List<ContrastAttemptEntity>

    @Query("SELECT * FROM contrast_attempts WHERE group_id = :groupId ORDER BY answered_at, session_id, position")
    abstract suspend fun attemptsOfGroup(groupId: String): List<ContrastAttemptEntity>

    // ---- Progress and reviews ----

    @Query("SELECT * FROM contrast_progress WHERE group_id = :groupId")
    abstract suspend fun progress(groupId: String): ContrastProgressEntity?

    @Query("SELECT * FROM contrast_progress ORDER BY group_id")
    abstract suspend fun allProgress(): List<ContrastProgressEntity>

    /** Published groups due at [now], most overdue first; retired or removed groups are never due. */
    @Query(
        """
        SELECT pr.group_id FROM contrast_progress pr JOIN contrast_groups g ON g.id = pr.group_id
        WHERE pr.due_at <= :now ORDER BY pr.due_at, g.sort_order LIMIT :limit
        """,
    )
    abstract suspend fun dueGroups(now: Long, limit: Int): List<String>

    /** Published groups never scheduled, in pack order. */
    @Query(
        """
        SELECT g.id FROM contrast_groups g JOIN contrast_packs p ON p.id = g.pack_id
        LEFT JOIN contrast_progress pr ON pr.group_id = g.id
        WHERE pr.group_id IS NULL
          AND (:type IS NULL OR g.content_type = :type)
          AND (:level IS NULL OR g.jlpt_level = :level)
          AND (:pattern IS NULL OR g.search_text LIKE :pattern ESCAPE '\')
        ORDER BY p.id, g.sort_order LIMIT :limit
        """,
    )
    abstract suspend fun newGroups(limit: Int, type: ContrastContentType?, level: String?, pattern: String?): List<String>

    /** Groups whose first review is in [start, end): the new groups introduced in that window (reviews don't count). */
    @Query(
        """
        SELECT COUNT(*) FROM (SELECT group_id, MIN(reviewed_at) AS first_at FROM contrast_reviews GROUP BY group_id)
        WHERE first_at >= :start AND first_at < :end
        """,
    )
    abstract suspend fun groupsFirstReviewedBetween(start: Long, end: Long): Int

    @Query("SELECT * FROM contrast_reviews WHERE group_id = :groupId ORDER BY reviewed_at, id")
    abstract suspend fun reviews(groupId: String): List<ContrastReviewEntity>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM contrast_progress pr JOIN contrast_groups g ON g.id = pr.group_id WHERE pr.due_at <= :now) AS due_count,
          (SELECT COUNT(*) FROM contrast_progress pr JOIN contrast_groups g ON g.id = pr.group_id) AS scheduled_count,
          (SELECT MIN(pr.due_at) FROM contrast_progress pr JOIN contrast_groups g ON g.id = pr.group_id WHERE pr.due_at > :now) AS next_due_at
        """,
    )
    abstract fun observeOverview(now: Long): Flow<ContrastOverviewRow>

    // ---- M7 analytics: aggregates computed by SQLite ----

    @Query("SELECT COUNT(*) FROM contrast_attempts")
    abstract fun observeAttemptCount(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(a.outcome = 'CORRECT'), 0) AS correct, COALESCE(SUM(a.outcome = 'INCORRECT'), 0) AS incorrect,
               COALESCE(SUM(a.outcome = 'SKIPPED'), 0) AS skipped
        FROM contrast_attempts a
        WHERE (:groupId IS NULL OR a.group_id = :groupId)
          AND (:questionType IS NULL OR a.question_type = :questionType)
          AND (:contentType IS NULL OR a.group_id IN (SELECT id FROM contrast_groups WHERE content_type = :contentType))
        """,
    )
    abstract suspend fun outcomeTotals(groupId: String?, questionType: ContrastQuestionType?, contentType: ContrastContentType?): ContrastOutcomeRow

    @Query(
        """
        SELECT a.group_id, SUM(a.outcome = 'CORRECT') AS correct, SUM(a.outcome = 'INCORRECT') AS incorrect, SUM(a.outcome = 'SKIPPED') AS skipped,
               MAX(CASE WHEN a.outcome = 'INCORRECT' THEN a.answered_at END) AS last_wrong_at
        FROM contrast_attempts a
        WHERE (:groupId IS NULL OR a.group_id = :groupId)
          AND (:questionType IS NULL OR a.question_type = :questionType)
          AND (:contentType IS NULL OR a.group_id IN (SELECT id FROM contrast_groups WHERE content_type = :contentType))
        GROUP BY a.group_id ORDER BY incorrect DESC, a.group_id
        """,
    )
    abstract suspend fun outcomesByGroup(groupId: String?, questionType: ContrastQuestionType?, contentType: ContrastContentType?): List<ContrastGroupOutcomeRow>

    @Query(
        """
        SELECT a.question_type, SUM(a.outcome = 'CORRECT') AS correct, SUM(a.outcome = 'INCORRECT') AS incorrect, SUM(a.outcome = 'SKIPPED') AS skipped
        FROM contrast_attempts a
        WHERE (:groupId IS NULL OR a.group_id = :groupId)
          AND (:questionType IS NULL OR a.question_type = :questionType)
          AND (:contentType IS NULL OR a.group_id IN (SELECT id FROM contrast_groups WHERE content_type = :contentType))
        GROUP BY a.question_type ORDER BY incorrect DESC, a.question_type
        """,
    )
    abstract suspend fun outcomesByQuestionType(groupId: String?, questionType: ContrastQuestionType?, contentType: ContrastContentType?): List<ContrastTypeOutcomeRow>

    /**
     * Directed confusions: same rule as ContrastAttempt.confusion (INCORRECT, both item ids known
     * and different). sessions_since = later sessions of the group with a non-skipped answer,
     * excluding the session of the last occurrence.
     */
    @Query(
        """
        WITH d AS (
          SELECT a.group_id, a.expected_item_id, a.chosen_item_id, COUNT(*) AS count,
                 COUNT(DISTINCT a.session_id) AS sessions, MAX(a.answered_at) AS last_at
          FROM contrast_attempts a
          WHERE a.outcome = 'INCORRECT' AND a.expected_item_id IS NOT NULL AND a.chosen_item_id IS NOT NULL
            AND a.expected_item_id != a.chosen_item_id
            AND (:groupId IS NULL OR a.group_id = :groupId)
          AND (:questionType IS NULL OR a.question_type = :questionType)
          AND (:contentType IS NULL OR a.group_id IN (SELECT id FROM contrast_groups WHERE content_type = :contentType))
          GROUP BY a.group_id, a.expected_item_id, a.chosen_item_id
        )
        SELECT d.group_id, d.expected_item_id, d.chosen_item_id, d.count, d.sessions, d.last_at,
          (SELECT COUNT(DISTINCT b.session_id) FROM contrast_attempts b
           WHERE b.group_id = d.group_id AND b.answered_at > d.last_at AND b.outcome != 'SKIPPED'
             AND b.session_id NOT IN (
               SELECT c.session_id FROM contrast_attempts c
               WHERE c.group_id = d.group_id AND c.answered_at = d.last_at AND c.outcome = 'INCORRECT'
                 AND c.expected_item_id = d.expected_item_id AND c.chosen_item_id = d.chosen_item_id)
          ) AS sessions_since
        FROM d ORDER BY d.count DESC, d.last_at DESC, d.group_id, d.expected_item_id, d.chosen_item_id
        """,
    )
    abstract suspend fun confusionDirections(groupId: String?, questionType: ContrastQuestionType?, contentType: ContrastContentType?): List<ContrastDirectionRow>

    @Query(
        """
        SELECT a.session_id, MIN(a.answered_at) AS first_at, SUM(a.outcome = 'CORRECT') AS correct, SUM(a.outcome = 'INCORRECT') AS incorrect, SUM(a.outcome = 'SKIPPED') AS skipped
        FROM contrast_attempts a
        WHERE (:groupId IS NULL OR a.group_id = :groupId)
          AND (:questionType IS NULL OR a.question_type = :questionType)
          AND (:contentType IS NULL OR a.group_id IN (SELECT id FROM contrast_groups WHERE content_type = :contentType))
        GROUP BY a.session_id ORDER BY first_at DESC, a.session_id LIMIT :limit
        """,
    )
    abstract suspend fun recentSessionOutcomes(groupId: String?, questionType: ContrastQuestionType?, contentType: ContrastContentType?, limit: Int): List<ContrastSessionOutcomeRow>

    @Query(
        """
        SELECT * FROM contrast_attempts
        WHERE group_id = :groupId AND outcome = 'INCORRECT' AND expected_item_id = :expected AND chosen_item_id = :chosen
        ORDER BY answered_at DESC, session_id DESC, position DESC LIMIT 1
        """,
    )
    abstract suspend fun latestConfusion(groupId: String, expected: String, chosen: String): ContrastAttemptEntity?

    /** The group of the most recent answer among the installed (published) groups; retired groups are skipped. */
    @Query(
        """
        SELECT group_id FROM contrast_attempts WHERE group_id IN (SELECT id FROM contrast_groups)
        ORDER BY answered_at DESC, session_id DESC, position DESC LIMIT 1
        """,
    )
    abstract suspend fun lastAnsweredGroup(): String?

    @Query(
        """
        SELECT a.* FROM contrast_attempts a
        WHERE a.outcome = 'INCORRECT' AND (:groupId IS NULL OR a.group_id = :groupId)
          AND (:questionType IS NULL OR a.question_type = :questionType)
          AND (:contentType IS NULL OR a.group_id IN (SELECT id FROM contrast_groups WHERE content_type = :contentType))
        ORDER BY a.answered_at DESC, a.session_id DESC, a.position DESC LIMIT :limit
        """,
    )
    abstract suspend fun recentIncorrect(groupId: String?, questionType: ContrastQuestionType?, contentType: ContrastContentType?, limit: Int): List<ContrastAttemptEntity>
}
