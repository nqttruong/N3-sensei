package com.n3sensei.app.core.ui.state

import com.n3sensei.app.feature.grammar.GrammarListViewModel
import com.n3sensei.app.feature.kanji.KanjiListViewModel
import com.n3sensei.app.feature.vocabulary.VocabularyListViewModel
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression: with a vocabulary-only pack and a grammar-only pack installed, the grammar list
 * offered the vocabulary lessons as filters (and selecting one showed an empty list).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonChipsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeContentRepository().apply {
        lessons.value = listOf(
            TestContent.lesson("vocab-only", 1, vocabulary = 120),
            TestContent.lesson("grammar-only", 1, grammar = 10),
            TestContent.lesson("mixed", 1, vocabulary = 30, kanji = 27, grammar = 7),
        )
    }

    @Test
    fun eachListOffersOnlyLessonsThatContainItsItems() = runTest {
        val grammar = GrammarListViewModel(repository)
        val vocabulary = VocabularyListViewModel(repository)
        val kanji = KanjiListViewModel(repository)
        listOf(grammar.lessons, vocabulary.lessons, kanji.lessons).forEach { it.launchIn(backgroundScope) }
        runCurrent()
        assertEquals(listOf("grammar-only", "mixed"), grammar.lessons.value.map { it.id })
        assertEquals(listOf("vocab-only", "mixed"), vocabulary.lessons.value.map { it.id })
        assertEquals(listOf("mixed"), kanji.lessons.value.map { it.id })
    }
}
