package com.n3sensei.app.feature.vocabulary

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VocabularyDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository().apply {
        vocabulary.value = listOf(TestContent.vocabulary("v1", "l1", "経験"))
    }

    @Test
    fun existingItem_isSuccess() {
        val state = VocabularyDetailViewModel("v1", repository).state.value
        assertEquals("経験", (state as UiState.Success).data.vocabulary.expression)
    }

    @Test
    fun missingItem_isNotFound() {
        assertEquals(UiState.NotFound, VocabularyDetailViewModel("missing", repository).state.value)
    }

    @Test
    fun failure_isError_andRefreshRecovers() {
        repository.failDetails = true
        val viewModel = VocabularyDetailViewModel("v1", repository)
        assertTrue(viewModel.state.value is UiState.Error)

        repository.failDetails = false
        viewModel.refresh()
        assertTrue(viewModel.state.value is UiState.Success)
    }
}
