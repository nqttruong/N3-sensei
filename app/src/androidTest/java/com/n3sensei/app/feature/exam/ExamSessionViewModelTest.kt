package com.n3sensei.app.feature.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AnswerManager
import com.n3sensei.app.domain.exam.ExamSessionManager
import com.n3sensei.app.domain.exam.ExamSessionStatus
import com.n3sensei.app.domain.exam.ExamSubmissionManager
import com.n3sensei.app.testing.ExamTestData
import com.n3sensei.app.testing.ExamTestData.Q_FILL
import com.n3sensei.app.testing.ExamTestData.Q_KANJI
import com.n3sensei.app.testing.ExamTestData.Q_READ_1
import com.n3sensei.app.testing.ExamTestData.Q_READ_2
import com.n3sensei.app.testing.ExamTestData.SET_ID
import com.n3sensei.app.testing.MutableTestClock
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ExamSessionViewModel] on a real database. A ViewModel kept in its store stands for a
 * rotation; a new one from a copy of the saved state, after clearing the store, stands for the
 * Activity recreated after the process died. Timer ticks are sent by hand; time is the clock.
 */
@RunWith(AndroidJUnit4::class)
class ExamSessionViewModelTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "exam-session-vm-test.db"
    private val start = 1_000_000L
    private val limit = 1_800_000L
    private val clock = MutableTestClock(start)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private val ticks = MutableSharedFlow<Unit>()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        runBlocking { exams.saveExamSet(ExamTestData.set()) }
    }

    @After
    fun tearDown() {
        onMain { stores.forEach { it.clear() } }
        db.close()
        context.deleteDatabase(name)
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private class Screen(val store: ViewModelStore, val saved: SavedStateHandle, val factory: ViewModelProvider.Factory) {
        val viewModel: ExamSessionViewModel get() = ViewModelProvider(store, factory)[ExamSessionViewModel::class.java]
    }

    private fun screen(saved: SavedStateHandle = SavedStateHandle()): Screen {
        val submission = ExamSubmissionManager(exams, clock)
        val factory = viewModelFactory {
            initializer {
                ExamSessionViewModel(SET_ID, saved, ExamSessionManager(exams, submission), AnswerManager(exams, clock), submission, clock, ticks)
            }
        }
        val store = ViewModelStore().also { stores += it }
        return Screen(store, saved, factory).also { s -> onMain { s.viewModel } }
    }

    /** The process dies: the ViewModel is gone, only the saved state is left for the new Activity. */
    private fun Screen.afterProcessDeath(): Screen {
        val copy = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })
        onMain { store.clear() }
        return screen(copy)
    }

    private fun Screen.await(condition: (ExamSessionUiState) -> Boolean): ExamSessionUiState = runBlocking {
        withTimeout(5_000) { viewModel.state.mapNotNull { (it as? UiState.Success)?.data }.first(condition) }
    }

    private fun tick() = runBlocking {
        withTimeout(5_000) { ticks.subscriptionCount.first { it > 0 } }
        ticks.emit(Unit)
    }

    @Test
    fun beforeTheStart_theSetIsShownNotStarted_thenStartingMovesAnswersChangesAndFlags() {
        val s = screen()
        val ready = s.await { it.status == ExamSessionStatus.NOT_STARTED }
        assertNull(ready.attempt)
        assertEquals(limit, ready.remainingMillis)
        assertNull(ready.unfinished)

        onMain { s.viewModel.start() }
        s.await { it.status == ExamSessionStatus.IN_PROGRESS }
        onMain { s.viewModel.next() }
        assertEquals(Q_FILL, s.await { it.currentIndex == 1 }.currentQuestion!!.id)
        onMain { s.viewModel.previous() }
        s.await { it.currentIndex == 0 }
        onMain { s.viewModel.previous() } // nothing before the first
        onMain { s.viewModel.goTo(2) }
        assertEquals(Q_READ_1, s.await { it.currentIndex == 2 }.currentQuestion!!.id)

        onMain { s.viewModel.answer(AnswerInput.Choice(0)) }
        s.await { it.answers[Q_READ_1]?.selectedIndex == 0 }
        onMain { s.viewModel.answer(AnswerInput.Choice(1)) }
        val changed = s.await { it.answers[Q_READ_1]?.selectedIndex == 1 }
        assertEquals(1, changed.answers.getValue(Q_READ_1).changeCount)
        assertTrue(changed.answers.getValue(Q_READ_1).isCorrect)
        assertEquals(1, changed.answeredCount)

        onMain { s.viewModel.toggleFlag() }
        s.await { Q_READ_1 in it.flagged }
        onMain { s.viewModel.toggleFlag() }
        s.await { Q_READ_1 !in it.flagged }
        onMain { s.viewModel.goTo(99) }
        assertEquals(2, s.await { true }.currentIndex)
        assertEquals(1, db.countRows("exam_answers"))
    }

    @Test
    fun rotationKeepsTheViewModel_andAfterTheProcessDiesTheAttemptComesBackWhereItWas() {
        val s = screen()
        s.await { it.status == ExamSessionStatus.NOT_STARTED }
        val first = s.viewModel
        onMain { first.start() }
        s.await { it.status == ExamSessionStatus.IN_PROGRESS }
        onMain { first.goTo(3) }
        onMain { first.answer(AnswerInput.Choice(2)) }
        onMain { first.toggleFlag() }
        val before = s.await { Q_READ_2 in it.flagged && Q_READ_2 in it.answers }

        // Rotation: same store, same ViewModel, same state.
        assertSame(first, s.viewModel)

        clock.advance(120_000)
        val restored = s.afterProcessDeath()
        val after = restored.await { it.status == ExamSessionStatus.IN_PROGRESS }
        assertEquals(before.attempt!!.id, after.attempt!!.id)
        assertEquals(3, after.currentIndex)
        assertEquals(setOf(Q_READ_2), after.answers.keys)
        assertEquals(2, after.answers.getValue(Q_READ_2).selectedIndex)
        assertEquals(setOf(Q_READ_2), after.flagged)
        assertEquals(limit - 120_000, after.remainingMillis)
        assertEquals(1, db.countRows("exam_attempts"))

        // Opening the exam again from the list offers to continue the same attempt.
        val fromList = screen()
        val offer = fromList.await { it.status == ExamSessionStatus.NOT_STARTED }
        assertEquals(before.attempt.id, offer.unfinished!!.id)
        onMain { fromList.viewModel.resume() }
        assertEquals(before.attempt.id, fromList.await { it.status == ExamSessionStatus.IN_PROGRESS }.attempt!!.id)
    }

    @Test
    fun whenTheTimeRunsOut_theAttemptIsSubmittedWithItsAnswers_andTakesNoMoreChanges() {
        val s = screen()
        s.await { it.status == ExamSessionStatus.NOT_STARTED }
        onMain { s.viewModel.start() }
        s.await { it.status == ExamSessionStatus.IN_PROGRESS }
        onMain { s.viewModel.answer(AnswerInput.Choice(0)) } // Q_KANJI, right
        s.await { Q_KANJI in it.answers }

        clock.advance(limit - 60_000)
        tick()
        assertEquals(60_000L, s.await { it.remainingMillis == 60_000L }.remainingMillis)
        clock.advance(60_000)
        tick()
        val expired = s.await { it.status == ExamSessionStatus.EXPIRED }
        assertEquals(1, expired.result!!.attempt.correctCount)
        assertEquals(start + limit, expired.result.attempt.submittedAt)
        assertEquals(0L, expired.remainingMillis)

        onMain {
            s.viewModel.next()
            s.viewModel.answer(AnswerInput.Choice(3))
            s.viewModel.toggleFlag()
            s.viewModel.submit()
        }
        val still = s.await { true }
        assertEquals(ExamSessionStatus.EXPIRED, still.status)
        assertEquals(0, still.currentIndex)
        assertEquals(0, db.countRows("exam_flags"))
        assertEquals(1, db.countRows("exam_answers"))
        assertEquals(5, db.countRows("error_records WHERE error_count = 1"))
    }

    @Test
    fun timeRunningOutWhileTheAppIsClosed_showsTheExpiredResultOnReturn() {
        val s = screen()
        s.await { it.status == ExamSessionStatus.NOT_STARTED }
        onMain { s.viewModel.start() }
        s.await { it.status == ExamSessionStatus.IN_PROGRESS }
        onMain { s.viewModel.answer(AnswerInput.Choice(0)) }
        s.await { Q_KANJI in it.answers }

        clock.advance(2 * limit)
        val back = s.afterProcessDeath().await { it.status == ExamSessionStatus.EXPIRED }
        assertEquals(start + limit, back.result!!.attempt.submittedAt)
        assertEquals(1, back.result.attempt.correctCount)
        assertEquals(1, back.answers.size)
    }

    @Test
    fun submittingByHand_twiceAndWithTheTimer_givesOneResult() {
        val s = screen()
        s.await { it.status == ExamSessionStatus.NOT_STARTED }
        onMain { s.viewModel.start() }
        s.await { it.status == ExamSessionStatus.IN_PROGRESS }
        onMain { s.viewModel.answer(AnswerInput.Choice(1)) } // Q_KANJI, wrong
        s.await { Q_KANJI in it.answers }
        clock.advance(300_000)

        onMain {
            s.viewModel.submit()
            s.viewModel.submit()
        }
        val done = s.await { it.status == ExamSessionStatus.SUBMITTED }
        assertNotNull(done.result)
        assertEquals(300L, done.result!!.attempt.durationSeconds)
        assertEquals(1, db.countRows("exam_attempts WHERE status = 'SUBMITTED'"))
        assertEquals(6, db.countRows("error_records WHERE error_count = 1"))
        assertEquals(6, db.countRows("review_items"))

        // Coming back to it later shows the same result; nothing is submitted again.
        clock.advance(limit)
        val later = s.afterProcessDeath().await { it.status == ExamSessionStatus.SUBMITTED }
        assertEquals(done.result.attempt, later.result!!.attempt)
        assertEquals(6, db.countRows("error_records WHERE error_count = 1"))
    }
}
