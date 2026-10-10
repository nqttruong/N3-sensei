package com.n3sensei.app.feature.vocabulary

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.FakeProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeProgressRepository()
    private val repository = FakeContentRepository().apply {
        vocabulary.value = listOf(TestContent.vocabulary("v1", "l1", "経験"))
    }

    @Test
    fun existingItem_isSuccess() {
        val state = VocabularyDetailViewModel("v1", repository, progress).state.value
        assertEquals("経験", (state as UiState.Success).data.vocabulary.expression)
    }

    @Test
    fun missingItem_isNotFound() {
        assertEquals(UiState.NotFound, VocabularyDetailViewModel("missing", repository, progress).state.value)
    }

    @Test
    fun failure_isError_andRefreshRecovers() {
        repository.failDetails = true
        val viewModel = VocabularyDetailViewModel("v1", repository, progress)
        assertTrue(viewModel.state.value is UiState.Error)

        repository.failDetails = false
        viewModel.refresh()
        assertTrue(viewModel.state.value is UiState.Success)
    }

    @Test
    fun toggleMark_marksAndUnmarksThroughRepository() = runTest {
        val viewModel = VocabularyDetailViewModel("v1", repository, progress)
        viewModel.itemState.launchIn(backgroundScope)
        runCurrent()
        assertFalse(viewModel.itemState.value!!.isMarked)

        viewModel.toggleMark()
        runCurrent()
        assertEquals(setOf(ItemRef(ItemType.VOCABULARY, "v1")), progress.marks.value)
        assertTrue(viewModel.itemState.value!!.isMarked)

        viewModel.toggleMark()
        runCurrent()
        assertTrue(progress.marks.value.isEmpty())
    }
}
