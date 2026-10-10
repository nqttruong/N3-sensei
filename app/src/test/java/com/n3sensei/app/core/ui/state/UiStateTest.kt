package com.n3sensei.app.core.ui.state

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    @Test
    fun emitsLoadingThenSuccess() = runTest {
        val states = flowOf(42).asUiState().toList()
        assertEquals(listOf(UiState.Loading, UiState.Success(42)), states)
    }

    @Test
    fun emptyPredicateMapsToEmpty() = runTest {
        val states = flowOf(emptyList<String>(), listOf("a")).asListUiState().toList()
        assertEquals(
            listOf(UiState.Loading, UiState.Empty, UiState.Success(listOf("a"))),
            states,
        )
    }

    @Test
    fun upstreamFailureMapsToErrorAfterLastGoodValue() = runTest {
        val failure = IllegalStateException("db closed")
        val states = flow {
            emit(1)
            throw failure
        }.asUiState().toList()

        assertEquals(3, states.size)
        assertEquals(UiState.Success(1), states[1])
        val error = states[2]
        assertTrue(error is UiState.Error)
        assertSame(failure, (error as UiState.Error).cause)
    }
}
