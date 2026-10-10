package com.n3sensei.app.feature.vocabulary

import com.n3sensei.app.core.ui.state.SEARCH_DEBOUNCE_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeContentRepository()
    private lateinit var viewModel: VocabularyListViewModel

    @Before
    fun setUp() {
        repository.vocabulary.value = listOf(
            TestContent.vocabulary("v1", "l1", "経験", "けいけん"),
            TestContent.vocabulary("v2", "l1", "増える", "ふえる"),
            TestContent.vocabulary("v3", "l2", "届ける", "とどける"),
        )
        viewModel = VocabularyListViewModel(repository)
    }

    private fun ids() = (viewModel.items.value as UiState.Success).data.map { it.item.id }

    @Test
    fun blankQuery_showsAllItems_thenFiltersByLessonImmediately() = runTest {
        viewModel.items.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf("v1", "v2", "v3"), ids())

        viewModel.onLessonSelected("l2")
        runCurrent()
        assertEquals(listOf("v3"), ids())
    }

    @Test
    fun typing_isDebouncedBeforeQuerying() = runTest {
        viewModel.items.launchIn(backgroundScope)
        runCurrent()
        val queriesBefore = repository.browseQueries.size

        viewModel.onQueryChange("ふ")
        viewModel.onQueryChange("ふえ")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertEquals(queriesBefore, repository.browseQueries.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals("ふえ", repository.browseQueries.last().text)
        assertEquals(listOf("v2"), ids())
    }

    @Test
    fun clearingText_appliesWithoutDelay() = runTest {
        viewModel.items.launchIn(backgroundScope)
        viewModel.onQueryChange("ふえ")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        runCurrent()
        viewModel.onQueryChange("")
        runCurrent()
        assertEquals(listOf("v1", "v2", "v3"), ids())
    }

    @Test
    fun markedOnly_showsMarkedItems_andListUpdatesWhenMarksChange() = runTest {
        viewModel.items.launchIn(backgroundScope)
        viewModel.onMarkedOnlyChange(true)
        runCurrent()
        assertEquals(UiState.Empty, viewModel.items.value)

        repository.marked.value = setOf("v3")
        runCurrent()
        assertEquals(listOf("v3"), ids())
        assertTrue((viewModel.items.value as UiState.Success).data.single().isMarked)
    }

    @Test
    fun statusAndTagFilters_arePassedToRepository() = runTest {
        viewModel.items.launchIn(backgroundScope)
        viewModel.onStatusSelected(LearningStatus.REVIEW)
        viewModel.onTagSelected("topic:work")
        runCurrent()
        val last = repository.browseQueries.last()
        assertEquals(LearningStatus.REVIEW, last.status)
        assertEquals("topic:work", last.tagId)
        assertEquals(UiState.Empty, viewModel.items.value)
    }

    @Test
    fun noMatch_isEmptyState() = runTest {
        viewModel.items.launchIn(backgroundScope)
        viewModel.onQueryChange("zzz")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertEquals(UiState.Empty, viewModel.items.value)
    }
}
