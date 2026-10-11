package com.n3sensei.app.feature.reviewsession

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewOverview
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.domain.srs.TodayCounts
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeReviewSessionRepository
import com.n3sensei.app.testing.FakeProgressRepository
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

    private fun viewModel() = ReviewSessionViewModel(
        content, progress, prefs, SubmitReviewUseCase(progress), sessions, clock, newRequestId = { "req-${nextId++}" },
    )

    private fun ReviewSessionViewModel.ui() = (state.value as UiState.Success).data

    private fun v(id: String) = ItemRef(ItemType.VOCABULARY, id)

    @Test
    fun queue_isDueFirstThenNew_withinLimits_andShowsIntervalHints() {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k1"))
        progress.newItems = listOf(v("v1"), v("v2"))
        prefs.preferences.value = prefs.preferences.value.copy(reviewSettings = ReviewSettings(limits = DailyLimits(newPerDay = 1, reviewsPerDay = 10)))

        val ui = viewModel().ui()
        assertEquals(listOf("k1", "v1"), ui.cards.map { it.ref.id })
        val hints = ui.cards.last().hints // new item
        assertEquals(ReviewInterval(20, IntervalUnit.MINUTES), hints[ReviewRating.AGAIN])
        assertEquals(ReviewInterval(1, IntervalUnit.HOURS), hints[ReviewRating.GOOD])
        assertEquals(ReviewInterval(1, IntervalUnit.DAYS), hints[ReviewRating.EASY])
    }

    @Test
    fun limitsAlreadyUsedToday_giveEmptySession() {
        progress.newItems = listOf(v("v1"))
        progress.overview = ReviewOverview(emptyMap(), 0, TodayCounts(newAnswered = 20, reviewsAnswered = 0), newAvailable = 1)
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
}
