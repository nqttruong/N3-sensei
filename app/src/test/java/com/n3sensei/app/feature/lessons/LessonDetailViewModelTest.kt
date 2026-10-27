package com.n3sensei.app.feature.lessons

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository().apply {
        lessons.value = listOf(TestContent.lesson("lv", 1), TestContent.lesson("lk", 2), TestContent.lesson("lg", 3))
        kanji.value = (1..26).map { TestContent.kanji("k$it", "lk", "字") }
        vocabulary.value = (1..31).map { TestContent.vocabulary("v$it", "lv", "語$it") }
        grammar.value = (1..8).map { TestContent.grammar("g$it", "lg", "〜$it") }
        kanjiWords["k1"] = listOf(KanjiWord("字幕", "{字幕|じまく}", "phụ đề"))
    }

    private fun TestScope.open(number: Int) = LessonDetailViewModel(number, repository).also {
        it.state.launchIn(backgroundScope)
        runCurrent()
    }

    @Test
    fun lessonOne_holds24Kanji30Words7Grammar() = runTest {
        val lesson = (open(1).state.value as UiState.Success).data
        assertEquals((1..24).map { "k$it" }, lesson.kanji.map { it.item.id })
        assertEquals((1..30).map { "v$it" }, lesson.words.map { it.item.id })
        assertEquals((1..7).map { "g$it" }, lesson.grammar.map { it.item.id })
        assertEquals(listOf("1.1"), lesson.vocabulary.map { it.label })
    }

    @Test
    fun lastLesson_holdsTheRest_andAnUnknownNumberIsNotFound() = runTest {
        val lesson = (open(2).state.value as UiState.Success).data
        assertEquals(listOf("k25", "k26"), lesson.kanji.map { it.item.id })
        assertEquals(listOf("v31"), lesson.words.map { it.item.id })
        assertEquals(listOf("1.2"), lesson.vocabulary.map { it.label })
        assertEquals(listOf("g8"), lesson.grammar.map { it.item.id })
        assertEquals(UiState.NotFound, open(3).state.value)
    }

    @Test
    fun tabs_switchFreelyInAnyOrder_andTheLessonStaysTheSame() = runTest {
        val viewModel = open(1)
        val before = viewModel.state.value
        assertEquals(LessonTab.KANJI, viewModel.selectedTab.value)
        listOf(LessonTab.GRAMMAR, LessonTab.KANJI, LessonTab.VOCABULARY, LessonTab.GRAMMAR).forEach {
            viewModel.selectTab(it)
            assertEquals(it, viewModel.selectedTab.value)
        }
        runCurrent()
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun expandingAKanji_loadsAllItsWords_andCollapsingKeepsNothingElseChanged() = runTest {
        val viewModel = open(1)
        viewModel.toggleKanji("k1")
        runCurrent()
        assertEquals(Expansion.Loaded(listOf(KanjiWord("字幕", "{字幕|じまく}", "phụ đề"))), viewModel.kanjiWords.value["k1"])
        viewModel.toggleKanji("k2")
        runCurrent()
        assertEquals(Expansion.Loaded(emptyList<KanjiWord>()), viewModel.kanjiWords.value["k2"])
        viewModel.toggleKanji("k1")
        assertFalse("k1" in viewModel.kanjiWords.value)
        assertTrue("k2" in viewModel.kanjiWords.value)
    }
}
