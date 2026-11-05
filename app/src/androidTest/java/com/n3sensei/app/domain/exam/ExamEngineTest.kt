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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exam engine (session, answers, timer, submission) on a real Room database file. Closing
 * and opening the database again stands for the app being closed or its process killed.
 */
@RunWith(AndroidJUnit4::class)
class ExamEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "exam-engine-test.db"
    private val start = 1_000_000L
    private val limit = 1_800_000L // ExamTestData: 30 minutes
    private val clock = MutableTestClock(start)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var submission: ExamSubmissionManager
    private lateinit var sessions: ExamSessionManager
    private lateinit var answers: AnswerManager

    private fun open() {
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        submission = ExamSubmissionManager(exams, clock)
        sessions = ExamSessionManager(exams, submission)
        answers = AnswerManager(exams, clock)
    }

    /** The app is closed (or its process killed) and opened again. */
    private fun reopen() {
        db.close()
        open()
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

    private fun ExamSession.question(id: String) = questions.single { it.id == id }

    @Test
    fun theExamList_filtersByLevelSkillAndFullOrSectionExam() = runBlocking {
        val base = ExamTestData.set()
        // Section, passage and question ids are unique across sets.
        val listening = base.copy(
            id = "listening-only", title = "Nghe", type = ExamType.SECTION,
            sections = base.sections.filter { it.kind == ExamSectionKind.LISTENING }.map { it.copy(id = "l-${it.id}") },
            passages = base.passages.filter { it.id == "p-audio" }.map { it.copy(id = "l-${it.id}") },
            questions = base.questions.filter { it.sectionId == "s-listening" }
                .map { it.copy(id = "l-${it.id}", sectionId = "l-${it.sectionId}", passageId = it.passageId?.let { p -> "l-$p" }) },
        )
        assertEquals(SaveExamResult.Saved, exams.saveExamSet(listening))
        val n2 = base.copy(
            id = "n2", jlptLevel = "N2", sections = base.sections.map { it.copy(id = "n2-${it.id}") }, passages = emptyList(),
            questions = base.questions.map { it.copy(id = "n2-${it.id}", sectionId = "n2-${it.sectionId}", passageId = null) },
        )
        assertEquals(SaveExamResult.Saved, exams.saveExamSet(n2))

        suspend fun ids(filter: ExamSetFilter) = exams.observeExamSets(filter).first().map { it.id }.toSet()
        assertEquals(setOf(SET_ID, "listening-only", "n2"), ids(ExamSetFilter()))
        assertEquals(setOf(SET_ID, "listening-only"), ids(ExamSetFilter(jlptLevel = "N3")))
        assertEquals(setOf("n2"), ids(ExamSetFilter(jlptLevel = "N2")))
        assertEquals(setOf(SET_ID, "n2"), ids(ExamSetFilter(skill = ExamSkill.KANJI)))
        assertEquals(setOf(SET_ID, "listening-only", "n2"), ids(ExamSetFilter(skill = ExamSkill.LISTENING)))
        assertEquals(emptySet<String>(), ids(ExamSetFilter(skill = ExamSkill.VOCABULARY)))
        assertEquals(setOf("listening-only"), ids(ExamSetFilter(type = ExamType.SECTION)))
        assertEquals(setOf(SET_ID), ids(ExamSetFilter("N3", ExamSkill.READING, ExamType.FULL)))
        val full = exams.observeExamSets().first().single { it.id == SET_ID }
        assertEquals(setOf(ExamSkill.KANJI, ExamSkill.GRAMMAR, ExamSkill.READING, ExamSkill.LISTENING), full.skills)

        assertEquals("Chỉ dùng cho kiểm thử", exams.getExamSet(SET_ID)!!.description)
        assertEquals(listOf(Q_KANJI, Q_FILL, Q_READ_1, Q_READ_2, Q_LISTEN, Q_RESPONSE), exams.getQuestions(SET_ID).map { it.id })
        assertEquals(listOf(Q_READ_1, Q_READ_2), exams.getQuestions(SET_ID, ExamSkill.READING).map { it.id })
        assertEquals(listOf("l-$Q_LISTEN", "l-$Q_RESPONSE"), exams.getQuestions("listening-only").map { it.id })
        assertTrue(exams.getQuestions("nope").isEmpty())
    }

    @Test
    fun movingAnsweringChangingAndFlagging_areSavedAtOnce_andComeBackAfterTheProcessDies() = runBlocking {
        val session = sessions.start(SET_ID)!!
        val id = session.attempt.id
        assertEquals(0, session.currentIndex)
        assertEquals(Q_KANJI, session.attempt.currentQuestionId)

        val shown = clock.millis()
        clock.advance(4_000)
        val first = answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(1), shown)!!
        assertFalse(first.isCorrect)
        assertEquals(4_000, first.timeSpentMs)
        clock.advance(2_000)
        val changed = answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(0), clock.millis() - 2_000)!!
        assertTrue(changed.isCorrect)
        assertEquals(1, changed.changeCount)
        assertEquals(6_000, changed.timeSpentMs)
        answers.answer(id, session.question(Q_FILL), AnswerInput.Text("ため"), clock.millis())
        assertTrue(sessions.moveTo(id, Q_READ_1))
        assertTrue(answers.setFlag(id, Q_READ_1, true))
        assertTrue(answers.setFlag(id, Q_KANJI, true))
        assertTrue(answers.setFlag(id, Q_KANJI, false))
        assertFalse(answers.setFlag(id, "not-in-set", true))

        // Inputs that do not fit the question are refused and change nothing.
        assertNull(answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(7), clock.millis()))
        assertNull(answers.answer(id, session.question(Q_KANJI), AnswerInput.Text("けいけん"), clock.millis()))
        assertNull(answers.answer(id, session.question(Q_FILL), AnswerInput.Text("  "), clock.millis()))

        reopen()
        val resumed = (sessions.open(id) as OpenedSession.Active).session
        assertEquals(2, resumed.currentIndex)
        assertEquals(setOf(Q_KANJI, Q_FILL), resumed.answers.keys)
        assertEquals(0, resumed.answers.getValue(Q_KANJI).selectedIndex)
        assertEquals(1, resumed.answers.getValue(Q_KANJI).changeCount)
        assertEquals(setOf(Q_READ_1), resumed.flagged)
        assertEquals(listOf(id), sessions.resumable(SET_ID).map { it.id })
        assertEquals(id, sessions.prepare(SET_ID)!!.unfinished!!.id)
    }

    @Test
    fun theTimerUsesRealTime_soAnAttemptLeftOpenResumesWithLessTime() = runBlocking {
        val id = sessions.start(SET_ID)!!.attempt.id
        clock.advance(600_000) // ten minutes with the app closed
        reopen()
        val resumed = (sessions.open(id) as OpenedSession.Active).session
        assertEquals(limit - 600_000, ExamTimer.remainingMillis(resumed.attempt, clock.millis()))
        assertEquals(AttemptStatus.IN_PROGRESS, resumed.attempt.status)
    }

    @Test
    fun timeRunningOutWhileTheAppIsClosed_submitsTheAttemptAtItsDeadline_withTheAnswersItHad() = runBlocking {
        val session = sessions.start(SET_ID)!!
        val id = session.attempt.id
        answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(0), clock.millis())
        answers.setFlag(id, Q_FILL, true)
        clock.advance(limit + 3_600_000) // an hour past the end
        reopen()

        // Even before anything submits it, no answer is taken after the deadline.
        assertNull(answers.answer(id, session.question(Q_FILL), AnswerInput.Text("ため"), clock.millis()))
        assertFalse(answers.setFlag(id, Q_READ_1, true))

        assertTrue(sessions.resumable().isEmpty())
        val attempt = exams.getAttempt(id)!!
        assertEquals(AttemptStatus.EXPIRED, attempt.status)
        assertEquals(start + limit, attempt.submittedAt)
        assertEquals(1_800L, attempt.durationSeconds)
        assertEquals(1, attempt.correctCount)
        assertEquals(5, db.countRows("error_records"))
        // Over now: opening it shows the result and it takes no changes.
        val finished = sessions.open(id) as OpenedSession.Finished
        assertEquals(AttemptStatus.EXPIRED, finished.result.attempt.status)
        assertEquals(1, finished.result.answers.size)
        assertFalse(sessions.moveTo(id, Q_READ_1))
        assertNull(sessions.prepare(SET_ID)!!.unfinished)
    }

    @Test
    fun atTheDeadlineExactly_answersStop_andTheSubmitIsExpired() = runBlocking {
        val session = sessions.start(SET_ID)!!
        val id = session.attempt.id
        clock.advance(limit - 1)
        assertNotNull(answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(0), clock.millis()))
        clock.advance(1)
        assertNull(answers.answer(id, session.question(Q_FILL), AnswerInput.Text("ため"), clock.millis()))
        val outcome = submission.submitIfExpired(exams.getAttempt(id)!!) as SubmitOutcome.Submitted
        assertEquals(AttemptStatus.EXPIRED, outcome.result.attempt.status)
        assertEquals(1, outcome.result.attempt.correctCount)
    }

    @Test
    fun aManualSubmit_isFinal_andSubmittingAgainChangesNothing() = runBlocking {
        val session = sessions.start(SET_ID)!!
        val id = session.attempt.id
        answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(2), clock.millis())
        clock.advance(60_000)
        assertNull(submission.submitIfExpired(exams.getAttempt(id)!!)) // still has time

        val submitted = submission.submit(id) as SubmitOutcome.Submitted
        assertEquals(AttemptStatus.SUBMITTED, submitted.result.attempt.status)
        assertEquals(60L, submitted.result.attempt.durationSeconds)
        assertEquals(0, submitted.result.attempt.correctCount)

        clock.advance(limit) // the timer firing later does not submit it again
        val again = submission.submit(id) as SubmitOutcome.AlreadySubmitted
        assertEquals(submitted.result.attempt, again.result.attempt)
        assertNull(submission.submitIfExpired(exams.getAttempt(id)!!))
        assertTrue(submission.submitExpired().isEmpty())
        assertEquals(6, db.countRows("error_records"))
        assertEquals(6, db.countRows("error_records WHERE error_count = 1"))
        assertEquals(6, db.countRows("review_items"))

        // Read-only now.
        assertNull(answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(0), clock.millis()))
        assertFalse(answers.setFlag(id, Q_KANJI, true))
        assertFalse(sessions.moveTo(id, Q_FILL))
        assertEquals(2, exams.getAnswers(id).single().selectedIndex)
    }

    @Test
    fun manySubmitsAtTheSameTime_giveOneResult_andRecordEachMistakeOnce() = runBlocking {
        val session = sessions.start(SET_ID)!!
        val id = session.attempt.id
        answers.answer(id, session.question(Q_KANJI), AnswerInput.Choice(0), clock.millis())
        clock.advance(limit) // the timer and the learner's tap at the very end
        val outcomes = coroutineScope { (1..8).map { async(Dispatchers.IO) { submission.submit(id) } }.awaitAll() }
        assertEquals(1, outcomes.count { it is SubmitOutcome.Submitted })
        assertEquals(7, outcomes.count { it is SubmitOutcome.AlreadySubmitted })
        assertEquals(1, db.countRows("exam_attempts WHERE status = 'EXPIRED'"))
        assertEquals(5, db.countRows("error_records WHERE error_count = 1"))
        assertEquals(5, db.countRows("review_items"))
    }

    @Test
    fun abandonedOrUnknownAttempts_cannotBeSubmitted() = runBlocking {
        val id = sessions.start(SET_ID)!!.attempt.id
        assertTrue(exams.abandon(id))
        assertEquals(SubmitOutcome.NotSubmittable, submission.submit(id))
        assertEquals(SubmitOutcome.NotSubmittable, submission.submit("nope"))
        assertEquals(AttemptStatus.ABANDONED, (sessions.open(id) as OpenedSession.Finished).result.attempt.status)
        assertEquals(OpenedSession.Missing, sessions.open("nope"))
        assertEquals(0, db.countRows("error_records"))
    }

    @Test
    fun anAttemptWhoseSetWasDeleted_isSubmittedWithTheAnswersItHas() = runBlocking {
        val session = sessions.start(SET_ID)!!
        answers.answer(session.attempt.id, session.question(Q_KANJI), AnswerInput.Choice(0), clock.millis())
        assertTrue(exams.deleteExamSet(SET_ID))
        val finished = sessions.open(session.attempt.id) as OpenedSession.Finished
        assertEquals(AttemptStatus.SUBMITTED, finished.result.attempt.status)
        assertEquals(1, finished.result.attempt.correctCount)
        assertNull(sessions.start(SET_ID))
    }

    @Test
    fun aSetWithoutTimeLimit_neverExpires() = runBlocking {
        val base = ExamTestData.set()
        val untimed = base.copy(
            id = "untimed", durationSeconds = 0, sections = base.sections.map { it.copy(id = "u-${it.id}") }, passages = emptyList(),
            questions = base.questions.map { it.copy(id = "u-${it.id}", sectionId = "u-${it.sectionId}", passageId = null) },
        )
        assertEquals(SaveExamResult.Saved, exams.saveExamSet(untimed))
        val session = sessions.start("untimed")!!
        clock.advance(10L * 86_400_000)
        reopen()
        assertEquals(listOf(session.attempt.id), sessions.resumable("untimed").map { it.id })
        assertNull(ExamTimer.remainingMillis(session.attempt, clock.millis()))
        assertNotNull(answers.answer(session.attempt.id, session.questions.first(), AnswerInput.Choice(0), clock.millis()))
        assertEquals(AttemptStatus.SUBMITTED, (submission.submit(session.attempt.id) as SubmitOutcome.Submitted).result.attempt.status)
    }
}
