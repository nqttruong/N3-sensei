package com.n3sensei.app.performance

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.net.Uri
import com.n3sensei.app.data.backup.AndroidBackupRepository
import com.n3sensei.app.data.local.dao.IN_PERIOD
import com.n3sensei.app.data.local.dao.VALID_RECORD
import com.n3sensei.app.domain.backup.BackupSection
import com.n3sensei.app.domain.backup.InspectResult
import com.n3sensei.app.testing.InMemoryPreferencesRepository
import com.n3sensei.app.testing.RecordingReminderScheduler
import org.junit.Assert.assertEquals
import java.io.File
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.model.ContentPackBundle
import com.n3sensei.app.data.repository.OfflineAnalyticsRepository
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.analytics.StatsPeriod
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * TEST DATA ONLY: a database far larger than the sample content, to measure the queries the
 * screens run and to check that heavy queries use an index. Timings are logged with tag
 * `N3Perf`; the assertions are generous ceilings that only catch gross regressions.
 *
 * Size: 50 lessons, 3 000 vocabulary, 1 000 kanji, 300 grammar; progress for 3 000 items;
 * 300 000 answers spread over two years (≈ 410 per day).
 */
@RunWith(AndroidJUnit4::class)
class LargeDataPerformanceTest {

    companion object {
        private const val TAG = "N3Perf"
        private const val DB_NAME = "perf-test.db"
        private const val RESTORE_DB = "perf-restore.db"
        private const val LESSONS = 50
        private const val VOCAB_PER_LESSON = 60
        private const val KANJI_PER_LESSON = 20
        private const val GRAMMAR_PER_LESSON = 6
        private const val ANSWERS = 300_000
        private const val DAY = 86_400_000L
        private val now: Instant = Instant.parse("2026-10-12T05:00:00Z")
        private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        private lateinit var db: N3Database

        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

        @JvmStatic
        @BeforeClass
        fun createLargeDatabase(): Unit = runBlocking {
            context.deleteDatabase(DB_NAME)
            db = Room.databaseBuilder(context, N3Database::class.java, DB_NAME).build()
            val started = System.nanoTime()
            db.contentPackDao().replacePack(bundle())
            val sql = db.openHelper.writableDatabase
            val random = Random(42)
            val vocabIds = (1..LESSONS).flatMap { l -> (1..VOCAB_PER_LESSON).map { "big-l$l-v$it" } }
            sql.beginTransaction()
            try {
                vocabIds.forEachIndexed { i, id ->
                    val due = now.toEpochMilli() + (i % 70 - 10) * DAY
                    sql.execSQL(
                        "INSERT INTO learning_progress VALUES ('VOCABULARY', ?, 'REVIEW', 3, 2880, ?, ?, 10, 1, 0, ?)",
                        arrayOf<Any>(id, due, due - 2 * DAY, due - 2 * DAY),
                    )
                }
                val seen = HashSet<String>()
                val insert = sql.compileStatement(
                    "INSERT INTO review_records (item_type, item_id, rating, reviewed_at, interval_before_minutes, " +
                        "interval_after_minutes, due_at_after, request_id, was_new) VALUES ('VOCABULARY', ?, ?, ?, 60, 1440, ?, ?, ?)",
                )
                val start = now.toEpochMilli() - 730 * DAY
                for (n in 0 until ANSWERS) {
                    val at = start + n.toLong() * (730 * DAY / ANSWERS)
                    val id = vocabIds[random.nextInt(vocabIds.size)]
                    insert.bindString(1, id)
                    insert.bindString(2, listOf("AGAIN", "HARD", "GOOD", "GOOD", "EASY")[random.nextInt(5)])
                    insert.bindLong(3, at)
                    insert.bindLong(4, at + DAY)
                    insert.bindString(5, "perf-$n")
                    insert.bindLong(6, if (seen.add(id)) 1 else 0)
                    insert.executeInsert()
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
            Log.i(TAG, "setup: ${(System.nanoTime() - started) / 1_000_000} ms")
        }

        @JvmStatic
        @AfterClass
        fun deleteLargeDatabase() {
            db.close()
            // `-e keepDb true` keeps the file for analysis with sqlite3 (adb pull).
            if (InstrumentationRegistry.getArguments().getString("keepDb") != "true") context.deleteDatabase(DB_NAME)
        }

        private fun bundle(): ContentPackBundle {
            val source = ContentSourceEntity("perf-source", "Dữ liệu hiệu năng (test)", null, "Test only", null, false)
            val lessons = (1..LESSONS).map { LessonEntity("big-l$it", "perf", it, "Bài $it", "N3", null) }
            var kanjiCode = 0x4E00
            return ContentPackBundle(
                sources = listOf(source),
                pack = ContentPackEntity("perf", "Gói hiệu năng", 1, "perf-source", isSeed = false, importedAt = 0),
                lessons = lessons,
                vocabulary = lessons.flatMap { l ->
                    (1..VOCAB_PER_LESSON).map {
                        VocabularyEntity("${l.id}-v$it", l.id, "perf-source", "語${l.number}の$it", "ご", null, "nghĩa thứ $it của bài ${l.number}", null, "N3", it, null)
                    }
                },
                kanji = lessons.flatMap { l ->
                    (1..KANJI_PER_LESSON).map {
                        KanjiEntity("${l.id}-k$it", l.id, "perf-source", String(Character.toChars(kanjiCode++)), listOf("オン"), listOf("くん"), "HÁN", "nghĩa kanji $it", 10, it, null)
                    }
                },
                grammar = lessons.flatMap { l ->
                    (1..GRAMMAR_PER_LESSON).map {
                        GrammarEntity("${l.id}-g$it", l.id, "perf-source", "〜文型${l.number}-$it", "V + 〜", "ý nghĩa $it", "cách dùng", null, it)
                    }
                },
            )
        }
    }

    private fun <T> timed(label: String, ceilingMs: Long = 2_000, block: suspend () -> T): T = runBlocking {
        block() // warm up: first run also compiles the statement
        val started = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i(TAG, "$label: $ms ms")
        assertTrue("$label took $ms ms", ms <= ceilingMs)
        result
    }

    private fun plan(sql: String, vararg args: Any?): String =
        db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", args).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("detail")) else null }.joinToString(" | ")
        }

    @Test
    fun statisticsAndDashboardQueries() {
        val analytics = OfflineAnalyticsRepository(db.analyticsDao())
        val (todayFrom, todayTo) = StatsPeriod.TODAY.range(now, zone)
        val (monthFrom, _) = StatsPeriod.LAST_30_DAYS.range(now, zone)
        timed("activity today") { analytics.observeActivity(todayFrom, todayTo).first() }
        timed("activity all time") { analytics.observeActivity(null, todayTo).first() }
        timed("time buckets 30 days") { analytics.observeTimeBuckets(monthFrom!!, todayTo).first() }
        val buckets = timed("study buckets (streak)") { analytics.observeStudyBuckets().first() }
        Log.i(TAG, "study buckets returned: ${buckets.size}")
        timed("answers by type 7 days") { analytics.observeAnswersByType(monthFrom, todayTo).first() }
        timed("trouble items today") { analytics.observeTroubleItems(todayFrom, todayTo, 10).first() }
        timed("trouble items all time") { analytics.observeTroubleItems(null, todayTo, 10).first() }
        timed("status by type") { analytics.observeStatusByType().first() }
        timed("due counts") { analytics.observeDue(now, ReviewTime.startOfDay(now, zone)).first() }
        timed("priority items") { analytics.observePriorityItems(now, 5).first() }
        val schedule = timed("schedule") { analytics.observeSchedule().first() }
        assertTrue(schedule.size == LESSONS * VOCAB_PER_LESSON)
    }

    @Test
    fun reviewQueueAndBrowsing() {
        val clock = Clock.fixed(now, zone)
        val progress = OfflineProgressRepository(db.progressDao(), clock)
        val content = OfflineContentRepository(db.contentDao(), db.contentPackDao())
        val dayStart = ReviewTime.startOfDay(now, zone)
        timed("review overview") { progress.observeReviewOverview(now, dayStart, dayStart.plusMillis(DAY)).first() }
        timed("due items (200)") { progress.getDueItems(now, 200) }
        timed("new items (20)") { progress.getNewItems(20) }
        timed("item history (20)") { progress.getReviewHistory(com.n3sensei.app.domain.model.ItemRef(com.n3sensei.app.domain.model.ItemType.VOCABULARY, "big-l1-v1"), 20) }
        val all = timed("vocabulary list, no filter") { content.observeVocabularyItems(ItemQuery()).first() }
        Log.i(TAG, "vocabulary list size: ${all.size}")
        timed("vocabulary search 'nghia thu 7'") { content.observeVocabularyItems(ItemQuery(text = "nghia thu 7")).first() }
        timed("vocabulary search kana 'ご'") { content.observeVocabularyItems(ItemQuery(text = "ご")).first() }
        timed("kanji list") { content.observeKanjiItems(ItemQuery()).first() }
    }

    @Test
    fun heavyQueriesUseIndexes() {
        val to = now.toEpochMilli()
        val from = to - DAY
        // Regression: the "item still in the content" filter used to make SQLite walk the whole
        // history through the item_type index; the period filter must stay an index range.
        val period = plan("SELECT COUNT(*) FROM review_records r WHERE $IN_PERIOD AND $VALID_RECORD", from, to)
        val history = plan("SELECT * FROM review_records WHERE item_type = 'VOCABULARY' AND item_id = ? ORDER BY reviewed_at DESC LIMIT 20", "x")
        val due = plan("SELECT * FROM learning_progress p WHERE p.due_at <= ? ORDER BY p.due_at LIMIT 20", to)
        val request = plan("SELECT COUNT(*) FROM review_records WHERE request_id = ?", "x")
        listOf("statistics period" to period, "history" to history, "due" to due, "request id" to request)
            .forEach { (k, v) -> Log.i(TAG, "plan $k: $v") }
        assertTrue(period, "SEARCH r USING COVERING INDEX index_review_records_reviewed_at" in period || "SEARCH r USING INDEX index_review_records_reviewed_at" in period)
        assertTrue(period, "MULTI-INDEX OR" !in period)
        assertTrue(history, "index_review_records_item_type_item_id" in history)
        assertTrue(due, "index_learning_progress_due_at" in due)
        assertTrue(request, "index_review_records_request_id" in request)
    }

    /**
     * Regression: exporting this history used to throw OutOfMemoryError (the whole history was
     * held three times, once as a JSON tree). Export, check and restore it into an empty database.
     */
    @Test
    fun backupOfLargeHistory_exportsAndRestoresWithinMemory(): Unit = runBlocking {
        val file = File(context.cacheDir, "perf-backup.json").apply { delete() }
        val prefs = InMemoryPreferencesRepository()
        val runtime = Runtime.getRuntime()
        var peak = 0L
        fun sample() { peak = maxOf(peak, runtime.totalMemory() - runtime.freeMemory()) }
        System.gc()
        val baseline = runtime.totalMemory() - runtime.freeMemory()

        val source = AndroidBackupRepository(context, db.backupDao(), prefs, RecordingReminderScheduler(), Clock.fixed(now, zone), "perf")
        var started = System.nanoTime()
        val exported = source.export(Uri.fromFile(file).toString())
        val exportMs = (System.nanoTime() - started) / 1_000_000
        sample()
        assertTrue(exported.issues.toString(), exported.success)
        assertEquals(ANSWERS, exported.counts[BackupSection.REVIEW_RECORDS])

        context.deleteDatabase(RESTORE_DB)
        val target = Room.databaseBuilder(context, N3Database::class.java, RESTORE_DB).build()
        try {
            target.contentPackDao().replacePack(bundle())
            val repo = AndroidBackupRepository(context, target.backupDao(), prefs, RecordingReminderScheduler(), Clock.fixed(now, zone), "perf")
            started = System.nanoTime()
            val preview = (repo.inspect(Uri.fromFile(file).toString()) as InspectResult.Ready).preview
            val inspectMs = (System.nanoTime() - started) / 1_000_000
            sample()
            started = System.nanoTime()
            val report = repo.restore(preview, restoreSettings = false)
            val restoreMs = (System.nanoTime() - started) / 1_000_000
            sample()
            assertTrue(report.issues.toString(), report.success)
            assertEquals(ANSWERS, report.counts.getValue(BackupSection.REVIEW_RECORDS).added)
            Log.i(
                TAG,
                "backup ${file.length() / 1048576} MB: export $exportMs ms, check $inspectMs ms, restore $restoreMs ms; " +
                    "heap baseline ${baseline / 1048576} MB, peak sampled ${peak / 1048576} MB, limit ${runtime.maxMemory() / 1048576} MB",
            )
        } finally {
            target.close()
            context.deleteDatabase(RESTORE_DB)
            file.delete()
        }
    }
}
