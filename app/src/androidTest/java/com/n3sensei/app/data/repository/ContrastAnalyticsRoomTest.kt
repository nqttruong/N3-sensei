package com.n3sensei.app.data.repository

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.importing.ContrastPackCheck
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.contrast.AnalyzeContrastMistakes
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastMistakeAnalyzer
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.GetContrastErrorAnalysis
import com.n3sensei.app.domain.contrast.GetContrastProgressReport
import com.n3sensei.app.domain.contrast.GetContrastReviewQueue
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.repository.AttemptFilter
import com.n3sensei.app.domain.repository.ContrastAnalyticsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * M7 analytics on a real Room database FILE: the SQL aggregates must agree with the M1 Kotlin
 * analyzer on the same attempts, the REVIEW scope round-trips, and the aggregates are timed on a
 * synthetic load (1 000 groups, 100 000 attempts; test-only data, deleted afterwards).
 */
@RunWith(AndroidJUnit4::class)
class ContrastAnalyticsRoomTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-analytics-test.db"
    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private lateinit var db: N3Database
    private lateinit var content: OfflineContrastContentRepository
    private lateinit var practice: OfflineContrastPracticeRepository
    private lateinit var progress: OfflineContrastProgressRepository
    private lateinit var analytics: ContrastAnalyticsRepository

    @Before
    fun setUp() = runTest {
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, N3Database::class.java, name).build()
        content = OfflineContrastContentRepository(db.contrastDao())
        practice = OfflineContrastPracticeRepository(db.contrastDao())
        progress = OfflineContrastProgressRepository(db.contrastDao())
        analytics = OfflineContrastAnalyticsRepository(db.contrastDao())
        // The TEST-ONLY copy of the sample pack (never installed in the app database).
        val text = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }
        content.replaceInstalledPack(ContrastPackCheck.check(text).validation.pack!!, schemaVersion = 1, now = t0)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private var n = 0
    private val sessions = mutableSetOf<String>()

    private fun attempt(session: String, minutes: Long, group: String, outcome: AnswerOutcome, expected: String?, chosen: String?, type: ContrastQuestionType = ContrastQuestionType.MULTIPLE_CHOICE): ContrastAttempt {
        n++
        return ContrastAttempt(
            "r$n", session, n, group, 1, "q$n", 1, type, "p", 1, outcome,
            if (outcome == AnswerOutcome.SKIPPED) null else "o", "o", expected, if (outcome == AnswerOutcome.SKIPPED) null else chosen,
            if (outcome == AnswerOutcome.SKIPPED) null else "chosen$n", "correct$n", t0.plus(Duration.ofMinutes(minutes)),
        )
    }

    /** Stores [attempts] in their sessions (positions renumbered 0..n-1 per session, as a real session has). */
    private suspend fun store(attempts: List<ContrastAttempt>) {
        attempts.groupBy { it.sessionId }.forEach { (session, raw) ->
            check(sessions.add(session)) { "one store() per session" }
            val list = raw.mapIndexed { i, a -> a.copy(position = i) }
            practice.createSession(ContrastSession(session, PracticeScope.Due, SessionStatus.ACTIVE, t0, t0, 1, list.map { SessionItem(it.position, it.groupId, it.questionId, 1, 0) }))
            list.forEach { practice.recordAnswer(it, null) }
            practice.finishSession(session, SessionStatus.COMPLETED, t0)
        }
    }

    private val grammar = "cg-g-koto-ni-shiteiru-naru"
    private val vocab = "cg-v-sameru-cold-awake"

    @Test
    fun sqlAggregates_agreeWithTheM1Analyzer_andSkipsPendingUnavailableNeverCountAsWrong() = runTest {
        val random = Random(7)
        val items = mapOf(grammar to listOf("A", "B", "C"), vocab to listOf("X", "Y"), "retired-g" to listOf("R", "S"))
        val all = buildList {
            repeat(12) { s ->
                repeat(8) { i ->
                    val group = items.keys.elementAt(random.nextInt(items.size))
                    val its = items.getValue(group)
                    val expected = its.random(random)
                    val outcome = AnswerOutcome.entries.random(random)
                    val chosen = when (outcome) {
                        AnswerOutcome.CORRECT -> expected
                        AnswerOutcome.INCORRECT -> if (random.nextInt(4) == 0) null else its.filter { it != expected }.random(random)
                        AnswerOutcome.SKIPPED -> null
                    }
                    add(attempt("s$s", s * 600L + i, group, outcome, expected, chosen, ContrastQuestionType.entries.random(random)))
                }
            }
        }
        store(all)
        // The same request id again: ignored (idempotent), never counted twice.
        practice.recordAnswer(all.first().copy(position = 0), null)

        val m1 = ContrastMistakeAnalyzer().analyze(all)
        val m7 = GetContrastErrorAnalysis(analytics)()
        assertEquals(m1.total, m7.totals.total)
        assertEquals(Triple(m1.correct, m1.incorrect, m1.skipped), Triple(m7.totals.correct, m7.totals.incorrect, m7.totals.skipped))
        assertEquals(m1.accuracy, m7.totals.accuracy)
        assertEquals(m1.otherIncorrect, m7.otherIncorrect)
        assertEquals(
            m1.confusions.map { listOf(it.groupId, it.expectedItemId, it.chosenItemId, it.count, it.sessions, it.lastAt) },
            m7.directions.map { listOf(it.count.groupId, it.count.expectedItemId, it.count.chosenItemId, it.count.count, it.count.sessions, it.count.lastAt) },
        )
        assertEquals(m1.groups.map { it.groupId to it.accuracy }.toSet(), m7.groups.map { it.groupId to it.counts.accuracy }.toSet())
        assertEquals(m1.byQuestionType.map { it.type to it.incorrect }.toSet(), m7.byQuestionType.map { it.type to it.counts.incorrect }.toSet())
        // A content-type filter = the M1 use case's filter (published groups of that type only).
        val m1Vocab = AnalyzeContrastMistakes(content, practice)(contentType = ContrastContentType.VOCABULARY)
        assertEquals(m1Vocab.total, analytics.totals(AttemptFilter(contentType = ContrastContentType.VOCABULARY)).total)
        // Group and question-type filters.
        assertEquals(all.count { it.groupId == grammar }, analytics.totals(AttemptFilter(groupId = grammar)).total)
        assertEquals(all.count { it.questionType == ContrastQuestionType.ERROR_DETECTION }, analytics.totals(AttemptFilter(questionType = ContrastQuestionType.ERROR_DETECTION)).total)
        // Sessions: newest first, limited.
        val recent = analytics.recentSessions(limit = 3)
        assertEquals(listOf("s11", "s10", "s9"), recent.map { it.sessionId })
        assertEquals(all.filter { it.sessionId == "s11" }.count { it.outcome == AnswerOutcome.INCORRECT }, recent[0].counts.incorrect)
        // Latest example and recent mistakes are the stored snapshots.
        val top = m7.directions.first().count
        val latest = all.filter { it.confusion?.let { c -> c.expectedItemId == top.expectedItemId && c.chosenItemId == top.chosenItemId } == true && it.groupId == top.groupId }.maxBy { it.answeredAt }
        assertEquals(latest.chosenText, analytics.latestExample(top.groupId, top.expectedItemId, top.chosenItemId)!!.chosenText)
        assertEquals(all.filter { it.outcome == AnswerOutcome.INCORRECT }.sortedByDescending { it.answeredAt }.take(5).map { it.questionId }, analytics.recentMistakes(limit = 5).map { it.questionId })
    }

    @Test
    fun sessionsSince_countsLaterSessionsWithAnswers_notTheErrorsOwnSession() = runTest {
        store(
            listOf(
                attempt("a", 0, grammar, AnswerOutcome.INCORRECT, "A", "B"),
                attempt("b", 60, grammar, AnswerOutcome.INCORRECT, "A", "B"),
                attempt("b", 61, grammar, AnswerOutcome.CORRECT, "A", "A"), // same session as the last error
                attempt("c", 120, grammar, AnswerOutcome.SKIPPED, "A", null), // skipped only: no evidence
                attempt("d", 180, grammar, AnswerOutcome.CORRECT, "A", "A"),
                attempt("e", 240, vocab, AnswerOutcome.CORRECT, "X", "X"), // another group
            ),
        )
        val d = analytics.directions().single()
        assertEquals(listOf(2, 2, 1), listOf(d.count, d.sessions, d.sessionsSince))
    }

    @Test
    fun theReviewScope_roundTrips_andChangesAreObserved() = runTest {
        val before = analytics.observeChanges().first()
        practice.createSession(ContrastSession("rv", PracticeScope.Review, SessionStatus.ACTIVE, t0, t0, 1, listOf(SessionItem(0, grammar, "cq-g-koto-mc1", 1, 0))))
        assertEquals(PracticeScope.Review, practice.activeSession()!!.scope)
        store(listOf(attempt("z", 0, grammar, AnswerOutcome.CORRECT, "A", "A")))
        assertEquals(before + 1, analytics.observeChanges().first())
    }

    /** Synthetic load (not user data): timings are logged under the tag ContrastPerf. */
    @Test
    fun aggregatesOnAThousandGroupsAndAHundredThousandAttempts() = runTest {
        val w = db.openHelper.writableDatabase
        val groups = 1000
        val attempts = 100_000
        val types = ContrastContentType.entries
        val qtypes = ContrastQuestionType.entries
        val random = Random(11)
        val seed = measureTimeMillis {
            w.beginTransaction()
            try {
                val g = w.compileStatement("INSERT INTO contrast_groups (id, pack_id, content_type, jlpt_level, title, summary_vi, revision, sort_order, search_text) VALUES (?, (SELECT id FROM contrast_packs LIMIT 1), ?, 'N3', ?, '', 1, ?, '')")
                repeat(groups) { i ->
                    g.bindString(1, "perf-$i"); g.bindString(2, types[i % 3].name); g.bindString(3, "Perf $i"); g.bindLong(4, 100L + i)
                    g.executeInsert(); g.clearBindings()
                }
                val s = w.compileStatement("INSERT INTO contrast_sessions (id, scope_kind, scope_value, status, started_at, last_activity_at, content_version) VALUES (?, 'DUE', NULL, 'COMPLETED', ?, ?, 1)")
                val a = w.compileStatement(
                    "INSERT INTO contrast_attempts (request_id, session_id, position, group_id, group_revision, question_id, question_revision, question_type, content_pack_id, content_version, outcome, chosen_option_id, correct_option_id, expected_item_id, chosen_item_id, chosen_text, correct_text, answered_at) " +
                        "VALUES (?, ?, ?, ?, 1, ?, 1, ?, 'p', 1, ?, ?, 'o', ?, ?, ?, 'correct', ?)",
                )
                val perSession = 20
                repeat(attempts / perSession) { si ->
                    val sessionAt = t0.toEpochMilli() + si * 3_600_000L
                    s.bindString(1, "ps$si"); s.bindLong(2, sessionAt); s.bindLong(3, sessionAt); s.executeInsert(); s.clearBindings()
                    repeat(perSession) { p ->
                        val group = "perf-${random.nextInt(groups)}"
                        val r = random.nextInt(10)
                        val outcome = if (r < 6) "CORRECT" else if (r < 9) "INCORRECT" else "SKIPPED"
                        val expected = "i${random.nextInt(3)}"
                        val chosen = when (outcome) { "CORRECT" -> expected; "INCORRECT" -> "i${(expected.last() - '0' + 1 + random.nextInt(2)) % 3}"; else -> null }
                        a.bindString(1, "pr$si-$p"); a.bindString(2, "ps$si"); a.bindLong(3, p.toLong()); a.bindString(4, group); a.bindString(5, "pq${random.nextInt(5000)}")
                        a.bindString(6, qtypes[random.nextInt(4)].name); a.bindString(7, outcome)
                        if (chosen == null) a.bindNull(8) else a.bindString(8, "o")
                        a.bindString(9, expected)
                        if (chosen == null) a.bindNull(10) else a.bindString(10, chosen)
                        if (chosen == null) a.bindNull(11) else a.bindString(11, "chosen")
                        a.bindLong(12, sessionAt + p * 1000L)
                        a.executeInsert(); a.clearBindings()
                    }
                }
                val pr = w.compileStatement("INSERT INTO contrast_progress (group_id, step_index, due_at, last_reviewed_at, review_count, lapse_count, created_at, updated_at) VALUES (?, 1, ?, ?, 1, 0, ?, ?)")
                repeat(600) { i ->
                    val due = t0.toEpochMilli() + (i - 300) * 3_600_000L
                    pr.bindString(1, "perf-$i"); pr.bindLong(2, due); pr.bindLong(3, t0.toEpochMilli()); pr.bindLong(4, t0.toEpochMilli()); pr.bindLong(5, t0.toEpochMilli())
                    pr.executeInsert(); pr.clearBindings()
                }
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
        }
        val timings = linkedMapOf<String, Long>()
        fun <T> timed(label: String, block: suspend () -> T): T {
            var result: T? = null
            timings[label] = measureTimeMillis { result = kotlinx.coroutines.runBlocking { block() } }
            return result!!
        }
        val totals = timed("totals (SQL)") { analytics.totals() }
        assertEquals(attempts, totals.total)
        timed("byGroup (SQL)") { analytics.byGroup() }
        timed("byQuestionType (SQL)") { analytics.byQuestionType() }
        val directions = timed("directions + sessionsSince (SQL)") { analytics.directions() }
        assertTrue(directions.size > 1000)
        timed("filtered totals GRAMMAR (SQL)") { analytics.totals(AttemptFilter(contentType = ContrastContentType.GRAMMAR)) }
        timed("recentSessions (SQL)") { analytics.recentSessions(limit = 5) }
        timed("recentMistakes (SQL)") { analytics.recentMistakes(limit = 10) }
        timed("GetContrastErrorAnalysis") { GetContrastErrorAnalysis(analytics)() }
        val now = t0.plus(Duration.ofDays(1))
        val report = timed("GetContrastProgressReport (600 scheduled groups)") { GetContrastProgressReport(content, practice, progress, analytics)(now, zone) }
        assertEquals(groups + 3, report.published)
        val queue = timed("GetContrastReviewQueue") { GetContrastReviewQueue(content, practice, progress, analytics)(now, zone) }
        assertEquals(600, queue.due.size + queue.upcoming.size)
        timed("M1 AnalyzeContrastMistakes (all attempts in memory, for comparison)") { AnalyzeContrastMistakes(content, practice)() }
        Log.i("ContrastPerf", "seed ${seed}ms")
        timings.forEach { (k, v) -> Log.i("ContrastPerf", "$k: ${v}ms") }
        // Generous bounds (emulator): these guard against an accidental full scan per row, not micro-timings.
        listOf("totals (SQL)", "byGroup (SQL)", "byQuestionType (SQL)", "recentSessions (SQL)", "recentMistakes (SQL)").forEach {
            assertTrue("$it took ${timings[it]}ms", timings.getValue(it) < 2_000)
        }
        assertTrue("directions took ${timings["directions + sessionsSince (SQL)"]}ms", timings.getValue("directions + sessionsSince (SQL)") < 10_000)
    }
}
