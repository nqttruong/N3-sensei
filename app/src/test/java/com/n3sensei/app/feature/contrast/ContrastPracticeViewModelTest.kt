package com.n3sensei.app.feature.contrast

import androidx.lifecycle.SavedStateHandle
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastFixtures
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.FakeContrastContent
import com.n3sensei.app.domain.contrast.FakeContrastStore
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ContrastPracticeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val clock = MutableClock(t0, ZoneId.of("Asia/Ho_Chi_Minh"))
    private val pack = ContrastFixtures.samplePack()
    private val content = FakeContrastContent(pack)
    private val store = FakeContrastStore(content)
    private val saved = SavedStateHandle()
    private var ids = 0
    private val grammarId = "cg-g-koto-ni-shiteiru-naru"
    private val vocabId = "cg-v-sameru-cold-awake"

    private fun vm(groupId: String? = grammarId, resume: Boolean = false, handle: SavedStateHandle = saved, config: ContrastConfig = ContrastConfig()) =
        ContrastPracticeViewModel(content, store, store, clock, handle, groupId, resume, config, newId = { "id-${++ids}" })

    private fun ContrastPracticeViewModel.ready() = state.value as PracticeUiState.Ready
    private fun ContrastPracticeViewModel.feedback() = (state.value as PracticeUiState.Feedback).feedback
    private fun ContrastPracticeViewModel.answer(correct: Boolean) {
        val q = ready().item.question
        select(if (correct) q.correctOptionId else q.options.first { it.id != q.correctOptionId }.id)
        submit()
    }

    @Test
    fun startsAtTheFirstQuestion_andViewingWritesNothing() = runTest {
        val vm = vm()
        val ready = vm.ready()
        assertEquals(0 to 3, ready.item.position to ready.item.total)
        assertEquals("cq-g-koto-mc1", ready.item.question.id)
        assertNull(ready.selectedOptionId)
        assertEquals(1, store.sessions.size)
        assertTrue(store.attempts.isEmpty() && store.progressRows.isEmpty() && store.reviewRows.isEmpty())
    }

    @Test
    fun correct_wrong_andSkipped_feedbackComesFromTheStoredResult() = runTest {
        val vm = vm()
        vm.answer(correct = true)
        val ok = vm.feedback()
        assertEquals(AnswerOutcome.CORRECT, ok.outcome)
        assertEquals(content.getQuestion("cq-g-koto-mc1")!!.explanationVi, ok.explanationVi)
        assertNull(ok.confusion)
        vm.next()

        vm.answer(correct = false) // fb1: target natteiru, chose shiteiru
        val wrong = vm.feedback()
        assertEquals(AnswerOutcome.INCORRECT, wrong.outcome)
        assertEquals("ci-g-koto-ni-natteiru" to "ci-g-koto-ni-shiteiru", wrong.confusion)
        assertTrue(wrong.retryAdded)
        vm.next()

        vm.skip()
        val skipped = vm.feedback()
        assertEquals(AnswerOutcome.SKIPPED, skipped.outcome)
        assertNull(skipped.chosenOptionId)
        assertNull(skipped.confusion) // a skip is never a confusion
        assertEquals(listOf(AnswerOutcome.CORRECT, AnswerOutcome.INCORRECT, AnswerOutcome.SKIPPED), store.attempts.map { it.outcome })
    }

    @Test
    fun leavingBeforeSubmitting_writesNothing_andTheDraftComesBack() = runTest {
        val first = vm()
        first.select("a")
        // The screen is closed (or the process dies): no attempt, the position stays pending.
        assertTrue(store.attempts.isEmpty())
        val again = vm()
        assertEquals(0, again.ready().item.position)
        assertEquals("a", again.ready().selectedOptionId)
        assertEquals(1, store.sessions.size) // resumed, not a second session
    }

    @Test
    fun processRecreation_resumesAfterTheLastStoredAnswer() = runTest {
        val first = vm()
        first.answer(correct = true)
        val recreated = vm(handle = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertEquals(1, recreated.ready().item.position)
        assertEquals(1, recreated.ready().item.done)
        assertEquals(1, store.attempts.size)
    }

    @Test
    fun aSecondTap_doesNotSubmitTwice() = runTest {
        val vm = vm()
        vm.select(vm.ready().item.question.correctOptionId)
        vm.submit()
        vm.submit()
        vm.skip()
        assertEquals(1, store.attempts.size)
    }

    @Test
    fun aFailedSave_keepsTheChoice_andTheRetryUsesTheSameRequestId() = runTest {
        val vm = vm()
        vm.select("b")
        store.failNextWrite = true
        vm.submit()
        val failed = vm.ready()
        assertTrue(failed.saveFailed)
        assertEquals("b", failed.selectedOptionId)
        assertTrue(store.attempts.isEmpty())
        val requestId = saved.keys().first { it.startsWith("contrast_request_") }.let { saved.get<String>(it) }
        vm.submit()
        assertEquals(AnswerOutcome.CORRECT, vm.feedback().outcome)
        assertEquals(listOf(requestId), store.attempts.map { it.requestId })
        assertTrue(saved.keys().none { it.startsWith("contrast_request_") })
    }

    @Test
    fun anAnswerStoredBefore_isShownAsStored_neverGradedTwice() = runTest {
        val vm = vm()
        val sessionId = vm.ready().item.sessionId
        // Stored with this position's request id, but the screen did not get the result (app killed).
        saved["contrast_request_${sessionId}_0"] = "known-request"
        SubmitContrastAnswer(content, store, store)(sessionId, 0, AnswerInput.Chosen("a"), "known-request", t0, clock.zone)
        vm.select("b")
        vm.submit()
        val shown = vm.feedback()
        assertEquals(AnswerOutcome.INCORRECT, shown.outcome) // what was stored ("a"), not the new choice
        assertEquals("a", shown.chosenOptionId)
        assertEquals(1, store.attempts.size)
    }

    @Test
    fun aQuestionRemovedFromThePack_isUnavailable_neverWrong() = runTest {
        val vm = vm()
        val current = vm.ready().item.question.id
        content.update(pack.copy(groups = pack.groups.map { g -> g.copy(questions = g.questions.filter { it.id != current }) }))
        vm.select("b")
        vm.submit()
        assertTrue(vm.state.value is PracticeUiState.QuestionUnavailable)
        assertTrue(store.attempts.isEmpty())
        vm.next()
        assertEquals(1, vm.ready().item.position)
        assertEquals(1, vm.ready().item.done) // the unavailable one counts as done, not as wrong
    }

    @Test
    fun aRevisedQuestion_isGradedOnItsCurrentRevision_andRecordsIt() = runTest {
        val vm = vm()
        val revised = pack.copy(groups = pack.groups.map { g ->
            g.copy(questions = g.questions.map { if (it.id == "cq-g-koto-mc1") it.copy(revision = 2, explanationVi = "Giải thích đã sửa.") else it })
        })
        content.update(revised)
        vm.select("b")
        vm.submit()
        assertEquals("Giải thích đã sửa.", vm.feedback().explanationVi)
        assertEquals(2, store.attempts.single().questionRevision)
    }

    @Test
    fun optionsChangedByThePack_askToChooseAgain() = runTest {
        val vm = vm()
        vm.select("c")
        content.update(pack.copy(groups = pack.groups.map { g ->
            g.copy(questions = g.questions.map { q -> if (q.id == "cq-g-koto-mc1") q.copy(revision = 2, options = q.options.filter { it.id != "c" }) else q })
        }))
        vm.submit()
        val again = vm.ready()
        assertTrue(again.contentChanged)
        assertNull(again.selectedOptionId)
        assertEquals(2, again.item.question.options.size)
        assertTrue(store.attempts.isEmpty())
    }

    @Test
    fun completingTheSession_schedulesOnce_andTheSummaryCounts() = runTest {
        val vm = vm()
        repeat(3) {
            vm.answer(correct = true)
            if (it < 2) vm.next()
        }
        val last = vm.feedback()
        assertTrue(last.sessionCompleted)
        assertNotNull(last.nextDueAt)
        vm.next()
        val sessionId = (vm.state.value as PracticeUiState.Completed).sessionId
        assertEquals(SessionStatus.COMPLETED, store.sessions.getValue(sessionId).status)
        assertEquals(1, store.reviewRows.size)
        assertEquals(1, store.progressRows.getValue(grammarId).reviewCount)

        val summary = ContrastSessionSummaryViewModel(sessionId, content, store, store, clock)
        val data = (summary.state.value as UiState.Success).data
        assertEquals(Triple(3, 0, 0), Triple(data.correct, data.incorrect, data.skipped))
        assertEquals(3, data.handled)
        assertEquals(1.0, data.accuracy.value!!, 0.0)
        assertEquals(ContrastMastery.LEARNING, data.groups.single().mastery)
        assertEquals(t0.plus(Duration.ofDays(3)), data.groups.single().nextDueAt)
        assertEquals(3, data.answers.size)
    }

    @Test
    fun summary_skipsAreNotWrong_andNoAccuracyWithoutAnswers() = runTest {
        val vm = vm(groupId = vocabId)
        vm.skip()
        vm.next()
        vm.skip()
        vm.next()
        val sessionId = (vm.state.value as PracticeUiState.Completed).sessionId
        val data = (ContrastSessionSummaryViewModel(sessionId, content, store, store, clock).state.value as UiState.Success).data
        assertEquals(Triple(0, 0, 2), Triple(data.correct, data.incorrect, data.skipped))
        assertNull(data.accuracy.value)
        assertTrue(store.reviewRows.isEmpty()) // too many skips: no schedule
    }

    @Test
    fun anotherUnfinishedSession_isOffered_neverSilentlyReplaced() = runTest {
        val grammar = vm()
        grammar.answer(correct = true) // grammar session: 2 questions left
        val vocab = vm(groupId = vocabId, handle = SavedStateHandle())
        val confirm = vocab.state.value as PracticeUiState.ConfirmReplace
        assertEquals(2, confirm.remaining)
        vocab.continueActive()
        assertEquals(grammarId, vocab.ready().item.question.groupId)
        assertEquals(1, store.sessions.size)

        val vocab2 = vm(groupId = vocabId, handle = SavedStateHandle())
        vocab2.startRequested()
        assertEquals(vocabId, vocab2.ready().item.question.groupId)
        assertEquals(listOf(SessionStatus.ABANDONED, SessionStatus.ACTIVE), store.sessions.values.map { it.status })
        assertEquals(1, store.attempts.size) // the abandoned session's answer is kept
    }

    @Test
    fun resume_continuesTheActiveSession() = runTest {
        vm(groupId = vocabId).answer(correct = true)
        val resumed = vm(groupId = null, resume = true, handle = SavedStateHandle())
        assertEquals(1, resumed.ready().item.position)
        assertEquals(vocabId, resumed.ready().item.question.groupId)
    }

    @Test
    fun nothingToPractice_andTheDailyNewLimit() = runTest {
        val empty = FakeContrastContent(pack.copy(groups = emptyList()))
        val nothing = ContrastPracticeViewModel(empty, FakeContrastStore(empty), FakeContrastStore(empty), clock, SavedStateHandle(), null)
        assertEquals(PracticeUiState.NothingToPractice, nothing.state.value)
        // Due scope with 1 new group a day: one group only.
        val oneNew = vm(groupId = null, config = ContrastConfig(newGroupsPerDay = 1), handle = SavedStateHandle())
        val session = store.sessions.values.single()
        assertEquals(listOf(grammarId), session.items.map { it.groupId }.distinct())
        assertEquals(grammarId, oneNew.ready().item.question.groupId)
    }
}
