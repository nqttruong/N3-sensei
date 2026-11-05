package com.n3sensei.app.feature.practice

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.ReviewRating.AGAIN
import com.n3sensei.app.domain.model.ReviewRating.EASY
import com.n3sensei.app.domain.model.ReviewRating.GOOD
import com.n3sensei.app.domain.model.ReviewRating.HARD
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.SpacedRepetitionScheduler
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class PracticeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock()
    private val repository = FakeContentRepository().apply {
        lessons.value = listOf(TestContent.lesson("l1", 1), TestContent.lesson("empty", 2))
        vocabulary.value = listOf(TestContent.vocabulary("v1", "l1", "経験"), TestContent.vocabulary("other", "x", "他"))
        kanji.value = listOf(TestContent.kanji("k1", "l1", "経"))
        grammar.value = listOf(TestContent.grammar("g1", "l1", "〜ばかり"))
    }

    private fun PracticeViewModel.ui() = (state.value as UiState.Success).data

    @Test
    fun loadsLessonItemsInOrder_vocabularyKanjiGrammar() {
        val items = PracticeViewModel("l1", markedOnly = false, repository, clock).ui().session.items
        assertEquals(listOf("v1", "k1", "g1"), items.map { it.ref.id })
        assertTrue(items[0] is PracticeItem.VocabularyItem)
    }

    @Test
    fun ratingEveryCard_producesResultWithDuration() {
        val viewModel = PracticeViewModel("l1", markedOnly = false, repository, clock)
        listOf(ReviewRating.GOOD, ReviewRating.AGAIN).forEach {
            viewModel.revealAnswer()
            viewModel.rate(it)
        }
        assertNull(viewModel.ui().result)

        clock.advance(Duration.ofSeconds(75))
        viewModel.revealAnswer()
        viewModel.rate(ReviewRating.EASY)

        assertEquals(PracticeResult(total = 3, again = 1, hard = 0, good = 1, easy = 1, durationSeconds = 75), viewModel.ui().result)
    }

    @Test
    fun emptyLesson_isEmpty_andUnknownLesson_isNotFound() {
        assertEquals(UiState.Empty, PracticeViewModel("empty", markedOnly = false, repository, clock).state.value)
        assertEquals(UiState.NotFound, PracticeViewModel("nope", markedOnly = false, repository, clock).state.value)
    }

    @Test
    fun markedOnly_practisesMarkedItemsAcrossLessons() {
        repository.marked.value = setOf("k1", "other")
        val ui = PracticeViewModel(lessonId = null, markedOnly = true, repository, clock).ui()
        assertEquals(listOf("other", "k1"), ui.session.items.map { it.ref.id })
        assertNull(ui.lessonTitle)
    }

    @Test
    fun markedOnly_withNothingMarked_isEmpty() {
        assertEquals(UiState.Empty, PracticeViewModel(null, markedOnly = true, repository, clock).state.value)
    }

    // --- next-interval labels ------------------------------------------------------------------

    private val progressRepository = FakeProgressRepository()
    private val preferences = FakeUserPreferencesRepository()

    private fun withSchedule(lessonId: String? = "l1") =
        PracticeViewModel(lessonId, markedOnly = false, repository, clock, progressRepository, preferences)

    private fun ReviewSettings.withLadder(vararg intervals: ReviewInterval) = copy(ladder = ReviewLadder.of(intervals.toList()))

    @Test
    fun answerButtons_showTheIntervalTheSchedulerWouldGive_fromTheItemsStoredProgress() = runTest {
        val k1 = ItemRef(ItemType.KANJI, "k1")
        progressRepository.progress[k1] = TestContent.progress(k1, stepIndex = 2, dueAt = clock.instant())
        val viewModel = withSchedule()
        viewModel.hints.launchIn(backgroundScope)
        runCurrent()
        // v1 is new: 20 phút, 20 phút, 1 giờ, 1 ngày on the default ladder.
        assertEquals(
            mapOf(AGAIN to minutes(20), HARD to minutes(20), GOOD to ReviewInterval(1, IntervalUnit.HOURS), EASY to days(1)),
            viewModel.hints.value,
        )
        viewModel.revealAnswer()
        viewModel.rate(GOOD)
        runCurrent()
        // k1 is on step 2 (1 ngày): Quên back to 20 phút, Khó stays, Nhớ +1, Dễ +2.
        val scheduler = SpacedRepetitionScheduler(ReviewLadder.DEFAULT)
        assertEquals(ReviewRating.entries.associateWith { scheduler.preview(progressRepository.progress[k1], it) }, viewModel.hints.value)
        assertEquals(mapOf(AGAIN to minutes(20), HARD to days(1), GOOD to days(2), EASY to ReviewInterval(1, IntervalUnit.WEEKS)), viewModel.hints.value)
    }

    @Test
    fun changingTheLadder_updatesTheLabels() = runTest {
        val viewModel = withSchedule()
        viewModel.hints.launchIn(backgroundScope)
        runCurrent()
        preferences.setReviewSettings(preferences.preferences.value.reviewSettings.withLadder(minutes(10), days(1), days(3)))
        runCurrent()
        assertEquals(mapOf(AGAIN to minutes(10), HARD to minutes(10), GOOD to days(1), EASY to days(3)), viewModel.hints.value)
    }

    @Test
    fun previewingAndRating_neverWriteTheSchedule() = runTest {
        val viewModel = withSchedule()
        viewModel.hints.launchIn(backgroundScope)
        runCurrent()
        repeat(3) {
            viewModel.revealAnswer()
            viewModel.rate(EASY)
            runCurrent()
        }
        assertEquals(0, progressRepository.submitCalls)
        assertTrue(progressRepository.progress.isEmpty())
        assertTrue(progressRepository.records.isEmpty())
    }

    @Test
    fun withoutTheSchedule_noLabelIsInvented() = runTest {
        val viewModel = PracticeViewModel("l1", markedOnly = false, repository, clock)
        viewModel.hints.launchIn(backgroundScope)
        runCurrent()
        assertTrue(viewModel.hints.value.isEmpty())
    }

    // --- kanji words -------------------------------------------------------------------------------

    @Test
    fun kanjiCards_carryAllTheirWords() {
        val words = (1..40).map { KanjiWord("経$it", null, "nghĩa $it") } + KanjiWord("けいけん", null, "chỉ có kana") +
            KanjiWord("経験", "{経験|けいけん}", "kinh nghiệm", vocabularyId = "v1")
        repository.kanjiWords["k1"] = words
        val card = PracticeViewModel("l1", markedOnly = false, repository, clock).ui().session.items
            .filterIsInstance<PracticeItem.KanjiItem>().single()
        assertEquals(words, card.words)
    }

    // --- "Bài học" lessons ---------------------------------------------------------------------------

    @Test
    fun courseLesson_practisesItsKanjiThenWordsThenGrammar() {
        repository.kanji.value = (1..30).map { TestContent.kanji("k$it", "kl", "字") }
        repository.vocabulary.value = (1..35).map { TestContent.vocabulary("v$it", "vl", "語$it") }
        repository.grammar.value = (1..9).map { TestContent.grammar("g$it", "gl", "〜$it") }
        val second = PracticeViewModel(null, markedOnly = false, repository, clock, courseLesson = 2).ui().session.items
        assertEquals((25..30).map { "k$it" } + (31..35).map { "v$it" } + (8..9).map { "g$it" }, second.map { it.ref.id })
        assertEquals(UiState.NotFound, PracticeViewModel(null, markedOnly = false, repository, clock, courseLesson = 3).state.value)
    }

    @Test
    fun vocabularyGroup_practisesJustItsWordsInOrder() {
        repository.vocabulary.value = (1..65).map { TestContent.vocabulary("v$it", "vl", "語$it") }
        val second = PracticeViewModel(null, markedOnly = false, repository, clock, vocabularyGroup = "vl#2").ui().session.items
        assertEquals((31..60).map { "v$it" }, second.map { it.ref.id })
        val last = PracticeViewModel(null, markedOnly = false, repository, clock, vocabularyGroup = "vl#3").ui().session.items
        assertEquals((61..65).map { "v$it" }, last.map { it.ref.id })
        assertEquals(UiState.NotFound, PracticeViewModel(null, markedOnly = false, repository, clock, vocabularyGroup = "vl#4").state.value)
    }

    private fun minutes(n: Int) = ReviewInterval(n, IntervalUnit.MINUTES)
    private fun days(n: Int) = ReviewInterval(n, IntervalUnit.DAYS)
}
