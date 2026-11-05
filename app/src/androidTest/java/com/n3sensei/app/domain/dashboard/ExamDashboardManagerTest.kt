package com.n3sensei.app.domain.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineErrorRepository
import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.repository.SaveExamResult
import com.n3sensei.app.testing.ExamTestData
import com.n3sensei.app.testing.ExamTestData.Q_FILL
import com.n3sensei.app.testing.ExamTestData.Q_KANJI
import com.n3sensei.app.testing.ExamTestData.Q_READ_1
import com.n3sensei.app.testing.ExamTestData.Q_READ_2
import com.n3sensei.app.testing.ExamTestData.SET_ID
import com.n3sensei.app.testing.InMemoryPreferencesRepository
import com.n3sensei.app.testing.MutableTestClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The dashboard on a real database with TEST DATA ONLY exams: empty, a little, and more data. */
@RunWith(AndroidJUnit4::class)
class ExamDashboardManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "dashboard-test.db"
    private val day = 24 * 60 * 60_000L
    private val clock = MutableTestClock(1_800_000_000_000)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var prefs: InMemoryPreferencesRepository
    private lateinit var dashboard: ExamDashboardManager

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        prefs = InMemoryPreferencesRepository()
        dashboard = ExamDashboardManager(
            exams, OfflineErrorRepository(db, clock), OfflineProgressRepository(db.progressDao(), clock),
            OfflineContentRepository(db.contentDao(), db.contentPackDao()), prefs, clock,
        )
        runBlocking { assertEquals(SaveExamResult.Saved, exams.saveExamSet(ExamTestData.set())) }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private suspend fun take(picks: Map<String, AnswerInput>) {
        val a = exams.startAttempt(SET_ID)!!
        picks.forEach { (id, input) -> exams.answer(a.id, id, input, 2_000) }
        exams.submit(a.id)
    }

    @Test
    fun noExamYet_showsZeros_andAnEmptyPlan() = runBlocking {
        val d = dashboard.load()
        assertFalse(d.hasHistory)
        assertEquals(ExamOverview(0, Tally.EMPTY, 0, 0, 0), d.overview)
        assertTrue(d.skills.isEmpty())
        assertTrue(d.categories.isEmpty())
        assertTrue(d.plan.tasks.isEmpty())
        assertEquals(20, d.plan.budgetMinutes)
    }

    @Test
    fun oneExam_isCountedExactly_butNoRateIsGivenOnTooFewQuestions() = runBlocking {
        // Kanji right, fill right, reading 1 wrong; 3 blank.
        take(mapOf(Q_KANJI to AnswerInput.Choice(0), Q_FILL to AnswerInput.Text("ため"), Q_READ_1 to AnswerInput.Choice(0)))
        val d = dashboard.load()
        assertTrue(d.hasHistory)
        assertEquals(1, d.overview.examsTaken)
        assertEquals(Tally(total = 6, answered = 3, correct = 2), d.overview.questions)
        assertFalse(d.overview.questions.reliable)
        assertEquals(4, d.overview.openErrors)
        assertEquals(0, d.overview.dueToday) // first reviews are tomorrow
        assertEquals(listOf(ExamSkill.KANJI, ExamSkill.GRAMMAR, ExamSkill.READING, ExamSkill.LISTENING), d.skills.map { it.skill })
        assertEquals(Tally(2, 1, 0), d.skills.single { it.skill == ExamSkill.READING }.overall)
        assertTrue(d.weakness.skills.none { it.reliable })
        assertTrue(d.plan.tasks.isEmpty()) // nothing due, no weak item found in the content
    }

    @Test
    fun moreExams_dueErrorsGoIntoAPlanThatFitsTheBudget() = runBlocking {
        repeat(3) {
            take(mapOf(Q_KANJI to AnswerInput.Choice(1), Q_READ_1 to AnswerInput.Choice(1), Q_READ_2 to AnswerInput.Choice(0)))
            clock.advance(day)
        }
        val d = dashboard.load()
        assertEquals(3, d.overview.examsTaken)
        assertEquals(Tally(18, 9, 3), d.overview.questions)
        assertTrue(d.overview.questions.reliable)
        assertEquals(5, d.overview.dueToday)
        assertEquals(5, d.plan.tasks.filterIsInstance<PlanTask.ReviewErrors>().sumOf { it.count })
        assertTrue(d.plan.seconds <= 20 * 60)
        assertEquals(1, d.weakness.recurring.count { it.questionId == Q_KANJI })

        dashboard.setBudget(5)
        val small = dashboard.load()
        assertEquals(5, small.plan.budgetMinutes)
        assertEquals(3, small.plan.tasks.filterIsInstance<PlanTask.ReviewErrors>().sumOf { it.count })
        assertEquals(2, small.plan.errorsLeft)
        assertTrue(small.plan.seconds <= 5 * 60)

        val kanji = dashboard.skillDetail(ExamSkill.KANJI)
        assertEquals(Tally(3, 3, 0), kanji.stats!!.overall)
        assertEquals(8, kanji.weekly.size)
        assertEquals(3, kanji.weekly.sumOf { it.tally.total })
        assertEquals(1, kanji.openErrors)
    }
}
