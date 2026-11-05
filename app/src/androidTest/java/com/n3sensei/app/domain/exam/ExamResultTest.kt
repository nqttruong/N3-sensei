package com.n3sensei.app.domain.exam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.domain.repository.SaveExamResult
import com.n3sensei.app.testing.ExamTestData
import com.n3sensei.app.testing.ExamTestData.Q_FILL
import com.n3sensei.app.testing.ExamTestData.Q_KANJI
import com.n3sensei.app.testing.ExamTestData.Q_LISTEN
import com.n3sensei.app.testing.ExamTestData.Q_READ_1
import com.n3sensei.app.testing.ExamTestData.Q_READ_2
import com.n3sensei.app.testing.ExamTestData.Q_RESPONSE
import com.n3sensei.app.testing.ExamTestData.SET_ID
import com.n3sensei.app.testing.MutableTestClock
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scoring and results on a real Room database: blanks, a set with four sections, a manual submit,
 * an attempt that ran out of time, retakes and history, edits of the set after a submit.
 */
@RunWith(AndroidJUnit4::class)
class ExamResultTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "exam-result-test.db"
    private val limit = 1_800_000L
    private val clock = MutableTestClock(1_000_000)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var sessions: ExamSessionManager
    private lateinit var answers: AnswerManager
    private lateinit var submission: ExamSubmissionManager
    private lateinit var results: ExamResultManager

    private fun open() {
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        submission = ExamSubmissionManager(exams, clock)
        sessions = ExamSessionManager(exams, submission)
        answers = AnswerManager(exams, clock)
        results = ExamResultManager(exams)
    }

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        open()
        assertEquals(SaveExamResult.Saved, runBlocking { exams.saveExamSet(ExamTestData.set()) })
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    /** Starts an attempt and answers: question id → choice index, or text for the typed question. */
    private suspend fun take(picks: Map<String, Any>, minutes: Long = 10): String {
        val session = sessions.start(SET_ID)!!
        picks.forEach { (id, pick) ->
            val q = session.questions.single { it.id == id }
            val input = if (pick is String) AnswerInput.Text(pick) else AnswerInput.Choice(pick as Int)
            clock.advance(5_000)
            assertNotNull(answers.answer(session.attempt.id, q, input, clock.millis() - 5_000))
        }
        clock.advance(minutes * 60_000 - picks.size * 5_000L)
        return session.attempt.id
    }

    @Test
    fun aManualSubmitWithBlanks_acrossFourSections_isScoredPerQuestionAndPerSkill() = runBlocking {
        // Right: kanji, fill. Wrong: reading 1. Blank: reading 2, both listening.
        val id = take(mapOf(Q_KANJI to 0, Q_FILL to "ため", Q_READ_1 to 0), minutes = 12)
        submission.submit(id) as SubmitOutcome.Submitted
        val report = results.report(id)!!
        assertEquals(listOf(Q_KANJI, Q_FILL, Q_READ_1, Q_READ_2, Q_LISTEN, Q_RESPONSE), report.items.map { it.question.id })
        assertEquals(
            listOf(ItemOutcome.CORRECT, ItemOutcome.CORRECT, ItemOutcome.WRONG, ItemOutcome.UNANSWERED, ItemOutcome.UNANSWERED, ItemOutcome.UNANSWERED),
            report.items.map { it.outcome },
        )
        val score = report.score
        assertEquals(listOf(6, 2, 1, 3), listOf(score.total, score.correct, score.wrong, score.unanswered))
        assertEquals(2.0 / 6, score.accuracy, 1e-9)
        assertEquals(720L, score.durationSeconds)
        val bySkill = score.skills.associateBy { it.skill }
        assertEquals(setOf(ExamSkill.KANJI, ExamSkill.GRAMMAR, ExamSkill.READING, ExamSkill.LISTENING), bySkill.keys)
        assertEquals(1 to 1, bySkill.getValue(ExamSkill.KANJI).let { it.correct to it.total })
        assertEquals(listOf(0, 1, 1, 2), bySkill.getValue(ExamSkill.READING).let { listOf(it.correct, it.wrong, it.unanswered, it.total) })
        assertEquals(0, bySkill.getValue(ExamSkill.LISTENING).correct)
        assertEquals(5_000L, bySkill.getValue(ExamSkill.READING).timeSpentMs)
        // Reading and listening are weak; three blanks, not because of the time.
        assertEquals(
            listOf(ExamSkill.READING, ExamSkill.LISTENING),
            report.improvements.filterIsInstance<Improvement.WeakSkill>().map { it.skill }.sortedBy { it.ordinal },
        )
        assertTrue(Improvement.Unanswered(3, timeRanOut = false) in report.improvements)
        assertEquals(6, db.countRows("exam_attempt_items WHERE attempt_id = '$id'"))
        assertEquals(AttemptStatus.SUBMITTED, report.attempt.status)
    }

    @Test
    fun anAttemptThatRanOutOfTime_isScoredAtItsDeadline_withTheBlanksPutDownToTheTime() = runBlocking {
        val id = take(mapOf(Q_KANJI to 0), minutes = 1)
        clock.advance(limit + 60_000)
        assertTrue(sessions.resumable().isEmpty()) // submitted by the timer logic
        val report = results.report(id)!!
        assertEquals(AttemptStatus.EXPIRED, report.attempt.status)
        assertEquals(1_800L, report.score.durationSeconds)
        assertEquals(listOf(6, 1, 0, 5), report.score.let { listOf(it.total, it.correct, it.wrong, it.unanswered) })
        assertTrue(Improvement.Unanswered(5, timeRanOut = true) in report.improvements)
    }

    @Test
    fun takingTheSetAgain_addsAResult_keepsTheFirstOne_andComparesWithIt() = runBlocking {
        val first = take(mapOf(Q_KANJI to 1, Q_FILL to "ため"), minutes = 20)
        submission.submit(first)
        val firstReport = results.report(first)!!

        clock.advance(86_400_000)
        val second = take(mapOf(Q_KANJI to 0, Q_FILL to "ため", Q_READ_1 to 1, Q_READ_2 to 2), minutes = 15)
        submission.submit(second)

        // The first result is exactly as it was.
        assertEquals(firstReport.score, results.report(first)!!.score)
        assertEquals(1, firstReport.score.correct)

        val overview = results.overview(second)!!
        assertEquals(listOf(second, first), overview.history.map { it.id })
        val c = overview.comparison!!
        assertEquals(first, c.previous.id)
        assertEquals(3, c.correctDelta) // 1 → 4
        assertEquals(-300L, c.durationDeltaSeconds) // 20 → 15 minutes
        assertEquals(setOf(ExamSkill.KANJI, ExamSkill.READING), c.improved.map { it.skill }.toSet())
        assertTrue(c.declined.isEmpty())
        assertTrue(overview.setAvailable)
        // The first attempt has nothing before it.
        assertNull(results.overview(first)!!.comparison)
        assertEquals(12, db.countRows("exam_attempt_items"))
        // Each mistake counted once per attempt it was made in.
        assertEquals(2, exams.getErrorForQuestion(Q_LISTEN)!!.errorCount)
        assertEquals(1, exams.getErrorForQuestion(Q_KANJI)!!.errorCount)
    }

    @Test
    fun editingOrDeletingTheSetLater_leavesTheResultAsSubmitted() = runBlocking {
        val id = take(mapOf(Q_FILL to "のに"), minutes = 5)
        submission.submit(id)
        val before = results.report(id)!!

        val v2 = ExamTestData.set().let { s ->
            s.copy(questions = s.questions.filter { it.id != Q_RESPONSE }.map { if (it.id == Q_FILL) it.copy(content = "（đã sửa）") else it })
        }
        assertEquals(SaveExamResult.Saved, exams.saveExamSet(v2))
        val after = results.report(id)!!
        assertEquals(before.score, after.score)
        assertEquals("雨が降った＿＿、試合は中止になった。", after.items.single { it.question.id == Q_FILL }.question.content)
        assertEquals(6, after.items.size)

        assertTrue(exams.deleteExamSet(SET_ID))
        db.close()
        open()
        val gone = results.overview(id)!!
        assertEquals(before.score, gone.report.score)
        assertEquals(false, gone.setAvailable)
        assertTrue(gone.report.record.passages.isEmpty())
    }

    @Test
    fun anAttemptNotSubmitted_isBuiltFromTheSet_andAnUnknownOneHasNoResult() = runBlocking {
        val id = take(mapOf(Q_KANJI to 0), minutes = 1)
        val record = exams.getRecord(id)!!
        assertEquals(6, record.items.size)
        assertEquals(1, record.items.count { it.outcome == ItemOutcome.CORRECT })
        assertEquals("来週の月曜日は図書館が休みです。", record.passages.getValue("p-text").text)
        assertEquals(0, db.countRows("exam_attempt_items"))
        assertNull(results.report("nope"))
    }
}
