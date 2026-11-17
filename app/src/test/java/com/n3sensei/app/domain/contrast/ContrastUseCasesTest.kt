package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ReviewRating
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The eight use cases on in-memory repositories filled with the real sample pack. */
class ContrastUseCasesTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val content = FakeContrastContent(ContrastFixtures.samplePack())
    private val store = FakeContrastStore(content)
    private var ids = 0
    private val start = StartOrResumePractice(content, store, store, newId = { "s${++ids}" })
    private val submit = SubmitContrastAnswer(content, store, store)
    private val result = GetPracticeResult(content, store, store)
    private val grammarId = "cg-g-koto-ni-shiteiru-naru"
    private var requests = 0

    private suspend fun answer(session: ContrastSession, position: Int, input: AnswerInput, at: Instant = t0) =
        submit(session.id, position, input, "req${++requests}", at, zone)

    private suspend fun correctOf(item: SessionItem) = AnswerInput.Chosen(content.getQuestion(item.questionId)!!.correctOptionId)
    private suspend fun wrongOf(item: SessionItem) = AnswerInput.Chosen(content.getQuestion(item.questionId)!!.options.first { it.id != content.getQuestion(item.questionId)!!.correctOptionId }.id)

    private suspend fun startGroup(groupId: String = grammarId, at: Instant = t0) =
        (start(PracticeScope.Group(groupId), at, zone) as StartPracticeResult.Started).session

    // ---- Browse ----

    @Test
    fun listSearchAndDetail_readOnly_noProgressFromReading() = runTest {
        val observe = ObserveContrastGroups(content)
        assertEquals(3, observe().first().size)
        assertEquals(listOf("cg-v-sameru-cold-awake"), observe(ContrastFilter(query = "冷める")).first().map { it.id })
        assertEquals(listOf("cg-k-seki-accumulate-achieve"), observe(ContrastFilter(contentType = ContrastContentType.KANJI)).first().map { it.id })

        val resolver = FakeSourceResolver(mapOf("ci-g-koto-ni-natteiru" to ResolvedSource(SourceRefStatus.SOURCE_CHANGED, "n3g-g002")))
        val detail = GetContrastGroupDetail(content, resolver)(grammarId) as GroupDetailResult.Found
        assertEquals(SourceRefStatus.OK, detail.detail.sources.getValue("ci-g-koto-ni-shiteiru").status)
        assertEquals(SourceRefStatus.SOURCE_CHANGED, detail.detail.sources.getValue("ci-g-koto-ni-natteiru").status)
        // Reading the theory writes nothing.
        assertTrue(store.attempts.isEmpty() && store.progressRows.isEmpty() && store.reviewRows.isEmpty() && store.sessions.isEmpty())
        assertEquals(GroupDetailResult.NotFound, GetContrastGroupDetail(content, resolver)("nope"))
    }

    @Test
    fun aRetiredGroup_isReportedAsRetired() = runTest {
        val pack = content.pack
        content.update(pack.copy(groups = pack.groups.take(2), retiredGroups = listOf(RetiredGroup("cg-k-seki-accumulate-achieve", 2, "Gộp", null))))
        assertTrue(GetContrastGroupDetail(content, FakeSourceResolver())("cg-k-seki-accumulate-achieve") is GroupDetailResult.Retired)
    }

    // ---- Sessions ----

    @Test
    fun startResume_andNeverTwoActiveSessions() = runTest {
        val first = startGroup()
        assertEquals(3, first.items.size)
        assertEquals(first, (start(PracticeScope.Group(grammarId), t0.plusSeconds(60), zone) as StartPracticeResult.Resumed).session)
        // Another scope: a new session; the old one is ABANDONED (its positions stay PENDING, not wrong).
        val second = (start(PracticeScope.Group("cg-v-sameru-cold-awake"), t0.plusSeconds(120), zone) as StartPracticeResult.Started).session
        assertEquals(1, store.sessions.values.count { it.status == SessionStatus.ACTIVE })
        assertEquals(SessionStatus.ABANDONED, store.sessions.getValue(first.id).status)
        assertEquals(List(3) { PositionState.PENDING }, result(first.id)!!.states)
        // Too old to resume: a new session.
        assertTrue(start(PracticeScope.Group("cg-v-sameru-cold-awake"), t0.plus(Duration.ofHours(30)), zone) is StartPracticeResult.Started)
        assertEquals(SessionStatus.ABANDONED, store.sessions.getValue(second.id).status)
    }

    @Test
    fun dueScope_newGroupsWithinTheDailyLimit() = runTest {
        val limited = StartOrResumePractice(content, store, store, ContrastConfig(newGroupsPerDay = 2), newId = { "d${++ids}" })
        val session = (limited(PracticeScope.Due, t0, zone) as StartPracticeResult.Started).session
        assertEquals(listOf(grammarId, "cg-v-sameru-cold-awake"), session.items.map { it.groupId }.distinct())
        val none = StartOrResumePractice(content, store, store, ContrastConfig(newGroupsPerDay = 0), newId = { "n${++ids}" })
        assertEquals(StartPracticeResult.NothingToPractice, none(PracticeScope.Type(ContrastContentType.KANJI), t0, zone))
    }

    @Test
    fun theDailyNewLimit_countsNewGroupsOnly_notReviews() = runTest {
        val oneNew = StartOrResumePractice(content, store, store, ContrastConfig(newGroupsPerDay = 1), newId = { "l${++ids}" })
        // Day 1: the grammar group is introduced (1 new group today).
        val day1 = (oneNew(PracticeScope.Due, t0, zone) as StartPracticeResult.Started).session
        assertEquals(listOf(grammarId), day1.items.map { it.groupId }.distinct())
        day1.items.forEach { answer(day1, it.position, correctOf(it)) }
        // Same day: the limit is used up.
        assertEquals(StartPracticeResult.NothingToPractice, oneNew(PracticeScope.Due, t0.plusSeconds(60), zone))
        // Day 4: grammar is due (a review, not counted) and one new group is allowed again.
        val day4 = (oneNew(PracticeScope.Due, t0.plus(Duration.ofDays(3)), zone) as StartPracticeResult.Started).session
        assertEquals(listOf(grammarId, "cg-v-sameru-cold-awake"), day4.items.map { it.groupId }.distinct())
        // Defaults are the confirmed experimental values.
        val defaults = ContrastConfig()
        assertEquals(Triple(3, 3, 10), Triple(defaults.questionsPerBlock, defaults.newGroupsPerDay, defaults.maxGroupsPerSession))
    }

    // ---- Answers and scheduling ----

    @Test
    fun aCompletedBlock_writesAttemptReviewAndProgressTogether() = runTest {
        val session = startGroup()
        session.items.take(2).forEach { assertTrue(answer(session, it.position, correctOf(it)) is SubmitOutcome.Recorded) }
        assertNull(store.progress(grammarId)) // block not complete yet
        val last = answer(session, 2, correctOf(session.items[2])) as SubmitOutcome.Recorded
        assertTrue(last.result.blockCompleted && last.result.sessionCompleted)
        assertEquals(ReviewRating.GOOD, last.result.review!!.rating)
        val progress = store.progress(grammarId)!!
        assertEquals(1, progress.stepIndex)
        assertEquals(t0.plus(Duration.ofDays(3)), progress.dueAt)
        assertEquals(SessionStatus.COMPLETED, store.sessions.getValue(session.id).status)
        // Attempts carry the content version and text snapshots.
        val a = store.attempts.first()
        assertEquals(1 to "contrast-n3", a.contentVersion to a.contentPackId)
        assertEquals(1 to 1, a.groupRevision to a.questionRevision)
        assertNotNull(a.correctText)
    }

    @Test
    fun aFailedTransaction_leavesNeitherAttemptNorProgress() = runTest {
        val session = startGroup()
        answer(session, 0, correctOf(session.items[0]))
        answer(session, 1, correctOf(session.items[1]))
        store.failNextWrite = true
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { answer(session, 2, correctOf(session.items[2])) } }
        assertEquals(2, store.attempts.size)
        assertNull(store.progress(grammarId))
        assertTrue(store.reviewRows.isEmpty())
    }

    @Test
    fun repeatedSubmit_isADuplicate_anotherAnswerToTheSamePositionIsRefused() = runTest {
        val session = startGroup()
        val first = submit(session.id, 0, correctOf(session.items[0]), "same", t0, zone)
        assertTrue(first is SubmitOutcome.Recorded)
        assertEquals(SubmitOutcome.Duplicate, submit(session.id, 0, correctOf(session.items[0]), "same", t0, zone))
        assertEquals(SubmitOutcome.Rejected(SubmitRejection.ALREADY_ANSWERED), submit(session.id, 0, wrongOf(session.items[0]), "other", t0, zone))
        assertEquals(SubmitOutcome.Rejected(SubmitRejection.UNKNOWN_POSITION), submit(session.id, 99, AnswerInput.Skipped, "x", t0, zone))
        assertEquals(SubmitOutcome.Rejected(SubmitRejection.INVALID_OPTION), submit(session.id, 1, AnswerInput.Chosen("zz"), "y", t0, zone))
        assertEquals(SubmitOutcome.Rejected(SubmitRejection.NO_ACTIVE_SESSION), submit("nope", 0, AnswerInput.Skipped, "z", t0, zone))
        assertEquals(1, store.attempts.size)
    }

    @Test
    fun skippedAndQuitting_areNotWrong() = runTest {
        val session = startGroup()
        answer(session, 0, AnswerInput.Skipped)
        answer(session, 1, wrongOf(session.items[1]))
        // The learner leaves: position 2 has no attempt.
        val r = result(session.id)!!
        assertEquals(listOf(PositionState.SKIPPED, PositionState.INCORRECT, PositionState.PENDING), r.states.take(3))
        assertEquals(0, r.count(PositionState.CORRECT))
        assertEquals(AnswerOutcome.SKIPPED, store.attempts[0].outcome)
        assertNull(store.progress(grammarId)) // block not completed: nothing scheduled
    }

    @Test
    fun aWrongAnswer_addsADifferentQuestionAtTheEnd_notCountedInTheBlock() = runTest {
        val session = startGroup()
        val wrong = answer(session, 0, wrongOf(session.items[0])) as SubmitOutcome.Recorded
        assertTrue(wrong.result.retryAdded)
        val updated = store.session(session.id)!!
        assertEquals(4, updated.items.size)
        assertNull(updated.items[3].blockIndex)
        assertTrue(updated.items[3].questionId !in session.items.map { it.questionId })
        answer(updated, 1, correctOf(updated.items[1]))
        val blockDone = answer(updated, 2, correctOf(updated.items[2])) as SubmitOutcome.Recorded
        assertTrue(blockDone.result.blockCompleted)
        assertEquals(ReviewRating.HARD, blockDone.result.review!!.rating) // 2/3
        assertTrue(!blockDone.result.sessionCompleted) // the re-ask is still open
        val retry = answer(updated, 3, wrongOf(updated.items[3])) as SubmitOutcome.Recorded
        assertTrue(!retry.result.retryAdded) // no re-ask of a re-ask
        assertTrue(retry.result.sessionCompleted)
        assertEquals(1, store.reviewRows.size) // the re-ask never reschedules
    }

    @Test
    fun tooManySkips_noSchedule() = runTest {
        val session = startGroup()
        answer(session, 0, AnswerInput.Skipped)
        answer(session, 1, AnswerInput.Skipped)
        val done = answer(session, 2, correctOf(session.items[2])) as SubmitOutcome.Recorded
        assertTrue(done.result.blockCompleted)
        assertNull(done.result.review)
        assertNull(store.progress(grammarId))
    }

    @Test
    fun aPackUpdate_keepsHistoryProgressAndSchedule_andRemovedQuestionsBecomeUnavailable() = runTest {
        val session = startGroup()
        session.items.forEach { answer(session, it.position, correctOf(it)) }
        val progressBefore = store.progress(grammarId)
        val attemptsBefore = store.attempts.toList()
        val pack = content.pack
        val grammar = pack.groups[0]
        // Version 2: the group's revision changes and one answered question is removed.
        content.update(pack.copy(contentVersion = 2, groups = listOf(grammar.copy(
            detail = grammar.detail.copy(group = grammar.detail.group.copy(revision = 2)),
            questions = grammar.questions.filter { it.id != session.items[0].questionId },
        )) + pack.groups.drop(1)))
        assertEquals(progressBefore, store.progress(grammarId))
        assertEquals(attemptsBefore, store.attempts)
        assertEquals(1, store.attempts.first().contentVersion) // the version used at answer time

        // An unfinished session over a removed question: the position becomes UNAVAILABLE, never wrong.
        val later = t0.plus(Duration.ofDays(3))
        val next = startGroup(at = later)
        content.update(content.pack.copy(groups = listOf(content.pack.groups[0].copy(questions = content.pack.groups[0].questions.filter { it.id != next.items[0].questionId })) + content.pack.groups.drop(1)))
        val resumed = start(PracticeScope.Group(grammarId), later, zone) as StartPracticeResult.Resumed
        assertEquals(setOf(0), resumed.unavailablePositions)
        assertEquals(SubmitOutcome.Rejected(SubmitRejection.QUESTION_UNAVAILABLE), answer(next, 0, AnswerInput.Skipped, later))
        answer(next, 1, correctOf(next.items[1]), later)
        val done = answer(next, 2, correctOf(next.items[2]), later) as SubmitOutcome.Recorded
        assertTrue(done.result.blockCompleted) // the unavailable position does not block the block
        assertEquals(2, done.result.review!!.contentVersion)
        assertEquals(PositionState.UNAVAILABLE, result(next.id)!!.states[0])
    }

    // ---- Insight ----

    @Test
    fun progressDueAndMistakes() = runTest {
        val session = startGroup()
        answer(session, 0, wrongOf(session.items[0]))
        answer(session, 1, correctOf(session.items[1]))
        answer(session, 2, correctOf(session.items[2]))

        val progress = GetContrastProgress(content, store, store)
        assertEquals(ContrastMastery.LEARNING, progress.ofGroup(grammarId, zone).mastery)
        val overview = progress.overview(zone)
        assertEquals(mapOf(ContrastMastery.LEARNING to 1, ContrastMastery.NEW to 2), overview.byMastery)

        val due = GetDueContrastReviews(store)
        assertEquals(DueSummary(0, 2, t0.plus(Duration.ofDays(1))), due(t0, zone))
        assertEquals(1, due(t0.plus(Duration.ofDays(1)), zone).dueCount)

        val mistakes = AnalyzeContrastMistakes(content, store)()
        assertEquals(1, mistakes.incorrect)
        assertEquals(listOf("ci-g-koto-ni-natteiru" to "ci-g-koto-ni-shiteiru"), mistakes.confusions.map { it.expectedItemId to it.chosenItemId })
        assertEquals(0, AnalyzeContrastMistakes(content, store)(contentType = ContrastContentType.KANJI).total)
        assertEquals(listOf(GroupConfusion(grammarId, Confusion("ci-g-koto-ni-natteiru", "ci-g-koto-ni-shiteiru"))), result(session.id)!!.confusions)
    }
}
