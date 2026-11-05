package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.mistakes.ErrorReviewSchedule
import com.n3sensei.app.domain.mistakes.ErrorState
import com.n3sensei.app.domain.mistakes.ReviewQuestionKind
import com.n3sensei.app.domain.repository.SaveExamResult
import com.n3sensei.app.testing.ExamTestData
import com.n3sensei.app.testing.ExamTestData.Q_FILL
import com.n3sensei.app.testing.ExamTestData.Q_KANJI
import com.n3sensei.app.testing.ExamTestData.Q_LISTEN
import com.n3sensei.app.testing.ExamTestData.SET_ID
import com.n3sensei.app.testing.MutableTestClock
import com.n3sensei.app.testing.countRows
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

/** The error notebook on a real Room database file: flags, notes, reviews and their history, repeats. */
@RunWith(AndroidJUnit4::class)
class ErrorRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "error-notebook-test.db"
    private val clock = MutableTestClock(1_000_000)
    private val minute = 60_000L
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var errors: OfflineErrorRepository

    private fun open() {
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        errors = OfflineErrorRepository(db, clock)
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

    /** One exam: kanji wrong, fill right, the rest blank. */
    private suspend fun takeExam(kanjiPick: Int = 1) {
        val a = exams.startAttempt(SET_ID)!!
        exams.answer(a.id, Q_KANJI, AnswerInput.Choice(kanjiPick), 1_000)
        exams.answer(a.id, Q_FILL, AnswerInput.Text("ため"), 1_000)
        clock.advance(10 * minute)
        exams.submit(a.id)
    }

    private suspend fun kanjiError() = errors.observeErrors().first().single { it.record.questionId == Q_KANJI }

    @Test
    fun aMissedQuestion_isOneEntry_withItsLevelAndSkill_andNewUntilReviewed() = runBlocking {
        takeExam()
        val all = errors.observeErrors().first()
        assertEquals(5, all.size) // kanji wrong + four blanks; the right one is not there
        val kanji = kanjiError()
        assertEquals("N3", kanji.jlptLevel)
        assertEquals(ExamSkill.KANJI, kanji.skill)
        assertEquals(ErrorState.NEW, kanji.state)
        assertEquals(ExamSkill.LISTENING, all.single { it.record.questionId == Q_LISTEN }.skill)
        assertEquals(kanji.id, errors.findByQuestion(Q_KANJI))
        assertNull(errors.findByQuestion(Q_FILL))
    }

    @Test
    fun flagsAndNotes_areSaved_andUnderstoodIsNotMastered() = runBlocking {
        takeExam()
        val id = kanjiError().id
        assertTrue(errors.setImportant(id, true))
        assertTrue(errors.setNote(id, "  nhầm âm on  "))
        assertTrue(errors.setUnderstood(id, true))
        db.close()
        open()
        val e = errors.observeError(id).first()!!
        assertTrue(e.isImportant)
        assertEquals("nhầm âm on", e.record.notes)
        assertEquals(clock.millis(), e.understoodAt)
        assertEquals(ErrorState.NEW, e.state) // understood ≠ mastered
        assertTrue(e.isPriority)
        assertTrue(errors.setNote(id, "   "))
        assertNull(errors.observeError(id).first()!!.record.notes)
        assertFalse(errors.setImportant(999, true))
    }

    private val day = 24 * 60 * minute

    /** Reviews the error with its own question, or [other] (another question, of [kind]). */
    private suspend fun review(id: Long, pick: Int, other: ExamQuestion? = null, kind: ReviewQuestionKind = ReviewQuestionKind.SIMILAR) =
        if (other != null) {
            errors.review(id, other, kind, AnswerInput.Choice(pick))!!
        } else {
            errors.review(id, errors.observeError(id).first()!!.record.question!!, ReviewQuestionKind.ORIGINAL, AnswerInput.Choice(pick))!!
        }

    @Test
    fun theFirstReview_isADayAfterTheExam_andAnEarlierRightAnswerIsOnlyPractice() = runBlocking {
        takeExam()
        val e = kanjiError()
        assertEquals(clock.millis() + day, e.review!!.nextReviewAt)
        assertEquals(0, errors.observeDueCount(clock.millis()).first())
        val early = review(e.id, 0)
        assertTrue(early.correct)
        assertFalse(early.counted)
        assertEquals(0, kanjiError().review!!.stepIndex)
        clock.advance(day)
        assertEquals(5, errors.observeDueCount(clock.millis()).first())
        assertTrue(review(e.id, 0).counted)
        assertEquals(1, kanjiError().review!!.stepIndex)
        assertEquals(clock.millis() + 3 * day, kanjiError().review!!.nextReviewAt)
    }

    @Test
    fun theOldQuestionAlone_neverMastersAnError_anotherQuestionOnTheSameItemDoes() = runBlocking {
        takeExam()
        val id = kanjiError().id
        listOf(1L, 3L, 7L, 14L, 30L).forEach { days ->
            clock.advance(days * day)
            assertTrue(review(id, 0).counted)
        }
        val waiting = kanjiError()
        assertEquals(ErrorState.REVIEWING, waiting.state)
        assertEquals(ErrorReviewSchedule.LEVELS, waiting.review!!.stepIndex)

        clock.advance(30 * day)
        val other = ExamTestData.set().questions.single { it.id == Q_FILL }.copy(format = AnswerFormat.CHOICE, options = listOf("ため", "のに"), answerIndex = 0)
        val result = review(id, 0, other, ReviewQuestionKind.APPLICATION)
        assertTrue(result.mastered)
        assertEquals(ErrorState.MASTERED, kanjiError().state)
        val log = errors.observeReviewLog(id).first()
        assertEquals(6, log.size)
        assertEquals(ReviewQuestionKind.APPLICATION, log.first().kind)
        assertEquals(Q_FILL, log.first().questionId)
        assertEquals(Q_KANJI, log.last().questionId)
        assertEquals(4, errors.observeDueCount(clock.millis() + 365 * day).first()) // the four blanks; the mastered one is not due
    }

    @Test
    fun wrongAnswers_dropTwoLevelsEachTime_andComeBackTheNextDay() = runBlocking {
        takeExam()
        val id = kanjiError().id
        listOf(1L, 3L, 7L).forEach { clock.advance(it * day); review(id, 0) }
        assertEquals(3, kanjiError().review!!.stepIndex)
        clock.advance(14 * day)
        val wrong = review(id, 3)
        assertFalse(wrong.correct)
        assertEquals(1, wrong.stepAfter)
        assertEquals(clock.millis() + day, wrong.nextReviewAt)
        clock.advance(day)
        review(id, 2)
        assertEquals(0, kanjiError().review!!.stepIndex)
        assertEquals(2, kanjiError().review!!.let { it.reviewCount - it.successCount })
    }

    @Test
    fun missingItAgainInAnExam_reopensTheSameEntry() = runBlocking {
        takeExam()
        val id = kanjiError().id
        clock.advance(day)
        review(id, 0)
        errors.setUnderstood(id, true)
        clock.advance(day)
        takeExam()
        val again = kanjiError()
        assertEquals(id, again.id) // the same entry, not a second one
        assertEquals(2, again.record.errorCount)
        assertNull(again.understoodAt)
        assertTrue(again.isRecurring)
        assertEquals(0, again.review!!.stepIndex)
        assertEquals(clock.millis() + day, again.review!!.nextReviewAt)
        assertEquals(1, db.countRows("error_records WHERE question_id = '$Q_KANJI'"))
        assertEquals(1, errors.observeReviewLog(id).first().size)
    }

    @Test
    fun deletingTheSet_keepsTheNotebook_andItsReviews() = runBlocking {
        takeExam()
        val id = kanjiError().id
        review(id, 0)
        assertTrue(exams.deleteExamSet(SET_ID))
        val e = errors.observeError(id).first()!!
        assertNotNull(e.record.question)
        assertEquals("N3", e.jlptLevel)
        assertFalse(review(id, 1).correct)
        assertEquals(2, errors.observeReviewLog(id).first().size)
    }
}
