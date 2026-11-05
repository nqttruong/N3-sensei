package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AttemptStatus
import com.n3sensei.app.domain.exam.Confidence
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
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

/** The exam data layer on a real Room database file, closed and opened again where it matters. */
@RunWith(AndroidJUnit4::class)
class ExamRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "exam-test.db"
    private val clock = MutableTestClock(1_000_000)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository

    private fun open() {
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
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

    @Test
    fun anExamSet_isStoredWithItsSectionsPassagesAudioAndLinks_inOrder() = runBlocking {
        val set = exams.getExamSet(SET_ID)!!
        assertEquals(listOf(ExamSectionKind.VOCABULARY, ExamSectionKind.GRAMMAR, ExamSectionKind.READING, ExamSectionKind.LISTENING), set.sections.map { it.kind })
        assertEquals(listOf(Q_KANJI, Q_FILL, Q_READ_1, Q_READ_2, Q_LISTEN, Q_RESPONSE), set.questions.map { it.id })
        assertEquals(6, set.totalQuestions)
        // One reading text and one audio, each used by several or one question.
        assertEquals(listOf("p-text", "p-text"), set.questions.filter { it.passageId == "p-text" }.map { it.passageId })
        assertEquals("exam/test-exam-1/listening-1.mp3", set.passages.single { it.id == "p-audio" }.audioPath)
        assertEquals("exam/test-exam-1/response-1.mp3", set.questions.single { it.id == Q_RESPONSE }.audioPath)
        assertEquals(listOf("ため", "ために"), set.questions.single { it.id == Q_FILL }.acceptedAnswers)
        assertEquals(
            setOf(ItemRef(ItemType.VOCABULARY, "v-keiken"), ItemRef(ItemType.KANJI, "k-kei")),
            set.questions.single { it.id == Q_KANJI }.relatedItems.toSet(),
        )
        val summary = exams.observeExamSets().first().single()
        assertEquals(6, summary.totalQuestions)
        assertEquals(0, summary.submittedAttempts)
        assertNull(summary.lastCorrect)
    }

    @Test
    fun anInvalidSet_isRefused_andNothingIsWritten() = runBlocking {
        val bad = ExamTestData.set().let { s ->
            s.copy(
                id = "bad",
                questions = s.questions.map {
                    when (it.id) {
                        Q_KANJI -> it.copy(answerIndex = 9)
                        Q_FILL -> it.copy(acceptedAnswers = listOf(" "))
                        Q_READ_1 -> it.copy(passageId = "missing")
                        else -> it
                    }
                },
            )
        }
        val result = exams.saveExamSet(bad) as SaveExamResult.Invalid
        assertEquals(setOf(Q_KANJI, Q_FILL, Q_READ_1), result.problems.keys)
        assertNull(exams.getExamSet("bad"))
        assertEquals(1, db.countRows("exam_sets"))
    }

    @Test
    fun answers_areGradedAtOnce_changesCounted_andTypedAnswersNormalised() = runBlocking {
        val attempt = exams.startAttempt(SET_ID)!!
        assertEquals(AttemptStatus.IN_PROGRESS, attempt.status)
        assertFalse(exams.answer(attempt.id, Q_KANJI, AnswerInput.Choice(1), 4_000)!!.isCorrect)
        clock.advance(5_000)
        val changed = exams.answer(attempt.id, Q_KANJI, AnswerInput.Choice(0), 3_000, Confidence.HIGH)!!
        assertTrue(changed.isCorrect)
        assertEquals(1, changed.changeCount)
        assertEquals(7_000, changed.timeSpentMs)
        assertEquals(Confidence.HIGH, changed.confidence)
        // Full-width and spaces do not matter for a typed answer; a choice for a text question is wrong.
        assertTrue(exams.answer(attempt.id, Q_FILL, AnswerInput.Text(" ため "), 2_000)!!.isCorrect)
        assertFalse(exams.answer(attempt.id, Q_READ_1, AnswerInput.Text("1"), 1_000)!!.isCorrect)
        // A question of another set, or an unknown attempt, is refused.
        assertNull(exams.answer(attempt.id, "not-in-set", AnswerInput.Choice(0), 1))
        assertNull(exams.answer("nope", Q_KANJI, AnswerInput.Choice(0), 1))
        assertEquals(3, exams.getAnswers(attempt.id).size)
    }

    @Test
    fun submitting_scoresTheAttempt_andPutsEveryMistakeInTheNotebook_withAReviewADayLater() = runBlocking {
        val attempt = exams.startAttempt(SET_ID)!!
        exams.answer(attempt.id, Q_KANJI, AnswerInput.Choice(0), 1_000) // right
        exams.answer(attempt.id, Q_FILL, AnswerInput.Text("のに"), 1_000) // wrong
        exams.answer(attempt.id, Q_READ_1, AnswerInput.Choice(1), 1_000) // right
        clock.advance(90_000)
        val result = exams.submit(attempt.id)!!
        assertEquals(AttemptStatus.SUBMITTED, result.attempt.status)
        assertEquals(2, result.attempt.correctCount)
        assertEquals(6, result.attempt.questionCount)
        assertEquals(90L, result.attempt.durationSeconds)

        val errors = exams.observeErrors().first().associateBy { it.questionId }
        assertEquals(setOf(Q_FILL, Q_READ_2, Q_LISTEN, Q_RESPONSE), errors.keys)
        assertEquals(ErrorType.INCORRECT, errors.getValue(Q_FILL).errorType)
        assertEquals(ErrorType.UNANSWERED, errors.getValue(Q_LISTEN).errorType)
        assertEquals("雨が降った＿＿、試合は中止になった。", errors.getValue(Q_FILL).question!!.content)
        // The first review is a day after the exam (ErrorReviewSchedule).
        assertTrue(exams.observeDueReviews(clock.millis()).first().isEmpty())
        val day = 24 * 60 * 60_000L
        val due = exams.observeDueReviews(clock.millis() + day).first()
        assertEquals(4, due.size)
        assertTrue(due.all { it.review.stepIndex == 0 && it.review.nextReviewAt == clock.millis() + day })
        // A submitted attempt takes no more answers and cannot be submitted twice.
        assertNull(exams.answer(attempt.id, Q_LISTEN, AnswerInput.Choice(3), 1))
        assertNull(exams.submit(attempt.id))
    }

    @Test
    fun severalAttemptsOfOneSet_areKept_andAMistakeMadeAgainIsCountedAgain() = runBlocking {
        val first = exams.startAttempt(SET_ID)!!
        exams.answer(first.id, Q_KANJI, AnswerInput.Choice(2), 1_000)
        exams.submit(first.id)
        val firstError = exams.getErrorForQuestion(Q_KANJI)!!
        exams.setErrorStatus(firstError.id, ErrorStatus.MASTERED)

        clock.advance(86_400_000)
        val second = exams.startAttempt(SET_ID)!!
        exams.answer(second.id, Q_KANJI, AnswerInput.Choice(3), 1_000)
        exams.answer(second.id, Q_FILL, AnswerInput.Text("ため"), 1_000)
        exams.submit(second.id)

        val again = exams.getErrorForQuestion(Q_KANJI)!!
        assertEquals(firstError.id, again.id)
        assertEquals(2, again.errorCount)
        assertEquals(ErrorStatus.ACTIVE, again.status) // a new mistake reopens it
        assertEquals(firstError.firstWrongAt, again.firstWrongAt)
        assertEquals(clock.millis(), again.lastWrongAt)
        // Answered right this time: still in the notebook from the first attempt, counted once.
        assertEquals(1, exams.getErrorForQuestion(Q_FILL)!!.errorCount)

        assertEquals(listOf(second.id, first.id), exams.observeAttempts(SET_ID).first().map { it.id })
        val summary = exams.observeExamSets().first().single()
        assertEquals(2, summary.submittedAttempts)
        assertEquals(1, summary.lastCorrect)
        assertEquals(1, exams.getAnswers(first.id).size)
        assertEquals(2, exams.getAnswers(second.id).size)
    }

    @Test
    fun updatingOrDeletingASet_keepsAttemptsAnswersAndErrors() = runBlocking {
        val attempt = exams.startAttempt(SET_ID)!!
        exams.answer(attempt.id, Q_FILL, AnswerInput.Text("のに"), 1_000)
        exams.submit(attempt.id)

        // A new version: one question removed, another reworded.
        val v2 = ExamTestData.set(title = "Đề kiểm thử v2").let { s ->
            s.copy(questions = s.questions.filter { it.id != Q_RESPONSE }.map { if (it.id == Q_FILL) it.copy(content = "（đã sửa）") else it })
        }
        assertEquals(SaveExamResult.Saved, exams.saveExamSet(v2))
        assertEquals(5, exams.getExamSet(SET_ID)!!.totalQuestions)
        assertEquals(0, db.countRows("exam_question_items WHERE question_id = '$Q_RESPONSE'"))
        // The notebook keeps the question as it was answered.
        assertEquals("雨が降った＿＿、試合は中止になった。", exams.getErrorForQuestion(Q_FILL)!!.question!!.content)
        assertNotNull(exams.getErrorForQuestion(Q_RESPONSE))

        assertTrue(exams.deleteExamSet(SET_ID))
        assertEquals(0, db.countRows("exam_questions"))
        assertEquals(0, db.countRows("exam_passages"))
        assertEquals(0, db.countRows("exam_sections"))
        db.close()
        open()
        // History outlives the set.
        assertEquals("Đề kiểm thử", exams.getAttempt(attempt.id)!!.examTitle)
        assertEquals(1, exams.getAnswers(attempt.id).size)
        assertEquals(6, exams.observeErrors().first().size)
        assertNull(exams.startAttempt(SET_ID))
    }

    @Test
    fun notebookEntries_canBeAnnotatedArchivedAndDeleted_withTheirReview() = runBlocking {
        val attempt = exams.startAttempt(SET_ID)!!
        exams.submit(attempt.id) // nothing answered: six errors
        val error = exams.getErrorForQuestion(Q_KANJI)!!
        assertTrue(exams.setErrorNotes(error.id, "  nhầm trường âm  "))
        assertEquals("nhầm trường âm", exams.getErrorForQuestion(Q_KANJI)!!.notes)
        assertTrue(exams.setErrorStatus(error.id, ErrorStatus.ARCHIVED))
        assertEquals(5, exams.observeErrors(ErrorStatus.ACTIVE).first().size)
        assertEquals(1, exams.observeErrors(ErrorStatus.ARCHIVED).first().size)
        assertEquals(6, exams.observeErrors(null).first().size)
        // Archived errors are not due for review; the others are, a day after the exam.
        val day = 24 * 60 * 60_000L
        assertEquals(5, exams.observeDueReviews(clock.millis() + day).first().size)
        // Not due before that.
        assertEquals(0, exams.observeDueReviews(clock.millis() + day - 1).first().size)

        assertTrue(exams.deleteError(error.id))
        assertEquals(5, db.countRows("review_items"))
        assertFalse(exams.deleteError(error.id))
    }

    @Test
    fun abandoning_keepsTheAnswers_butRecordsNoScoreAndNoMistakes() = runBlocking {
        val attempt = exams.startAttempt(SET_ID)!!
        exams.answer(attempt.id, Q_KANJI, AnswerInput.Choice(3), 1_000)
        assertTrue(exams.abandon(attempt.id))
        assertEquals(AttemptStatus.ABANDONED, exams.getAttempt(attempt.id)!!.status)
        assertNull(exams.getAttempt(attempt.id)!!.correctCount)
        assertEquals(1, exams.getAnswers(attempt.id).size)
        assertEquals(0, db.countRows("error_records"))
        assertFalse(exams.abandon(attempt.id))
    }

    @Test
    fun foreignKeys_cascadeInsideTheContent_andAPassageRemovedLeavesItsQuestions() = runBlocking {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DELETE FROM exam_passages WHERE id = 'p-text'")
        assertEquals(2, db.countRows("exam_questions WHERE id IN ('$Q_READ_1', '$Q_READ_2') AND passage_id IS NULL"))
        sql.execSQL("DELETE FROM exam_sections WHERE id = 's-listening'")
        assertEquals(0, db.countRows("exam_questions WHERE section_id = 's-listening'"))
        assertEquals(4, db.countRows("exam_questions"))
    }
}
