package com.n3sensei.app.feature.contrast

import androidx.lifecycle.SavedStateHandle
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastAnalyticsPolicy
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastFixtures
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastProgress
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.ContrastReview
import com.n3sensei.app.domain.contrast.DirectionStatus
import com.n3sensei.app.domain.contrast.FakeContrastAnalytics
import com.n3sensei.app.domain.contrast.FakeContrastContent
import com.n3sensei.app.domain.contrast.FakeContrastStore
import com.n3sensei.app.domain.contrast.GetContrastErrorAnalysis
import com.n3sensei.app.domain.contrast.GetContrastProgressReport
import com.n3sensei.app.domain.contrast.GetContrastReviewQueue
import com.n3sensei.app.domain.contrast.GetDueContrastReviews
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.contrast.SubmitOutcome
import com.n3sensei.app.domain.contrast.SubmitRejection
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.repository.AttemptFilter
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** M7: error analysis, smart review and progress (use cases + ViewModels on in-memory fakes). */
class ContrastInsightsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val clock = MutableClock(t0, ZoneId.of("Asia/Ho_Chi_Minh"))
    private val pack = ContrastFixtures.samplePack()
    private val content = FakeContrastContent(pack)
    private val store = FakeContrastStore(content)
    private val analytics = FakeContrastAnalytics(content, store)
    private val grammar = "cg-g-koto-ni-shiteiru-naru"
    private val vocab = "cg-v-sameru-cold-awake"
    private val shiteiru = "ci-g-koto-ni-shiteiru"
    private val natteiru = "ci-g-koto-ni-natteiru"
    private var ids = 0
    private val start get() = StartOrResumePractice(content, store, store, newId = { "s${++ids}" })
    private val submit get() = SubmitContrastAnswer(content, store, store)

    // ---- helpers ----

    private var n = 0

    /** A stored answer built directly (for directions not in the sample pack, e.g. 3-item groups). */
    private fun attempt(session: String, minutes: Long, outcome: AnswerOutcome, expected: String? = "A", chosen: String? = null, group: String = "g3", type: ContrastQuestionType = ContrastQuestionType.MULTIPLE_CHOICE): ContrastAttempt {
        n++
        val chosenItem = chosen ?: if (outcome == AnswerOutcome.CORRECT) expected else null
        return ContrastAttempt(
            "r$n", session, n, group, 1, "q$n", 1, type, "p", 1, outcome,
            if (outcome == AnswerOutcome.SKIPPED) null else "o", "o", expected, if (outcome == AnswerOutcome.SKIPPED) null else chosenItem,
            if (outcome == AnswerOutcome.SKIPPED) null else "chosen$n", "correct$n", t0.plus(Duration.ofMinutes(minutes)),
        )
    }

    /** Plays one session on [scope]: answers each planned position with [answer] (option id, or null = skip). */
    private suspend fun play(scope: PracticeScope, answer: (questionId: String, correct: String) -> String?): String {
        val session = when (val r = start(scope, clock.instant(), clock.zone)) {
            is StartPracticeResult.Started -> r.session
            is StartPracticeResult.Resumed -> r.session
            StartPracticeResult.NothingToPractice -> error("nothing to practice")
        }
        var position = 0
        while (true) {
            val item = store.session(session.id)!!.items.firstOrNull { it.position == position } ?: break
            val q = content.getQuestion(item.questionId)!!
            val chosen = answer(q.id, q.correctOptionId)
            submit(session.id, position, chosen?.let { AnswerInput.Chosen(it) } ?: AnswerInput.Skipped, "req-${session.id}-$position", clock.instant(), clock.zone)
            clock.advance(Duration.ofSeconds(10))
            position++
        }
        analytics.changes.value++
        return session.id
    }

    private fun wrong(q: String, correct: String) = if (correct == "a") "b" else "a"

    private fun <T> UiState<T>.data() = (this as UiState.Success).data

    // ---- A. analysis ----

    @Test
    fun directions_areDirected_threeItemGroupsKeepEveryPair_andSkipsAreNotWrong() = runTest {
        store.attempts += listOf(
            attempt("s1", 0, AnswerOutcome.INCORRECT, "A", "B"),
            attempt("s2", 60, AnswerOutcome.INCORRECT, "A", "B"),
            attempt("s2", 61, AnswerOutcome.INCORRECT, "B", "A"),
            attempt("s2", 62, AnswerOutcome.INCORRECT, "A", "C"),
            attempt("s2", 63, AnswerOutcome.INCORRECT, "A", null), // a distractor: wrong, not a confusion
            attempt("s2", 64, AnswerOutcome.CORRECT, "C"),
            attempt("s2", 65, AnswerOutcome.SKIPPED),
        )
        val report = GetContrastErrorAnalysis(analytics)()
        val keys = report.directions.map { Triple(it.count.expectedItemId, it.count.chosenItemId, it.count.count) }
        assertEquals(listOf(Triple("A", "B", 2), Triple("A", "C", 1), Triple("B", "A", 1)), keys)
        assertEquals(DirectionStatus.TO_REINFORCE, report.directions[0].status) // A→B in 2 sessions
        assertTrue(report.directions.drop(1).all { it.status == DirectionStatus.SINGLE })
        assertEquals(1, report.otherIncorrect)
        assertEquals(7, report.totals.total)
        assertEquals(5, report.totals.incorrect)
        assertEquals(1, report.totals.skipped)
        assertEquals(1, report.totals.accuracy.correct)
        assertEquals(6, report.totals.accuracy.answered) // skipped not in the denominator
    }

    @Test
    fun nothingAnswered_noAccuracy_noTrend_noDivisionByZero() = runTest {
        val report = GetContrastErrorAnalysis(analytics)()
        assertEquals(0, report.totals.total)
        assertNull(report.totals.accuracy.value)
        assertNull(report.trend.recent)
        assertNull(report.trend.earlier)
        assertTrue(report.directions.isEmpty())
        val rate = RateView(report.totals.accuracy, 5)
        assertFalse(rate.reliable)
    }

    @Test
    fun aRepeatedMistake_needsSeveralLaterSessions_notOneCorrectAnswer() = runTest {
        val policy = ContrastAnalyticsPolicy(repeatedMinSessions = 2, settledAfterSessions = 2)
        store.attempts += attempt("s1", 0, AnswerOutcome.INCORRECT, "A", "B")
        store.attempts += attempt("s2", 60, AnswerOutcome.INCORRECT, "A", "B")
        store.attempts += attempt("s2", 61, AnswerOutcome.CORRECT, "A") // same session as the last error: does not count
        fun status() = kotlinx.coroutines.runBlocking { GetContrastErrorAnalysis(analytics, policy)().directions.single().status }
        assertEquals(DirectionStatus.TO_REINFORCE, status())
        store.attempts += attempt("s3", 120, AnswerOutcome.CORRECT, "A")
        assertEquals(DirectionStatus.TO_REINFORCE, status()) // one later session is not enough
        store.attempts += attempt("s4", 180, AnswerOutcome.SKIPPED)
        assertEquals(DirectionStatus.TO_REINFORCE, status()) // a skipped-only session is no evidence
        store.attempts += attempt("s5", 240, AnswerOutcome.CORRECT, "A")
        assertEquals(DirectionStatus.SETTLING, status())
        // A higher threshold comes from the policy, never from the UI.
        assertEquals(DirectionStatus.TO_REINFORCE, GetContrastErrorAnalysis(analytics, ContrastAnalyticsPolicy(settledAfterSessions = 3))().directions.single().status)
    }

    @Test
    fun pendingAndUnavailable_areNotCounted_andARetryIsCountedOnce() = runTest {
        val session = (start(PracticeScope.Group(grammar), t0, clock.zone) as StartPracticeResult.Started).session
        val q0 = content.getQuestion(session.items[0].questionId)!!
        submit(session.id, 0, AnswerInput.Chosen(wrong(q0.id, q0.correctOptionId)), "same", t0, clock.zone)
        assertEquals(SubmitOutcome.Duplicate, submit(session.id, 0, AnswerInput.Chosen(q0.correctOptionId), "same", t0, clock.zone))
        // Position 1 removed from the pack: unavailable, rejected, never stored.
        val removed = session.items[1].questionId
        content.update(pack.copy(groups = pack.groups.map { g -> g.copy(questions = g.questions.filter { it.id != removed }) }))
        assertEquals(SubmitOutcome.Rejected(SubmitRejection.QUESTION_UNAVAILABLE), submit(session.id, 1, AnswerInput.Chosen("a"), "x", t0, clock.zone))
        // Position 2 stays pending.
        val totals = analytics.totals()
        assertEquals(1, totals.total)
        assertEquals(1, totals.incorrect)
        assertEquals(1, GetContrastErrorAnalysis(analytics)().directions.single().count.count)
    }

    // ---- Errors ViewModel ----

    private fun errorsVm(saved: SavedStateHandle = SavedStateHandle()) =
        ContrastErrorsViewModel(content, analytics, saved, retiredLabel = { "retired:$it" })

    private suspend fun kotlinx.coroutines.test.TestScope.collect(vm: ContrastErrorsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

    @Test
    fun errors_emptyState_beforeAnyMistake() = runTest {
        val vm = errorsVm()
        collect(vm)
        val data = vm.state.value.data()
        assertEquals(0, data.totals.incorrect)
        assertEquals(0, data.rate.accuracy.answered)
        assertTrue(data.directions.isEmpty())
        assertTrue(store.sessions.isEmpty() && store.attempts.isEmpty())
    }

    @Test
    fun errors_labelsDirectionsByItem_keepTheOldSnapshot_andFiltersSurviveRecreation() = runTest {
        play(PracticeScope.Group(grammar), ::wrong)
        clock.advance(Duration.ofDays(1))
        play(PracticeScope.Group(vocab), ::wrong)
        // The pack revises mc1 afterwards: history keeps what was answered.
        content.update(pack.copy(groups = pack.groups.map { g -> g.copy(questions = g.questions.map { if (it.id == "cq-g-koto-mc1") it.copy(revision = 2) else it }) }))
        val saved = SavedStateHandle()
        val vm = errorsVm(saved)
        collect(vm)
        val all = vm.state.value.data()
        val mc1 = all.directions.first { it.groupId == grammar && it.expectedLabel == "〜ことになっている" && it.chosenLabel == "〜ことにしている" }
        assertTrue(mc1.published)
        assertEquals(store.attempts.filter { it.groupId == grammar && it.expectedItemId == natteiru && it.chosenItemId == shiteiru }.maxBy { it.answeredAt }.chosenText, mc1.example!!.chosenText)
        // natteiru→shiteiru and shiteiru→natteiru both happened: two rows.
        assertTrue(all.directions.any { it.groupId == grammar && it.expectedLabel == "〜ことにしている" && it.chosenLabel == "〜ことになっている" })

        vm.selectContentType(ContrastContentType.VOCABULARY)
        assertTrue(vm.state.value.data().directions.all { it.groupId == vocab })
        vm.selectQuestionType(ContrastQuestionType.CONTEXT_SELECTION)
        assertEquals(1, vm.state.value.data().totals.total)
        val recreated = errorsVm(saved)
        collect(recreated)
        val kept = recreated.state.value.data()
        assertEquals(ContrastContentType.VOCABULARY to ContrastQuestionType.CONTEXT_SELECTION, kept.contentType to kept.questionType)
        recreated.selectContentType(null)
        recreated.selectQuestionType(null)
        recreated.selectGroup(grammar)
        assertTrue(recreated.state.value.data().directions.all { it.groupId == grammar })
    }

    @Test
    fun errors_ofARetiredGroup_stayInHistory_underItsOwnId() = runTest {
        play(PracticeScope.Group(vocab), ::wrong)
        content.update(pack.copy(groups = pack.groups.filter { it.detail.group.id != vocab }, retiredGroups = listOf(RetiredGroup(vocab, 2, "Gộp nhóm", "cg-v-new"))))
        val vm = errorsVm()
        collect(vm)
        val data = vm.state.value.data()
        // cold→awake and awake→cold: two directions, both kept under the retired group's own id.
        assertEquals(2, data.directions.size)
        data.directions.forEach { row ->
            assertEquals("retired:$vocab", row.groupTitle)
            assertFalse(row.published)
            assertEquals(vocab, row.groupId) // never moved to the replacement id
        }
        assertEquals(2, data.totals.incorrect)
        val report = GetContrastProgressReport(content, store, store, analytics)(clock.instant(), clock.zone)
        assertEquals(2, report.historical.total)
        assertEquals(2, report.published)
    }

    // ---- B. smart review ----

    private val queue get() = GetContrastReviewQueue(content, store, store, analytics)

    @Test
    fun reviewQueue_dueUpcomingAndTheLadder_andViewingWritesNothing() = runTest {
        play(PracticeScope.Group(grammar)) { _, c -> c } // GOOD → step 1 (3 days)
        val sessions = store.sessions.size
        val q = queue(clock.instant(), clock.zone)
        assertTrue(q.due.isEmpty())
        val entry = q.upcoming.single()
        assertEquals(1 to 3, entry.step to entry.interval.amount)
        val dueAt = store.progressRows.getValue(grammar).dueAt
        assertEquals(dueAt, q.nextDueAt)
        assertTrue(queue(dueAt.minusMillis(1), clock.zone).due.isEmpty())
        assertEquals(listOf(grammar), queue(dueAt, clock.zone).due.map { it.group.id })
        // Reading the queue (and the screens' ViewModels) wrote nothing.
        val vm = ContrastReviewViewModel(content, store, store, analytics, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(1, vm.state.value.data().upcoming.size)
        assertEquals(sessions, store.sessions.size)
        assertEquals(1, store.reviewRows.size)
        assertEquals(1, store.progressRows.getValue(grammar).reviewCount)
    }

    @Test
    fun dueDates_followCalendarDays_acrossMidnightZonesAndDaylightSaving() = runTest {
        val berlin = ZoneId.of("Europe/Berlin")
        // Saturday 24 Oct 2026 23:30 (CEST); DST ends on Sunday 25 Oct.
        clock.now = LocalDateTime.of(2026, 10, 24, 23, 30).atZone(berlin).toInstant()
        clock.currentZone = berlin
        play(PracticeScope.Group(grammar)) { _, c -> c }
        val completed = store.reviewRows.single().reviewedAt
        val stored = store.progressRows.getValue(grammar).dueAt
        assertEquals(completed.atZone(berlin).plusDays(3).toInstant(), stored) // 3 calendar days at the same local time
        assertEquals(Duration.ofHours(73), Duration.between(completed, stored)) // not a naive 72 h
        assertEquals(LocalDateTime.of(2026, 10, 27, 23, 30), stored.atZone(berlin).toLocalDateTime().withSecond(0))
        // Not due one hour before (a naive +72 h would say due), due at the local time.
        assertTrue(queue(stored.minus(Duration.ofHours(1)), berlin).due.isEmpty())
        assertEquals(1, queue(stored, berlin).due.size)
        // The learner travels: the stored instant decides, whatever the zone.
        assertEquals(1, queue(stored, ZoneId.of("America/New_York")).due.size)
        assertTrue(queue(stored.minusSeconds(1), ZoneId.of("Asia/Tokyo")).due.isEmpty())
    }

    @Test
    fun reviewSession_dueGroupsOnly_newAllowanceUntouched_scheduledOnce_andResumed() = runTest {
        play(PracticeScope.Group(grammar)) { _, c -> c }
        clock.now = store.progressRows.getValue(grammar).dueAt
        val due = GetDueContrastReviews(store)
        val before = due(clock.instant(), clock.zone).newAvailableToday
        val session = (start(PracticeScope.Review, clock.instant(), clock.zone) as StartPracticeResult.Started).session
        assertEquals(listOf(grammar), session.items.map { it.groupId }.distinct()) // no new group
        // Resumed, not a second session.
        assertTrue(start(PracticeScope.Review, clock.instant(), clock.zone) is StartPracticeResult.Resumed)
        play(PracticeScope.Review) { _, c -> c }
        assertEquals(before, due(clock.instant(), clock.zone).newAvailableToday)
        assertEquals(2, store.progressRows.getValue(grammar).reviewCount) // + exactly one
        assertEquals(2, store.reviewRows.size)
        // Nothing due any more: nothing to review, and no session is created.
        val count = store.sessions.size
        assertEquals(StartPracticeResult.NothingToPractice, start(PracticeScope.Review, clock.instant(), clock.zone))
        assertEquals(count, store.sessions.size)
    }

    @Test
    fun aReviewWithoutAnyValidQuestion_schedulesNothing() = runTest {
        play(PracticeScope.Group(grammar)) { _, c -> c }
        clock.now = store.progressRows.getValue(grammar).dueAt
        val session = (start(PracticeScope.Review, clock.instant(), clock.zone) as StartPracticeResult.Started).session
        content.update(pack.copy(groups = pack.groups.map { g -> if (g.detail.group.id == grammar) g.copy(questions = emptyList()) else g }))
        session.items.forEach { item ->
            assertEquals(SubmitOutcome.Rejected(SubmitRejection.QUESTION_UNAVAILABLE), submit(session.id, item.position, AnswerInput.Chosen("a"), "u${item.position}", clock.instant(), clock.zone))
        }
        assertEquals(1, store.progressRows.getValue(grammar).reviewCount)
        assertEquals(1, store.reviewRows.size)
        // The practice screen closes such a session without grading anything.
        val vm = ContrastPracticeViewModel(content, store, store, clock, SavedStateHandle(), null, resume = true)
        assertTrue(vm.state.value is PracticeUiState.Completed)
        assertEquals(1, store.reviewRows.size)
    }

    @Test
    fun theReviewButton_opensTheM6PracticeOnDueGroups_orNothing() = runTest {
        val nothing = ContrastPracticeViewModel(content, store, store, clock, SavedStateHandle(), null, review = true)
        assertEquals(PracticeUiState.NothingToPractice, nothing.state.value)
        assertTrue(store.sessions.isEmpty())
        play(PracticeScope.Group(vocab)) { _, c -> c }
        clock.now = store.progressRows.getValue(vocab).dueAt
        val vm = ContrastPracticeViewModel(content, store, store, clock, SavedStateHandle(), null, review = true)
        assertEquals(vocab, (vm.state.value as PracticeUiState.Ready).item.question.groupId)
        assertEquals(PracticeScope.Review, store.activeSession()!!.scope)
    }

    // ---- C. progress ----

    private val report get() = GetContrastProgressReport(content, store, store, analytics)

    @Test
    fun progress_notStarted_learning_due_byCategory_andAnswerCounts() = runTest {
        val empty = report(clock.instant(), clock.zone)
        assertEquals(listOf(3, 3, 0, 0, 0), listOf(empty.published, empty.notStarted, empty.learning, empty.due, empty.mastered))
        assertEquals(0, empty.totals.total)
        play(PracticeScope.Group(grammar)) { q, c -> if (q == "cq-g-koto-mc1") wrong(q, c) else c } // 2/3: HARD, scheduled
        val after = report(clock.instant(), clock.zone)
        assertEquals(listOf(2, 1, 0), listOf(after.notStarted, after.learning, after.due))
        val grammarCategory = after.categories.single { it.type == ContrastContentType.GRAMMAR }
        assertEquals(1 to 1, grammarCategory.started to grammarCategory.total)
        assertEquals(store.attempts.size, after.totals.total)
        assertEquals(1, after.totals.incorrect)
        val due = report(store.progressRows.getValue(grammar).dueAt, clock.zone)
        assertEquals(1, due.due)
        assertEquals(1, due.categories.single { it.type == ContrastContentType.GRAMMAR }.due)
    }

    private fun masteredHistory(lastDelayed: ReviewRating) {
        val day = Duration.ofDays(1)
        fun a(session: String, d: Long, type: ContrastQuestionType) = attempt(session, d * 24 * 60, AnswerOutcome.CORRECT, natteiru, group = grammar, type = type)
        repeat(4) { store.attempts += a("m1", 0, ContrastQuestionType.FILL_IN_THE_BLANK) }
        repeat(4) { store.attempts += a("m2", 3, ContrastQuestionType.MULTIPLE_CHOICE) }
        repeat(2) { store.attempts += a("m3", 10, ContrastQuestionType.CONTEXT_SELECTION) }
        repeat(2) { store.attempts += a("m3", 10, ContrastQuestionType.ERROR_DETECTION) }
        fun r(session: String, d: Long, rating: ReviewRating) = ContrastReview(0, grammar, session, t0.plus(day.multipliedBy(d)).plusSeconds(3600), 4, 4, 0, 0, rating, 0, 2, t0, 1)
        store.reviewRows += listOf(r("m1", 0, ReviewRating.GOOD), r("m2", 3, ReviewRating.GOOD), r("m3", 10, lastDelayed))
        store.progressRows[grammar] = ContrastProgress(grammar, 3, t0.plus(day.multipliedBy(24)), t0.plus(day.multipliedBy(10)), 3, 0, t0, t0)
    }

    @Test
    fun progress_masteredOnlyByTheM1Rule() = runTest {
        masteredHistory(lastDelayed = ReviewRating.AGAIN)
        val notYet = report(t0.plus(Duration.ofDays(11)), clock.zone)
        assertEquals(0, notYet.mastered) // 100% correct, but the delayed block was AGAIN
        assertEquals(1, notYet.learning)
        store.reviewRows.clear()
        store.attempts.clear()
        masteredHistory(lastDelayed = ReviewRating.GOOD)
        val mastered = report(t0.plus(Duration.ofDays(11)), clock.zone)
        assertEquals(1, mastered.mastered)
        assertEquals(ContrastMastery.MASTERED, mastered.groups.single { it.group.id == grammar }.mastery)
        assertEquals(1, mastered.categories.single { it.type == ContrastContentType.GRAMMAR }.mastered)
    }

    @Test
    fun progressScreen_updatesAfterAPractice() = runTest {
        val vm = ContrastProgressViewModel(content, store, store, analytics, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(0, vm.state.value.data().report.totals.total)
        play(PracticeScope.Group(grammar)) { _, c -> c }
        val data = vm.state.value.data()
        assertEquals(3, data.report.totals.total)
        assertEquals(1, data.report.learning)
        assertFalse(data.rate.reliable) // 3 answers < 5: no percentage yet
        val row = data.groups.single { it.groupId == grammar }
        assertEquals(3, row.counts.correct)
    }

    @Test
    fun theAnalyticsFilterMatchesTheM1Analyzer() = runTest {
        play(PracticeScope.Group(grammar), ::wrong)
        play(PracticeScope.Group(vocab)) { q, c -> if (q.endsWith("cs1")) null else wrong(q, c) }
        val m1 = com.n3sensei.app.domain.contrast.AnalyzeContrastMistakes(content, store)()
        val m7 = GetContrastErrorAnalysis(analytics)()
        assertEquals(m1.total to m1.incorrect, m7.totals.total to m7.totals.incorrect)
        assertEquals(m1.skipped, m7.totals.skipped)
        assertEquals(m1.confusions.map { Triple(it.groupId, it.expectedItemId, it.chosenItemId) to it.count }, m7.directions.map { Triple(it.count.groupId, it.count.expectedItemId, it.count.chosenItemId) to it.count.count })
        assertEquals(m1.otherIncorrect, m7.otherIncorrect)
        val vocabOnly = GetContrastErrorAnalysis(analytics)(AttemptFilter(contentType = ContrastContentType.VOCABULARY))
        assertEquals(com.n3sensei.app.domain.contrast.AnalyzeContrastMistakes(content, store)(contentType = ContrastContentType.VOCABULARY).total, vocabOnly.totals.total)
    }
}
