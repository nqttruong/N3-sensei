package com.n3sensei.app.performance

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineErrorRepository
import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.dashboard.ExamDashboardManager
import com.n3sensei.app.domain.dashboard.ExamStatistics
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamSetFilter
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.mistakes.ErrorReviewSessionManager
import com.n3sensei.app.domain.mistakes.NotebookFilter
import com.n3sensei.app.domain.mistakes.WeaknessAnalyzer
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.InMemoryPreferencesRepository
import com.n3sensei.app.testing.MutableTestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * TEST DATA ONLY: far more exam data than a learner will have, to time what the exam screens
 * run. 200 sets of 30 questions (6 000 questions on 1 500 study items), 300 submitted attempts
 * over 300 days (9 000 graded questions) and one notebook entry per question ever missed.
 * Timings are logged with tag `N3Perf`; the ceilings only catch gross regressions.
 */
@RunWith(AndroidJUnit4::class)
class ExamModulePerformanceTest {

    companion object {
        private const val TAG = "N3Perf"
        private const val DB_NAME = "exam-perf-test.db"
        private const val SETS = 200
        private const val QUESTIONS = 30
        private const val ATTEMPTS = 300
        private val day = 24 * 60 * 60_000L
        private val clock = MutableTestClock(1_800_000_000_000)
        private lateinit var db: N3Database
        private lateinit var exams: OfflineExamRepository
        private lateinit var errors: OfflineErrorRepository

        private val types = QuestionType.entries.filter { it != QuestionType.OTHER }

        private fun ref(i: Int) = when (i % 3) {
            0 -> ItemRef(ItemType.VOCABULARY, "v${i % 1000}")
            1 -> ItemRef(ItemType.KANJI, "k${i % 300}")
            else -> ItemRef(ItemType.GRAMMAR, "g${i % 200}")
        }

        private fun set(s: Int) = ExamSet(
            "set$s", "Đề $s", null, "N3", if (s % 4 == 0) ExamType.FULL else ExamType.SECTION, 1_800, null, s.toLong(),
            ExamSectionKind.entries.mapIndexed { i, k -> ExamSection("set$s-s$i", k, null, i) },
            emptyList(),
            (0 until QUESTIONS).map { q ->
                ExamQuestion(
                    "set$s-q$q", "set$s-s${q % 4}", types[(s + q) % types.size], AnswerFormat.CHOICE, "Câu hỏi số $q của đề $s", null,
                    listOf("một", "hai", "ba", "bốn"), q % 4, emptyList(), "Giải thích $q", 2, null, null, q,
                    listOf(ref(s * 7 + q), ref(s * 13 + q * 3)),
                )
            },
        )

        @BeforeClass
        @JvmStatic
        fun createDatabase(): Unit = runBlocking {
            val started = System.nanoTime()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase(DB_NAME)
            db = N3Database.create(context, DB_NAME)
            var ids = 0
            exams = OfflineExamRepository(db, clock) { "a${++ids}" }
            errors = OfflineErrorRepository(db, clock)
            (0 until SETS).forEach { exams.saveExamSet(set(it)) }
            // Attempts: answered by SQL in one transaction (the repository would take minutes).
            val random = Random(7)
            val sql = db.openHelper.writableDatabase
            sql.beginTransaction()
            try {
                repeat(ATTEMPTS) { a ->
                    val s = set(a % SETS)
                    val at = clock.millis() - (ATTEMPTS - a) * day
                    sql.execSQL("INSERT INTO exam_attempts VALUES ('att$a', '${s.id}', '${s.title}', ${at - 600_000}, $at, 1800, 600, 0, $QUESTIONS, 'SUBMITTED', NULL)")
                    s.questions.forEachIndexed { pos, q ->
                        val r = random.nextInt(10)
                        val outcome = when { r < 6 -> "CORRECT"; r < 9 -> "WRONG"; else -> "UNANSWERED" }
                        val snapshot = OfflineExamRepository.snapshot(q).replace("'", "''")
                        sql.execSQL("INSERT INTO exam_attempt_items VALUES ('att$a', $pos, '${q.id}', 'VOCABULARY', '${ExamSkill.of(q.type, ExamSectionKind.VOCABULARY).name}', '$outcome', '$snapshot')")
                        if (outcome != "UNANSWERED") {
                            val pick = if (outcome == "CORRECT") q.answerIndex else (q.answerIndex!! + 1) % 4
                            sql.execSQL("INSERT INTO exam_answers VALUES ('att$a', '${q.id}', $pick, NULL, ${if (outcome == "CORRECT") 1 else 0}, 20000, NULL, 0, $at, '$snapshot')")
                        }
                        if (outcome != "CORRECT") {
                            sql.execSQL(
                                "INSERT INTO error_records (question_id, exam_set_id, error_type, error_count, first_wrong_at, last_wrong_at, mastery_level, status, notes, question_snapshot, is_important, jlpt_level, skill) " +
                                    "VALUES ('${q.id}', '${s.id}', 'INCORRECT', 1, $at, $at, 0, 'ACTIVE', NULL, '$snapshot', 0, 'N3', '${ExamSkill.of(q.type, ExamSectionKind.VOCABULARY).name}') " +
                                    "ON CONFLICT(question_id) DO UPDATE SET error_count = error_count + 1, last_wrong_at = $at",
                            )
                        }
                    }
                }
                sql.execSQL(
                    "INSERT INTO review_items (error_record_id, next_review_at, interval_minutes, step_index, review_count, success_count, last_reviewed_at, review_status) " +
                        "SELECT id, CASE WHEN id % 2 = 0 THEN ${clock.millis() - day} ELSE ${clock.millis() + 3 * day} END, 1440, 0, 0, 0, NULL, 'NEW' FROM error_records",
                )
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
            Log.i(TAG, "exam setup: ${(System.nanoTime() - started) / 1_000_000} ms")
        }

        @AfterClass
        @JvmStatic
        fun deleteDatabase() {
            db.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
        }
    }

    private fun <T> timed(label: String, ceilingMs: Long, block: suspend () -> T): T = runBlocking {
        block() // warm up
        val started = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i(TAG, "$label: $ms ms")
        assertTrue("$label took $ms ms (ceiling $ceilingMs)", ms <= ceilingMs)
        result
    }

    private fun plan(sql: String): String =
        db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("detail")) else null }.joinToString(" | ")
        }

    @Test
    fun theExamListAndASet_loadQuickly() {
        val all = timed("exam list (200 sets)", 1_500) { exams.observeExamSets().first() }
        assertTrue(all.size == SETS)
        timed("exam list filtered", 1_500) { exams.observeExamSets(ExamSetFilter("N3", ExamSkill.KANJI, ExamType.SECTION)).first() }
        timed("one set with questions", 500) { exams.getExamSet("set42")!! }
        timed("result of an attempt", 1_000) { exams.getRecord("att150")!! }
    }

    @Test
    fun historyNotebookAndAnalysis_stayWithinSeconds() {
        val history = timed("graded history (9 000)", 4_000) { exams.getGradedHistory() }
        assertTrue(history.size == ATTEMPTS * QUESTIONS)
        val notebook = timed("notebook (all errors)", 4_000) { errors.observeErrors().first() }
        timed("notebook search", 800) { notebook.filter(NotebookFilter(query = "câu hỏi số 7")::matches) }
        timed("weak points", 1_500) { WeaknessAnalyzer.analyze(history) }
        timed("error categories", 2_000) { ExamStatistics.categories(history) }
        timed("due count", 300) { errors.observeDueCount(clock.millis()).first() }
    }

    @Test
    fun aReviewSessionAndTheDashboard_buildInSeconds() {
        val sessions = ErrorReviewSessionManager(errors, exams, clock)
        val cards = timed("due review session", 5_000) { sessions.dueSession() }
        assertTrue(cards.isNotEmpty())
        timed("one error session", 2_000) { sessions.singleSession(cards.first().error.id) }
        val dashboard = ExamDashboardManager(
            exams, errors, OfflineProgressRepository(db.progressDao(), clock), OfflineContentRepository(db.contentDao(), db.contentPackDao()),
            InMemoryPreferencesRepository(), clock,
        )
        timed("dashboard", 8_000) { dashboard.load() }
        timed("skill detail", 4_000) { dashboard.skillDetail(ExamSkill.GRAMMAR) }
    }

    @Test
    fun heavyQueries_useIndexes() {
        val graded = plan(
            "SELECT i.*, a.exam_set_id FROM exam_attempt_items i JOIN exam_attempts a ON a.id = i.attempt_id " +
                "LEFT JOIN exam_answers ans ON ans.attempt_id = i.attempt_id AND ans.question_id = i.question_id WHERE a.status IN ('SUBMITTED', 'EXPIRED')",
        )
        Log.i(TAG, "graded history plan: $graded")
        assertFalse(graded, Regex("SCAN ans\\b").containsMatchIn(graded))
        val due = plan(
            "SELECT COUNT(*) FROM error_records e JOIN review_items r ON r.error_record_id = e.id WHERE e.status = 'ACTIVE' AND r.next_review_at <= 0",
        )
        Log.i(TAG, "due plan: $due")
        val byQuestion = plan("SELECT * FROM error_records WHERE question_id = 'x'")
        assertTrue(byQuestion, byQuestion.contains("USING INDEX"))
    }

    /**
     * Regression (QA): started from the main thread, as the ViewModels do, the heavy work must
     * not block it. A probe posted to the main thread every 16 ms measures how late it runs.
     */
    @Test
    fun heavyWork_startedOnTheMainThread_doesNotBlockIt() {
        val dashboard = ExamDashboardManager(
            exams, errors, OfflineProgressRepository(db.progressDao(), clock), OfflineContentRepository(db.contentDao(), db.contentPackDao()),
            InMemoryPreferencesRepository(), clock,
        )
        val sessions = ErrorReviewSessionManager(errors, exams, clock)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var worst = 0L
        val done = java.util.concurrent.CountDownLatch(1)
        main.post {
            kotlinx.coroutines.MainScope().launch {
                dashboard.load()
                sessions.dueSession()
                errors.observeErrors().first().filter(NotebookFilter(query = "đề 12")::matches)
                done.countDown()
            }
        }
        while (done.count > 0) {
            val posted = System.nanoTime()
            val ran = java.util.concurrent.CountDownLatch(1)
            main.post { worst = maxOf(worst, (System.nanoTime() - posted) / 1_000_000); ran.countDown() }
            ran.await()
            Thread.sleep(16)
        }
        Log.i(TAG, "worst main-thread delay: $worst ms")
        assertTrue("main thread blocked for $worst ms", worst <= 250)
    }
}
