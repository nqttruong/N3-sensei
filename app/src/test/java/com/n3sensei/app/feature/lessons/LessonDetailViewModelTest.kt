package com.n3sensei.app.feature.lessons

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository().apply {
        lessons.value = listOf(TestContent.lesson("l1", 1), TestContent.lesson("l2", 2))
        vocabulary.value = listOf(TestContent.vocabulary("v1", "l1", "経験"))
        kanji.value = listOf(TestContent.kanji("k1", "l1", "経"))
    }

    @Test
    fun lessonWithItems_groupsItemsOfThatLesson() = runTest {
        val viewModel = LessonDetailViewModel("l1", repository)
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val detail = (viewModel.state.value as UiState.Success).data
        assertEquals(listOf("v1"), detail.vocabulary.map { it.id })
        assertEquals(listOf("k1"), detail.kanji.map { it.id })
        assertEquals(emptyList<Any>(), detail.grammar)
    }

    @Test
    fun emptyLesson_cannotBePracticed() = runTest {
        val viewModel = LessonDetailViewModel("l2", repository)
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        assertFalse((viewModel.state.value as UiState.Success).data.hasItems)
    }

    @Test
    fun unknownLesson_isNotFound() = runTest {
        val viewModel = LessonDetailViewModel("nope", repository)
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        assertEquals(UiState.NotFound, viewModel.state.value)
    }

    @Test
    fun tabSelection_isKept() {
        val viewModel = LessonDetailViewModel("l1", repository)
        viewModel.selectTab(LessonTab.GRAMMAR)
        assertEquals(LessonTab.GRAMMAR, viewModel.selectedTab.value)
    }
}
