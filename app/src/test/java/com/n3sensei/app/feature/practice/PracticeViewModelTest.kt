package com.n3sensei.app.feature.practice

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ReviewRating
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
        val items = PracticeViewModel("l1", repository, clock).ui().session.items
        assertEquals(listOf("v1", "k1", "g1"), items.map { it.ref.id })
        assertTrue(items[0] is PracticeItem.VocabularyItem)
    }

    @Test
    fun ratingEveryCard_producesResultWithDuration() {
        val viewModel = PracticeViewModel("l1", repository, clock)
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
        assertEquals(UiState.Empty, PracticeViewModel("empty", repository, clock).state.value)
        assertEquals(UiState.NotFound, PracticeViewModel("nope", repository, clock).state.value)
    }
}
