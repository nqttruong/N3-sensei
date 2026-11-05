package com.n3sensei.app.domain.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineErrorRepository
import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.SaveExamResult
import com.n3sensei.app.testing.ExamTestData
import com.n3sensei.app.testing.ExamTestData.Q_FILL
import com.n3sensei.app.testing.ExamTestData.Q_KANJI
import com.n3sensei.app.testing.ExamTestData.SET_ID
import com.n3sensei.app.testing.MutableTestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review sessions on a real database: due dates from the clock, priority order, overdue errors,
 * and other questions taken from a second TEST DATA ONLY set that shares study items.
 */
@RunWith(AndroidJUnit4::class)
class ErrorReviewSessionManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "error-session-test.db"
    private val day = 24 * 60 * 60_000L
    private val clock = MutableTestClock(1_000_000)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var errors: OfflineErrorRepository
    private lateinit var sessions: ErrorReviewSessionManager

    /** A second set: two questions on the kanji question's items, none on anything else. */
    private val second = ExamSet(
        "test-exam-2", "Đề kiểm thử 2", null, "N3", ExamType.SECTION, 600, null, 0,
        listOf(ExamSection("s2-vocab", ExamSectionKind.VOCABULARY, null, 1)),
        emptyList(),
        listOf(
            ExamQuestion(
                "q2-orthography", "s2-vocab", QuestionType.ORTHOGRAPHY, AnswerFormat.CHOICE, "「けいけん」の漢字", null,
                listOf("経験", "径験"), 0, emptyList(), null, null, null, null, 1, listOf(ItemRef(ItemType.VOCABULARY, "v-keiken")),
            ),
            ExamQuestion(
                "q2-reading", "s2-vocab", QuestionType.KANJI_READING, AnswerFormat.CHOICE, "「経由」の読み方", null,
                listOf("けいゆ", "きょうゆ"), 0, emptyList(), null, null, null, null, 2, listOf(ItemRef(ItemType.KANJI, "k-kei")),
            ),
        ),
    )

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        errors = OfflineErrorRepository(db, clock)
        sessions = ErrorReviewSessionManager(errors, exams, clock)
        runBlocking {
            assertEquals(SaveExamResult.Saved, exams.saveExamSet(ExamTestData.set()))
            assertEquals(SaveExamResult.Saved, exams.saveExamSet(second))
            // Kanji and fill wrong, the rest blank: six errors.
            val a = exams.startAttempt(SET_ID)!!
            exams.answer(a.id, Q_KANJI, AnswerInput.Choice(1), 1_000)
            exams.answer(a.id, Q_FILL, AnswerInput.Text("のに"), 1_000)
            exams.submit(a.id)
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private suspend fun id(questionId: String) = errors.findByQuestion(questionId)!!

    @Test
    fun nothingIsDueOnTheExamDay_everythingTheNextDay_importantOnesFirst() = runBlocking {
        assertTrue(sessions.dueSession().isEmpty())
        errors.setImportant(id(Q_FILL), true)
        clock.advance(day)
        val cards = sessions.dueSession()
        assertEquals(6, cards.size)
        assertEquals(Q_FILL, cards.first().error.record.questionId)
        assertEquals(ReviewPriority.HIGH, cards.first().priority)
    }

    @Test
    fun theOldQuestionComesFirst_thenAnotherOneOnTheSameItems() = runBlocking {
        clock.advance(day)
        val kanji = sessions.dueSession().single { it.error.record.questionId == Q_KANJI }
        assertEquals(3, kanji.questionCount) // the original and the two of the second set
        assertEquals(ReviewQuestionKind.ORIGINAL, kanji.question.kind)
        assertTrue(sessions.answer(kanji, AnswerInput.Choice(0))!!.counted)

        // Next time (3 days later) another question: the kanji reading of the second set first, by kind order.
        clock.advance(3 * day)
        val next = sessions.dueSession().single { it.error.record.questionId == Q_KANJI }
        assertTrue(next.question.kind != ReviewQuestionKind.ORIGINAL)
        assertTrue(next.question.question.id in setOf("q2-orthography", "q2-reading"))
        val result = sessions.answer(next, AnswerInput.Choice(0))!!
        assertTrue(result.correct)
        val log = errors.observeReviewLog(id(Q_KANJI)).first()
        assertEquals(next.question.question.id, log.first().questionId)
        assertEquals(next.question.kind, log.first().kind)

        // A question with no study item shared by another one is reviewed with itself only.
        val listen = sessions.dueSession().single { it.error.record.questionId == ExamTestData.Q_LISTEN }
        assertEquals(1, listen.questionCount)
        assertEquals(ReviewQuestionKind.ORIGINAL, listen.question.kind)
    }

    @Test
    fun overdueErrors_stayDue_withRaisedPriority_andAWrongAnswerKeepsThemComingBack() = runBlocking {
        clock.advance(10 * day) // the app was not opened for ten days
        val cards = sessions.dueSession()
        assertEquals(6, cards.size)
        assertTrue(cards.all { it.priority >= ReviewPriority.RAISED })
        val kanji = cards.single { it.error.record.questionId == Q_KANJI }
        val wrong = sessions.answer(kanji, AnswerInput.Choice(1))!!
        assertEquals(clock.millis() + day, wrong.nextReviewAt)
        assertEquals(5, sessions.dueSession().size)
        clock.advance(day)
        assertEquals(6, sessions.dueSession().size)
    }

    @Test
    fun aSingleError_canBeReviewedBeforeItIsDue_asPracticeOnly() = runBlocking {
        val cards = sessions.singleSession(id(Q_KANJI))
        assertEquals(1, cards.size)
        val result = sessions.answer(cards.single(), AnswerInput.Choice(0))!!
        assertTrue(result.correct)
        assertEquals(false, result.counted)
        assertTrue(sessions.singleSession(999).isEmpty())
    }
}
