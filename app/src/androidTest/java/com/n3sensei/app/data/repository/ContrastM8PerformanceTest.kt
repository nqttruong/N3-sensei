package com.n3sensei.app.data.repository

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.importing.ContrastContentImporter
import com.n3sensei.app.data.importing.ContrastImportReport
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.contrast.SubmitOutcome
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.feature.contrast.ContrastErrorsViewModel
import com.n3sensei.app.feature.contrast.ContrastHomeViewModel
import com.n3sensei.app.feature.contrast.ContrastLearningViewModel
import com.n3sensei.app.feature.contrast.ContrastLibraryViewModel
import com.n3sensei.app.feature.contrast.ContrastProgressViewModel
import com.n3sensei.app.feature.contrast.ContrastReviewViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * M8 performance on synthetic, TEST-ONLY data (a database file deleted afterwards; nothing of it is
 * in the app): the 3 sample groups cloned under new ids into a pack of 99 / 999 groups, imported
 * through the real importer (real bundled source packs), then 10 000 / 100 000 attempts. Each
 * screen is timed from ViewModel creation to its first loaded state; the use cases behind
 * "start a session" and "submit an answer" are timed directly. Results go to logcat (tag
 * ContrastPerf); the bounds are generous and only guard against a full scan per row.
 */
