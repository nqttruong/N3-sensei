package com.n3sensei.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.backup.AndroidBackupRepository
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.backup.InspectResult
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.data.importing.ContrastPackCheck
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.contrast.SubmitOutcome
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.InMemoryPreferencesRepository
import com.n3sensei.app.testing.RecordingReminderScheduler
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * M8 data-integrity checks on a real Room v16 database (test-only file, deleted afterwards):
 * - a write failure injected with a test-only SQLite trigger inside each learner write
 *   (start a session, append items, record an answer that schedules a block) leaves no partial state;
 * - the same answer submitted many times concurrently is stored once and schedules once;
 * - the old backup neither exports nor touches the Contrast tables (documented limitation).
 */
@RunWith(AndroidJUnit4::class)
class ContrastM8IntegrityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-m8-integrity-test.db"
    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val grammar = "cg-g-koto-ni-shiteiru-naru"
    private lateinit var db: N3Database
    private lateinit var content: OfflineContrastContentRepository
    private lateinit var practice: OfflineContrastPracticeRepository
    private lateinit var progress: OfflineContrastProgressRepository
    private val dir = File(context.cacheDir, "contrast-m8-backup").apply { deleteRecursively(); mkdirs() }

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, N3Database::class.java, name).build()
        content = OfflineContrastContentRepository(db.contrastDao())
        practice = OfflineContrastPracticeRepository(db.contrastDao())
        progress = OfflineContrastProgressRepository(db.contrastDao())
        // The TEST-ONLY copy of the sample pack (never installed in the app database).
        val text = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }
        content.replaceInstalledPack(ContrastPackCheck.check(text).validation.pack!!, schemaVersion = 1, now = t0)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
        dir.deleteRecursively()
    }

    private val start get() = StartOrResumePractice(content, practice, progress)
    private val submit get() = SubmitContrastAnswer(content, practice, progress)

    /** Every learner table, to compare before/after a failed write. */
    private fun learnerState() = listOf("contrast_sessions", "contrast_session_items", "contrast_attempts", "contrast_progress", "contrast_reviews")
        .associateWith { table -> db.openHelper.readableDatabase.query("SELECT * FROM $table").use { c -> generateSequence { if (c.moveToNext()) (0 until c.columnCount).map { c.getString(it) } else null }.toList() } }

    /** A test-only trigger that aborts [event] on [table]: the injected write failure. */
    private fun failOn(event: String, table: String) {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER m8_fault BEFORE $event ON $table BEGIN SELECT RAISE(ABORT, 'M8 injected failure'); END")
    }

    private fun healed() = db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS m8_fault")

    private fun expectFailure(block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected the injected failure")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("M8 injected failure"))
        }
    }

    private suspend fun started(scope: PracticeScope, now: Instant = t0) = (start(scope, now, zone) as StartPracticeResult.Started).session

    /** Answers the questions of the first block (positions 0..), the last one through [last]. */
    private suspend fun answerFirstBlockButLast(sessionId: String): Int {
        val session = practice.session(sessionId)!!
        val block = session.items.filter { it.groupId == session.items.first().groupId && it.blockIndex == session.items.first().blockIndex }
        block.dropLast(1).forEach { item ->
            val q = content.getQuestion(item.questionId)!!
            assertTrue(submit(sessionId, item.position, AnswerInput.Chosen(q.correctOptionId), "req-${item.position}", t0, zone) is SubmitOutcome.Recorded)
        }
        return block.last().position
    }

    @Test
    fun startingASession_failingWhileWritingItems_keepsThePreviousSessionActive_andWritesNothing() = runBlocking {
        val first = started(PracticeScope.Group(grammar))
        val before = learnerState()
        failOn("INSERT", "contrast_session_items")
        // Starting a new scope abandons the active session and inserts the new one in one transaction.
        expectFailure { start(PracticeScope.Due, t0.plusSeconds(60), zone) }
        healed()
        assertEquals(before, learnerState())
        assertEquals(first.id, practice.activeSession()!!.id)
        Unit
    }

    @Test
    fun answeringTheLastQuestionOfABlock_failingWhileScheduling_rollsBackAttemptReviewProgressAndSession() = runBlocking {
        val session = started(PracticeScope.Group(grammar))
        val last = answerFirstBlockButLast(session.id)
        val before = learnerState()
        assertEquals(0, db.countRows("contrast_progress"))
        // The block's last answer writes attempt + review + progress + session activity: fail at the progress row.
        failOn("INSERT", "contrast_progress")
        val q = content.getQuestion(practice.session(session.id)!!.items.first { it.position == last }.questionId)!!
        expectFailure { submit(session.id, last, AnswerInput.Chosen(q.correctOptionId), "req-last", t0.plusSeconds(30), zone) }
        healed()
        assertEquals(before, learnerState())
        // The same request succeeds afterwards and schedules the block exactly once.
        val outcome = submit(session.id, last, AnswerInput.Chosen(q.correctOptionId), "req-last", t0.plusSeconds(31), zone) as SubmitOutcome.Recorded
        assertTrue(outcome.result.blockCompleted)
        assertEquals(1, db.countRows("contrast_reviews"))
        assertEquals(1, db.countRows("contrast_progress WHERE review_count = 1"))
    }

    @Test
    fun failingWhileTouchingTheSession_rollsBackTheAttempt() = runBlocking {
        val session = started(PracticeScope.Group(grammar))
        val before = learnerState()
        failOn("UPDATE", "contrast_sessions")
        val q = content.getQuestion(session.items.first { it.position == 0 }.questionId)!!
        expectFailure { submit(session.id, 0, AnswerInput.Chosen(q.correctOptionId), "req-0", t0.plusSeconds(5), zone) }
        healed()
        assertEquals(before, learnerState())
    }

    @Test
    fun appendingItems_failingWhileTouchingTheSession_addsNoItem() = runBlocking {
        val session = started(PracticeScope.Group(grammar))
        val before = learnerState()
        val extra = session.items.first().copy(position = session.items.size)
        failOn("UPDATE", "contrast_sessions")
        expectFailure { practice.appendItems(session.id, listOf(extra), t0.plusSeconds(5)) }
        healed()
        assertEquals(before, learnerState())
    }

    @Test
    fun theSameAnswerSubmittedConcurrently_isStoredOnce_andSchedulesOnce() = runBlocking {
        val session = started(PracticeScope.Group(grammar))
        val last = answerFirstBlockButLast(session.id)
        val q = content.getQuestion(practice.session(session.id)!!.items.first { it.position == last }.questionId)!!
        // A double tap / retry storm: 12 concurrent submissions of the block's last answer, same request id.
        val outcomes = withContext(Dispatchers.IO) {
            (1..12).map { async { submit(session.id, last, AnswerInput.Chosen(q.correctOptionId), "req-storm", t0.plusSeconds(40), zone) } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it is SubmitOutcome.Recorded })
        assertEquals(11, outcomes.count { it == SubmitOutcome.Duplicate })
        assertEquals(1, db.countRows("contrast_attempts WHERE request_id = 'req-storm'"))
        assertEquals(1, db.countRows("contrast_reviews"))
        assertEquals(1, db.countRows("contrast_progress WHERE review_count = 1"))
    }

    @Test
    fun theOldBackup_exportsNoContrastData_andARestoreLeavesItUntouched() = runBlocking {
        db.contentPackDao().replacePack(SeedData.bundle(isSeed = false))
        val session = started(PracticeScope.Group(grammar))
        val last = answerFirstBlockButLast(session.id)
        val q = content.getQuestion(practice.session(session.id)!!.items.first { it.position == last }.questionId)!!
        submit(session.id, last, AnswerInput.Chosen(q.correctOptionId), "req-last", t0, zone)
        val before = learnerState()
        val repo = AndroidBackupRepository(context, db.backupDao(), InMemoryPreferencesRepository(UserPreferences()), RecordingReminderScheduler(), Clock.fixed(t0, ZoneOffset.UTC), "test")
        val file = File(dir, "backup.json")
        assertTrue(repo.export(android.net.Uri.fromFile(file).toString()).success)
        val json = file.readText()
        assertFalse("the backup format has no Contrast section", json.contains("contrast", ignoreCase = true))
        val preview = (repo.inspect(android.net.Uri.fromFile(file).toString()) as InspectResult.Ready).preview
        assertTrue(repo.restore(preview, restoreSettings = false).success)
        assertEquals(before, learnerState())
    }
}
