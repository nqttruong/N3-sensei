package com.n3sensei.app.feature.reviewsession

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewMode
import com.n3sensei.app.domain.srs.ReviewScope
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.ReviewTime
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.domain.srs.TodayCounts
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeReviewSessionRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class ReviewSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock()
    private val content = FakeContentRepository().apply {
        vocabulary.value = listOf(TestContent.vocabulary("v1", "l1", "経験"), TestContent.vocabulary("v2", "l1", "増える"))
        kanji.value = listOf(TestContent.kanji("k1", "l1", "経"))
    }
    private val progress = FakeProgressRepository()
    private val prefs = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private var nextId = 0

    private val sessions = FakeReviewSessionRepository()

    private fun viewModel(scope: ReviewScope = ReviewScope.ALL) = ReviewSessionViewModel(
        content, progress, prefs, SubmitReviewUseCase(progress), sessions, clock, newRequestId = { "req-${nextId++}" }, scope = scope,
    )

    private fun k(id: String) = ItemRef(ItemType.KANJI, id)
    private fun g(id: String) = ItemRef(ItemType.GRAMMAR, id)

    /** Three words, two kanji and one grammar pattern, all due; one new item of each type. */
    private fun allTypes() {
        content.vocabulary.value = listOf("v1", "v2", "v3", "v4").map { TestContent.vocabulary(it, "l1", "語$it") }
        content.kanji.value = listOf(TestContent.kanji("k1", "l1", "経"), TestContent.kanji("k2", "l1", "験"))
        content.grammar.value = listOf(TestContent.grammar("g1", "l1", "〜ばかり"), TestContent.grammar("g2", "l1", "〜ところ"))
        progress.dueItems = listOf(v("v1"), v("v2"), v("v3"), k("k1"), g("g1"))
        progress.newItems = listOf(v("v4"), k("k2"), g("g2"))
    }

    private fun limits(new: Int, reviews: Int) {
        prefs.preferences.value = prefs.preferences.value.copy(reviewSettings = ReviewSettings(limits = DailyLimits(newVocabulary = new, newKanji = new, newGrammar = new, reviewsPerDay = reviews)))
    }

    private fun ReviewSessionViewModel.ui() = (state.value as UiState.Success).data

    private fun v(id: String) = ItemRef(ItemType.VOCABULARY, id)

    @Test
    fun queue_isDueFirstThenNew_withinLimits_andShowsIntervalHints() {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k1"))
        progress.newItems = listOf(v("v1"), v("v2"))
        prefs.preferences.value = prefs.preferences.value.copy(reviewSettings = ReviewSettings(limits = DailyLimits(newVocabulary = 1, newKanji = 1, newGrammar = 1, reviewsPerDay = 10)))

        val ui = viewModel().ui()
        assertEquals(listOf("k1", "v1"), ui.cards.map { it.ref.id })
        val hints = ui.cards.last().hints // new item
        assertEquals(ReviewInterval(20, IntervalUnit.MINUTES), hints[ReviewRating.AGAIN])
        assertEquals(ReviewInterval(1, IntervalUnit.HOURS), hints[ReviewRating.GOOD])
        assertEquals(ReviewInterval(1, IntervalUnit.DAYS), hints[ReviewRating.EASY])
    }

    @Test
    fun eachCard_saysWhyItIsInTheSession() {
        val zone = clock.zone
        val today = ReviewTime.startOfDay(clock.instant(), zone)
        val k1 = ItemRef(ItemType.KANJI, "k1")
        progress.dueItems = listOf(k1, v("v2"))
        progress.newItems = listOf(v("v1"))
        progress.progress[k1] = TestContent.progress(k1, stepIndex = 3, dueAt = today.minusSeconds(3_600), lapses = 2) // due yesterday
        progress.progress[v("v2")] = TestContent.progress(v("v2"), stepIndex = 1, dueAt = today.plusSeconds(60))

        val reasons = viewModel().ui().cards.associate { it.ref.id to it.reason }
        assertEquals(CardReason.Due(today.minusSeconds(3_600), step = 4, steps = 7, lapses = 2, overdue = true), reasons["k1"])
        assertEquals(CardReason.Due(today.plusSeconds(60), step = 2, steps = 7, lapses = 0, overdue = false), reasons["v2"])
        assertEquals(CardReason.New, reasons["v1"])
    }

    @Test
    fun limitsAlreadyUsedToday_giveEmptySession() {
        progress.newItems = listOf(v("v1"))
        // Vocabulary introduced its 20 today (its default cap).
        progress.overview = ReviewOverview(emptyMap(), 0, TodayCounts(newAnswered = 20, reviewsAnswered = 0, newByType = mapOf(ItemType.VOCABULARY to 20)), newAvailable = 1)
        assertEquals(UiState.Empty, viewModel().state.value)
    }

    @Test
    fun removedContent_isSkipped() {
        progress.newItems = listOf(v("gone"), v("v1"))
        val ui = viewModel().ui()
        assertEquals(listOf("v1"), ui.cards.map { it.ref.id })
        assertEquals(1, ui.skippedMissing)
    }

    @Test
    fun doubleTap_recordsOnce() {
        progress.newItems = listOf(v("v1"), v("v2"))
        val vm = viewModel()
        vm.revealAnswer()
        vm.rate(ReviewRating.GOOD)
        vm.rate(ReviewRating.GOOD) // second tap on the next card: answer not revealed yet
        vm.rate(ReviewRating.EASY)

        assertEquals(1, progress.records.size)
        assertEquals(1, vm.ui().index)
        // An abandoned session keeps its partial completion.
        assertEquals(1, sessions.sessions.value.single().answered)
        assertFalse(sessions.sessions.value.single().finished)
    }

    @Test
    fun rateBeforeReveal_isIgnored() {
        progress.newItems = listOf(v("v1"))
        val vm = viewModel()
        vm.rate(ReviewRating.GOOD)
        assertEquals(0, progress.submitCalls)
    }

    @Test
    fun failedSave_canBeRetried_withTheSameRequestId_withoutDuplicates() {
        progress.newItems = listOf(v("v1"), v("v2"))
        progress.failNextSubmits = 1
        val vm = viewModel()
        val requestId = vm.ui().cards.first().requestId
        vm.revealAnswer()

        vm.rate(ReviewRating.GOOD)
        assertTrue(vm.ui().saveFailed)
        assertEquals(0, vm.ui().index)
        assertEquals(0, progress.records.size)

        vm.rate(ReviewRating.GOOD)
        assertEquals(1, vm.ui().index)
        assertEquals(listOf(requestId), progress.records.map { it.requestId })
    }

    @Test
    fun alreadySavedAnswer_reportedAsDuplicate_stillAdvances() {
        progress.newItems = listOf(v("v1"), v("v2"))
        val vm = viewModel()
        val first = vm.ui().cards.first()
        // The write succeeded before (e.g. process died after commit); the retry is a duplicate.
        kotlinx.coroutines.runBlocking {
            SubmitReviewUseCase(progress)(first.ref, first.requestId, ReviewRating.GOOD, ReviewSettings(), clock.now, clock.zone)
        }
        vm.revealAnswer()
        vm.rate(ReviewRating.GOOD)
        assertEquals(1, vm.ui().index)
        assertEquals(1, progress.records.size)
    }

    @Test
    fun sessionHistoryFailure_doesNotBlockTheReview() {
        sessions.failWrites = true
        progress.newItems = listOf(v("v1"))
        val vm = viewModel()
        vm.revealAnswer()
        vm.rate(ReviewRating.GOOD)
        assertEquals(1, progress.records.size)
        assertNotNull(vm.ui().result)
        assertFalse(vm.ui().saveFailed)
        assertTrue(sessions.sessions.value.isEmpty())
    }

    @Test
    fun finishingAllCards_producesResult_andSavesEachAnswer() {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k1"))
        progress.newItems = listOf(v("v1"))
        val vm = viewModel()
        clock.advance(Duration.ofSeconds(30))
        listOf(ReviewRating.AGAIN, ReviewRating.EASY).forEach {
            assertNull(vm.ui().result)
            vm.revealAnswer()
            vm.rate(it)
        }
        val result = assertNotNull(vm.ui().result).let { vm.ui().result!! }
        assertEquals(2, result.total)
        assertEquals(1, result.again)
        assertEquals(1, result.easy)
        assertEquals(30L, result.durationSeconds)
        assertEquals(2, progress.progress.size)
        assertEquals(0, progress.progress.getValue(ItemRef(ItemType.KANJI, "k1")).stepIndex)
        // Session history: planned 2, answered 2, finished.
        val session = sessions.sessions.value.single()
        assertEquals(2, session.planned)
        assertEquals(2, session.answered)
        assertTrue(session.finished)
        assertEquals(2, progress.progress.getValue(v("v1")).stepIndex)
    }

    @Test
    fun aMixedSession_keepsTheDueOrder_andTakesNewItemsWithinEachTypesOwnLimit() {
        allTypes()
        limits(new = 1, reviews = 3)
        // Due: oldest first whatever the type (unchanged); new: one of each type, nobody's share moved.
        assertEquals(listOf("v1", "v2", "v3", "v4", "k2", "g2"), viewModel().ui().cards.map { it.ref.id })
        // Kanji turned off: words and grammar keep theirs.
        prefs.preferences.value = prefs.preferences.value.copy(
            reviewSettings = ReviewSettings(limits = DailyLimits(newVocabulary = 1, newKanji = 0, newGrammar = 1, reviewsPerDay = 3)),
        )
        assertEquals(listOf("v1", "v2", "v3", "v4", "g2"), viewModel().ui().cards.map { it.ref.id })
    }

    @Test
    fun whatATypeIntroducedToday_onlyReducesThatType() {
        allTypes()
        progress.overview = ReviewOverview(emptyMap(), 0, TodayCounts(10, 0, mapOf(ItemType.GRAMMAR to 10)), newAvailable = 3)
        limits(new = 10, reviews = 200)
        val ids = viewModel(ReviewScope(mode = ReviewMode.SCHEDULED)).ui().cards.map { it.ref.id }
        assertTrue("g2" !in ids) // grammar used its 10 today
        assertTrue("v4" in ids && "k2" in ids)
    }

    @Test
    fun aTypeThatReachedItsLimitInAnotherSession_meanwhile_isSkipped_notSaved() {
        allTypes()
        prefs.preferences.value = prefs.preferences.value.copy(reviewSettings = ReviewSettings(limits = DailyLimits(newVocabulary = 0, newKanji = 1, newGrammar = 0)))
        progress.progress[k("k1")] = TestContent.progress(k("k1"), stepIndex = 1, dueAt = clock.instant().minusSeconds(60))
        val vm = viewModel(ReviewScope(type = ItemType.KANJI))
        assertEquals(listOf("k1", "k2"), vm.ui().cards.map { it.ref.id }) // one due kanji, one new
        // Another session introduces a kanji first.
        kotlinx.coroutines.runBlocking { SubmitReviewUseCase(progress)(k("k9"), "other-session", ReviewRating.GOOD, prefs.preferences.value.reviewSettings, clock.instant(), clock.zone) }
        vm.revealAnswer(); vm.rate(ReviewRating.GOOD) // k1: due, saved
        vm.revealAnswer(); vm.rate(ReviewRating.GOOD) // k2: new, kanji cap reached → skipped
        val ui = vm.ui()
        assertEquals(1, ui.skippedLimit)
        assertEquals(listOf("k9", "k1"), progress.records.map { it.item.id })
        assertEquals(null, progress.progress[k("k2")])
        assertEquals(1, ui.result!!.total)
    }

    @Test
    fun oneTypeOnly_asksThatTypeAlone_onItsOwnSchedule() {
        allTypes()
        assertEquals(listOf("k1", "k2"), viewModel(ReviewScope(type = ItemType.KANJI)).ui().cards.map { it.ref.id })
        assertEquals(listOf("g1", "g2"), viewModel(ReviewScope(type = ItemType.GRAMMAR)).ui().cards.map { it.ref.id })
        assertEquals(listOf("v1", "v2", "v3", "v4"), viewModel(ReviewScope(type = ItemType.VOCABULARY)).ui().cards.map { it.ref.id })
    }

    @Test
    fun dueOnly_leavesNewItemsOut() {
        allTypes()
        val ui = viewModel(ReviewScope(mode = ReviewMode.DUE_ONLY)).ui()
        assertEquals(listOf("v1", "v2", "v3", "k1", "g1"), ui.cards.map { it.ref.id })
        assertTrue(ui.cards.none { it.ref in listOf(v("v4"), k("k2"), g("g2")) })
    }

    @Test
    fun forgotten_asksTheLastForgottenItems_evenNotYetDue_andRatesThemOnTheirSchedule() {
        allTypes()
        val later = clock.instant().plusSeconds(600)
        progress.forgottenItems = listOf(k("k1"), g("g1"))
        progress.progress[g("g1")] = TestContent.progress(g("g1"), stepIndex = 0, dueAt = later, lapses = 1) // not due yet
        val vm = viewModel(ReviewScope(mode = ReviewMode.FORGOTTEN))
        val ui = vm.ui()
        assertEquals(listOf("k1", "g1"), ui.cards.map { it.ref.id })
        assertEquals(CardReason.Forgotten(lapses = 1), ui.cards.last().reason)
        // The interval shown is the scheduler's own (not a fixed label).
        assertEquals(ReviewInterval(1, IntervalUnit.HOURS), ui.cards.first().hints[ReviewRating.GOOD])
        vm.revealAnswer()
        vm.rate(ReviewRating.GOOD)
        assertEquals(k("k1"), progress.records.single().item)
        // Only the kanji's own schedule was written.
        assertEquals(setOf(k("k1"), g("g1")), progress.progress.keys)
    }

    @Test
    fun aGrammarCard_carriesItsExamples() {
        allTypes()
        content.grammarExamples["g1"] = listOf(com.n3sensei.app.domain.model.ExampleSentence("e1", "来たばかりです。", null, "Vừa mới đến."))
        val card = viewModel(ReviewScope(type = ItemType.GRAMMAR)).ui().cards.first()
        assertEquals(listOf("来たばかりです。"), (card.item as PracticeItem.GrammarItem).examples.map { it.japanese })
    }

    @Test
    fun revealingTheAnswer_recordsNothing_onlyARatingDoes() {
        progress.newItems = listOf(v("v1"))
        val vm = viewModel()
        vm.revealAnswer()
        vm.revealAnswer() // turned over again: still nothing
        assertTrue(progress.records.isEmpty())
        assertEquals(0, progress.submitCalls)
        assertTrue(vm.ui().isAnswerRevealed)
        vm.rate(ReviewRating.HARD)
        assertEquals(listOf(ReviewRating.HARD), progress.records.map { it.rating })
    }

    @Test
    fun aWordCard_carriesItsOwnExamples() {
        content.vocabularyExamples["v1"] = listOf(com.n3sensei.app.domain.model.ExampleSentence("e1", "経験があります。", null, "Có kinh nghiệm."))
        progress.newItems = listOf(v("v1"), v("v2"))
        val cards = viewModel().ui().cards
        assertEquals(listOf("e1"), (cards[0].item as PracticeItem.VocabularyItem).examples.map { it.id })
        assertTrue((cards[1].item as PracticeItem.VocabularyItem).examples.isEmpty())
    }
}