@RunWith(AndroidJUnit4::class)
class ContrastM8PerformanceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-m8-perf-test.db"
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val clock = Clock.fixed(t0, zone)
    private lateinit var db: N3Database
    private val sample = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        AndroidContentImportRepository.BUNDLED_PACK_ASSETS.forEach { asset ->
            val json = context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(asset, ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), clock).import(json).outcome)
        }
    }

    @After
    fun tearDown() {
        // Cancels the viewModelScopes so no collector touches the closed database.
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        db.close()
        context.deleteDatabase(name)
    }

    private val idPrefix = Regex("^(cg|ci|cx|cm|ce|ct|cq)-")

    /** The sample's groups cloned [copies] times; every content id gets the suffix "-p<k>". */
    private fun syntheticPack(copies: Int): String {
        val base = JSONObject(sample)
        val groups = JSONArray()
        fun rename(o: Any?, k: Int): Any? = when (o) {
            is JSONObject -> JSONObject().also { out -> o.keys().forEach { key -> out.put(if (idPrefix.containsMatchIn(key)) "$key-p$k" else key, rename(o.get(key), k)) } }
            is JSONArray -> JSONArray().also { out -> (0 until o.length()).forEach { out.put(rename(o.get(it), k)) } }
            // "cq-x/a" (a question option, in acknowledged warnings) → "cq-x-p<k>/a".
            is String -> if (idPrefix.containsMatchIn(o)) o.substringBefore('/') + "-p$k" + (if ('/' in o) "/" + o.substringAfter('/') else "") else o
            else -> o
        }
        repeat(copies) { k ->
            val src = base.getJSONArray("groups")
            (0 until src.length()).forEach { groups.put(rename(src.getJSONObject(it), k)) }
        }
        return base.put("groups", groups).toString()
    }

    private fun <T> loaded(flow: StateFlow<UiState<T>>): T = runBlocking {
        withTimeout(120_000) { (flow.first { it !is UiState.Loading } as UiState.Success<T>).data }
    }

    private inline fun <reified V : ViewModel> vm(crossinline create: () -> V): V {
        val store = ViewModelStore().also { stores += it }
        val factory = viewModelFactory { initializer { create() } }
        var v: V? = null
        instrumentation.runOnMainSync { v = ViewModelProvider.create(store, factory)[V::class] }
        return v!!
    }

    private fun usedHeapKb(): Long {
        repeat(3) { Runtime.getRuntime().gc(); System.runFinalization() }
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024
    }

    private fun run(copies: Int, attempts: Int): Map<String, Long> {
        val content = OfflineContrastContentRepository(db.contrastDao())
        val practice = OfflineContrastPracticeRepository(db.contrastDao())
        val progress = OfflineContrastProgressRepository(db.contrastDao())
        val analytics = OfflineContrastAnalyticsRepository(db.contrastDao())
        val sources = RoomContrastSourceResolver(db.contrastDao())
        val timings = linkedMapOf<String, Long>()
        fun timed(label: String, block: () -> Unit) { timings[label] = measureTimeMillis(block) }

        val text = syntheticPack(copies)
        var report: ContrastImportReport? = null
        timed("import pack (${copies * 3} groups, ${text.length / 1024} KB JSON)") { report = runBlocking { ContrastContentImporter(db.contrastDao(), clock).import(text) } }
        assertEquals(ContrastContentImporter.summary(report!!), ContrastImportReport.Outcome.IMPORTED, report!!.outcome)
        val groupIds = db.openHelper.readableDatabase.query("SELECT id FROM contrast_groups ORDER BY id").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertEquals(copies * 3, groupIds.size)

        // Attempts in completed sessions of 20 answers, one per hour before t0 (raw SQL: seeding only).
        val w = db.openHelper.writableDatabase
        val random = Random(8)
        timed("seed $attempts attempts (test only)") {
            w.beginTransaction()
            try {
                val s = w.compileStatement("INSERT INTO contrast_sessions (id, scope_kind, scope_value, status, started_at, last_activity_at, content_version) VALUES (?, 'DUE', NULL, 'COMPLETED', ?, ?, 1)")
                val a = w.compileStatement(
                    "INSERT INTO contrast_attempts (request_id, session_id, position, group_id, group_revision, question_id, question_revision, question_type, content_pack_id, content_version, outcome, chosen_option_id, correct_option_id, expected_item_id, chosen_item_id, chosen_text, correct_text, answered_at) " +
                        "VALUES (?, ?, ?, ?, 1, ?, 1, ?, 'contrast-n3', 1, ?, ?, 'a', ?, ?, ?, 'correct', ?)",
                )
                val perSession = 20
                val sessionsCount = attempts / perSession
                repeat(sessionsCount) { si ->
                    val at = t0.toEpochMilli() - (sessionsCount - si) * 3_600_000L
                    s.bindString(1, "ps$si"); s.bindLong(2, at); s.bindLong(3, at); s.executeInsert(); s.clearBindings()
                    repeat(perSession) { p ->
                        val r = random.nextInt(10)
                        val outcome = if (r < 6) "CORRECT" else if (r < 9) "INCORRECT" else "SKIPPED"
                        val expected = "i${random.nextInt(3)}"
                        val chosen = when (outcome) { "CORRECT" -> expected; "INCORRECT" -> "i${(expected.last() - '0' + 1 + random.nextInt(2)) % 3}"; else -> null }
                        a.bindString(1, "pr$si-$p"); a.bindString(2, "ps$si"); a.bindLong(3, p.toLong()); a.bindString(4, groupIds[random.nextInt(groupIds.size)])
                        a.bindString(5, "pq${random.nextInt(5000)}"); a.bindString(6, ContrastQuestionType.entries[random.nextInt(4)].name); a.bindString(7, outcome)
                        if (chosen == null) a.bindNull(8) else a.bindString(8, "b")
                        a.bindString(9, expected)
                        if (chosen == null) a.bindNull(10) else a.bindString(10, chosen)
                        if (chosen == null) a.bindNull(11) else a.bindString(11, "chosen")
                        a.bindLong(12, at + p * 1000L)
                        a.executeInsert(); a.clearBindings()
                    }
                }
                // 60 % of the groups scheduled, half of them due now.
                val pr = w.compileStatement("INSERT INTO contrast_progress (group_id, step_index, due_at, last_reviewed_at, review_count, lapse_count, created_at, updated_at) VALUES (?, 1, ?, ?, 1, 0, ?, ?)")
                groupIds.take(groupIds.size * 6 / 10).forEachIndexed { i, g ->
                    val due = t0.toEpochMilli() + (if (i % 2 == 0) -1 else 1) * (1 + i) * 60_000L
                    pr.bindString(1, g); pr.bindLong(2, due); pr.bindLong(3, t0.toEpochMilli() - 86_400_000L); pr.bindLong(4, t0.toEpochMilli()); pr.bindLong(5, t0.toEpochMilli())
                    pr.executeInsert(); pr.clearBindings()
                }
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
        }

        timed("Contrast Home (first loaded state)") { loaded(vm { ContrastHomeViewModel(content, practice, progress, analytics, clock) }.state) }
        val heapBefore = usedHeapKb()
        lateinit var library: ContrastLibraryViewModel
        timed("Library (all ${groupIds.size} rows)") {
            library = vm { ContrastLibraryViewModel(content, practice, progress, clock, SavedStateHandle()) }
            assertEquals(groupIds.size, loaded(library.state).rows.size)
        }
        timings["Library heap delta (KB, approx.)"] = usedHeapKb() - heapBefore
        timed("Library search \"ことになっている\"") {
            instrumentation.runOnMainSync { library.setQuery("ことになっている") }
            runBlocking { withTimeout(120_000) { library.state.first { (it as? UiState.Success)?.data?.let { d -> d.query == "ことになっている" && d.rows.size == copies } == true } } }
        }
        timed("Compare (group detail + sources)") { loaded(vm { ContrastLearningViewModel(groupIds.first { it.startsWith("cg-g-") }, content, sources, practice, progress, clock) }.state) }
        timed("Error Analysis") { loaded(vm { ContrastErrorsViewModel(content, analytics, SavedStateHandle(), retiredLabel = { it }) }.state) }
        timed("Progress") { loaded(vm { ContrastProgressViewModel(content, practice, progress, analytics, clock) }.state) }
        timed("Smart Review queue") { assertTrue(loaded(vm { ContrastReviewViewModel(content, practice, progress, analytics, clock, retiredLabel = { it }) }.state).due.isNotEmpty()) }

        lateinit var started: StartPracticeResult
        timed("start a session (due + new, StartOrResumePractice)") { started = runBlocking { StartOrResumePractice(content, practice, progress)(PracticeScope.Due, t0, zone) } }
        val session = (started as StartPracticeResult.Started).session
        assertTrue(session.items.isNotEmpty())
        val item = session.items.first { it.position == 0 }
        val q = runBlocking { content.getQuestion(item.questionId)!! }
        timed("submit an answer (SubmitContrastAnswer)") {
            assertTrue(runBlocking { SubmitContrastAnswer(content, practice, progress)(session.id, 0, AnswerInput.Chosen(q.correctOptionId), "perf-submit", t0, zone) } is SubmitOutcome.Recorded)
        }
        timings.forEach { (k, v) -> Log.i("ContrastPerf", "[${copies * 3} groups / $attempts attempts] $k: $v") }
        return timings
    }

    private fun assertBounded(timings: Map<String, Long>, maxMs: Long) {
        timings.filterKeys { !it.startsWith("seed") && !it.contains("heap") }.forEach { (k, v) -> assertTrue("$k took ${v}ms", v < maxMs) }
    }

    @Test
    fun about100Groups_and10kAttempts() {
        assertBounded(run(copies = 33, attempts = 10_000), maxMs = 5_000)
    }

    @Test
    fun about1000Groups_and100kAttempts() {
        assertBounded(run(copies = 333, attempts = 100_000), maxMs = 15_000)
    }
}
