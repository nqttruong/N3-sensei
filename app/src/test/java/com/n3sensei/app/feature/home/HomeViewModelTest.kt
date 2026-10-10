package com.n3sensei.app.feature.home

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ProgressSummary
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val summary = ProgressSummary(trackedItems = 2, learning = 1, inReview = 1, dueNow = 1, totalReviews = 5)

    private val progress = object : ProgressRepository {
        var requestedAt: Instant? = null
        override fun observeSummary(now: Instant): Flow<ProgressSummary> = flowOf(summary).also { requestedAt = now }
        override fun observeProgress(item: ItemRef) = flowOf<LearningProgress?>(null)
        override fun observeDue(now: Instant, limit: Int) = flowOf(emptyList<LearningProgress>())
        override fun observeDueCount(now: Instant) = flowOf(0)
        override fun observeRatingCounts() = flowOf(emptyMap<ReviewRating, Int>())
        override suspend fun recordReview(progress: LearningProgress, record: ReviewRecord) = Unit
        override suspend fun getReviewHistory(item: ItemRef, limit: Int) = emptyList<ReviewRecord>()
    }

    @Test
    fun noContent_isReportedAsEmptyTotals() = runTest {
        val viewModel = HomeViewModel(FakeContentRepository(), progress, MutableClock())
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        val ui = (viewModel.state.value as UiState.Success).data
        assertTrue(ui.totals.isEmpty)
        assertEquals(summary, ui.progress)
    }

    @Test
    fun totalsAndSummaryComeFromRepositories_atClockTime() = runTest {
        val content = FakeContentRepository().apply {
            lessons.value = listOf(TestContent.lesson("l1", 1))
            vocabulary.value = listOf(TestContent.vocabulary("v1", "l1", "経験"))
        }
        val clock = MutableClock()
        val viewModel = HomeViewModel(content, progress, clock)
        viewModel.state.launchIn(backgroundScope)
        runCurrent()

        val ui = (viewModel.state.value as UiState.Success).data
        assertEquals(1, ui.totals.lessons)
        assertEquals(1, ui.totals.vocabulary)
        assertEquals(clock.now, progress.requestedAt)
    }
}
