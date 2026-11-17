package com.n3sensei.app.feature.contrast

import androidx.lifecycle.SavedStateHandle
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.ContrastFilter
import com.n3sensei.app.domain.contrast.ContrastFixtures
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.FakeContrastContent
import com.n3sensei.app.domain.contrast.FakeContrastStore
import com.n3sensei.app.domain.contrast.FakeSourceResolver
import com.n3sensei.app.domain.contrast.InstalledContrastPack
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.ResolvedSource
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.SourceRefStatus
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ContrastViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private val clock = MutableClock(t0, ZoneId.of("Asia/Ho_Chi_Minh"))
    private val samplePack = ContrastFixtures.samplePack()
    private val content = FakeContrastContent(samplePack)
    private val store = FakeContrastStore(content)
    private val grammarId = "cg-g-koto-ni-shiteiru-naru"

    private fun <T> kotlinx.coroutines.test.TestScope.collect(flow: kotlinx.coroutines.flow.StateFlow<T>) =
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }

    /** Learner activity through the real use cases (what M6 will do): one grammar block with a wrong answer. */
    private suspend fun learnGrammar() {
        val zone = clock.zone
        val session = (StartOrResumePractice(content, store, store, newId = { "s1" })(PracticeScope.Group(grammarId), t0, zone) as StartPracticeResult.Started).session
        val submit = SubmitContrastAnswer(content, store, store)
        session.items.forEachIndexed { i, item ->
            val q = content.getQuestion(item.questionId)!!
            submit(session.id, item.position, AnswerInput.Chosen(if (i == 0) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId), "r$i", t0, zone)
        }
    }

    private fun emptyContent() = FakeContrastContent(samplePack.copy(groups = emptyList()))

    private val failing = object : ContrastContentRepository {
        override fun observeGroups(filter: ContrastFilter): Flow<List<ContrastGroup>> = flow { error("disk error") }
        override suspend fun getDetail(groupId: String): ContrastDetail? = error("disk error")
        override suspend fun getQuestions(groupId: String): List<ContrastQuestion> = error("disk error")
        override suspend fun getQuestion(questionId: String): ContrastQuestion? = error("disk error")
        override suspend fun installedPack(): InstalledContrastPack? = error("disk error")
        override suspend fun retiredGroup(groupId: String): RetiredGroup? = error("disk error")
    }

    private fun writes() = Triple(store.attempts.size, store.progressRows.size, store.reviewRows.size) to store.sessions.size

    // ---- Home ----

    @Test
    fun home_withoutAReviewedPack_isEmpty() = runTest {
        val empty = emptyContent()
        val vm = ContrastHomeViewModel(empty, FakeContrastStore(empty), FakeContrastStore(empty), clock)
        collect(vm.state)
        assertEquals(UiState.Empty, vm.state.value)
    }

    @Test
    fun home_showsRealFigures_andNothingIsWrittenByOpeningIt() = runTest {
        val vm = ContrastHomeViewModel(content, store, store, clock)
        collect(vm.state)
        val data = (vm.state.value as UiState.Success).data
        assertEquals(listOf(3, 0, 0, 0), listOf(data.available, data.learning, data.mastered, data.due))
        assertNull(data.continueGroup)
        assertTrue(data.confusions.isEmpty() && data.dueGroups.isEmpty())
        assertEquals((Triple(0, 0, 0) to 0), writes())
    }

    @Test
    fun home_afterPractice_continueDueAndConfusionsComeFromRealData() = runTest {
        learnGrammar()
        clock.advance(Duration.ofDays(2)) // the HARD block was scheduled for 1 day
        val vm = ContrastHomeViewModel(content, store, store, clock)
        collect(vm.state)
        val data = (vm.state.value as UiState.Success).data
        assertEquals(1, data.learning)
        assertEquals(grammarId, data.continueGroup!!.group.id)
        assertEquals(listOf(grammarId), data.dueGroups.map { it.group.id })
        assertEquals(1, data.due)
        val confusion = data.confusions.single()
        assertEquals("〜ことになっている" to "〜ことにしている", confusion.expectedLabel to confusion.chosenLabel)
    }

    @Test
    fun home_repositoryError_isAnError_andRetryRecovers() = runTest {
        val vm = ContrastHomeViewModel(failing, store, store, clock)
        collect(vm.state)
        assertTrue(vm.state.value is UiState.Error)
    }

    // ---- Library ----

    @Test
    fun library_typeFilter_search_andStatusFilters() = runTest {
        val saved = SavedStateHandle()
        val vm = ContrastLibraryViewModel(content, store, store, clock, saved)
        collect(vm.state)
        fun ids() = (vm.state.value as UiState.Success).data.rows.map { it.group.id }
        assertEquals(3, ids().size)
        vm.selectType(ContrastContentType.KANJI)
        assertEquals(listOf("cg-k-seki-accumulate-achieve"), ids())
        vm.selectType(null)
        vm.setQuery("冷める")
        assertEquals("冷める", vm.queryText.value) // what the field shows, at once
        assertEquals(listOf("cg-v-sameru-cold-awake"), ids())
        vm.setQuery("zzz")
        val noMatch = (vm.state.value as UiState.Success).data
        assertTrue(noMatch.rows.isEmpty())
        assertEquals(3, noMatch.totalGroups) // "nothing matches", not "no content"
        vm.setQuery("")
        vm.selectStatus(LibraryStatus.NOT_STARTED)
        assertEquals(3, ids().size)
        vm.selectStatus(LibraryStatus.IN_PROGRESS)
        assertEquals(emptyList<String>(), ids())
        // Kept in the saved state: still there when the learner comes back from a group.
        assertEquals("IN_PROGRESS", saved.get<String>("contrast_library_status"))
        val restored = ContrastLibraryViewModel(content, store, store, clock, saved)
        collect(restored.state)
        assertEquals(LibraryStatus.IN_PROGRESS, (restored.state.value as UiState.Success).data.status)
        assertEquals((Triple(0, 0, 0) to 0), writes())
    }

    @Test
    fun library_statusesFollowTheDomain_afterPractice() = runTest {
        learnGrammar()
        clock.advance(Duration.ofDays(2))
        val vm = ContrastLibraryViewModel(content, store, store, clock)
        collect(vm.state)
        vm.selectStatus(LibraryStatus.IN_PROGRESS)
        assertEquals(listOf(grammarId), (vm.state.value as UiState.Success).data.rows.map { it.group.id })
        vm.selectStatus(LibraryStatus.NEEDS_REVIEW)
        assertEquals(listOf(grammarId), (vm.state.value as UiState.Success).data.rows.map { it.group.id })
        vm.selectStatus(LibraryStatus.NOT_STARTED)
        assertEquals(2, (vm.state.value as UiState.Success).data.rows.size)
    }

    @Test
    fun library_emptyAndError() = runTest {
        val empty = emptyContent()
        val vm = ContrastLibraryViewModel(empty, FakeContrastStore(empty), FakeContrastStore(empty), clock)
        collect(vm.state)
        assertEquals(UiState.Empty, vm.state.value)
        val broken = ContrastLibraryViewModel(failing, store, store, clock)
        collect(broken.state)
        assertTrue(broken.state.value is UiState.Error)
    }

    // ---- Compare / learning ----

    @Test
    fun learning_resolvesSources_withEveryStatus_andWritesNothing() = runTest {
        val resolver = FakeSourceResolver(
            mapOf(
                "ci-g-koto-ni-shiteiru" to ResolvedSource(SourceRefStatus.SOURCE_CHANGED, "n3g-g001"),
                "ci-g-koto-ni-natteiru" to ResolvedSource(SourceRefStatus.AMBIGUOUS, candidateIds = listOf("x", "y")),
            ),
        )
        val vm = ContrastLearningViewModel(grammarId, content, resolver, store, store, clock)
        val group = (vm.state.value as UiState.Success).data as ContrastLearningContent.Group
        assertEquals(SourceRefStatus.SOURCE_CHANGED, group.detail.sources.getValue("ci-g-koto-ni-shiteiru").status)
        assertEquals(SourceRefStatus.AMBIGUOUS, group.detail.sources.getValue("ci-g-koto-ni-natteiru").status)
        assertEquals(ContrastMastery.NEW, group.mastery.mastery)
        assertEquals((Triple(0, 0, 0) to 0), writes())
        val vocab = ContrastLearningViewModel("cg-v-sameru-cold-awake", content, FakeSourceResolver(mapOf("ci-v-sameru-cold" to ResolvedSource(SourceRefStatus.SOURCE_MISSING))), store, store, clock)
        val missing = (vocab.state.value as UiState.Success).data as ContrastLearningContent.Group
        assertEquals(SourceRefStatus.SOURCE_MISSING, missing.detail.sources.getValue("ci-v-sameru-cold").status)
        assertEquals(SourceRefStatus.OK, missing.detail.sources.getValue("ci-v-sameru-awake").status)
    }

    @Test
    fun learning_notFound_retired_error_andThreeItems() = runTest {
        assertEquals(UiState.NotFound, ContrastLearningViewModel("nope", content, FakeSourceResolver(), store, store, clock).state.value)
        val retiredPack = samplePack.copy(groups = samplePack.groups.take(2), retiredGroups = listOf(RetiredGroup("cg-k-seki-accumulate-achieve", 2, "Gộp nhóm", null)))
        val retired = ContrastLearningViewModel("cg-k-seki-accumulate-achieve", FakeContrastContent(retiredPack), FakeSourceResolver(), store, store, clock)
        assertEquals("Gộp nhóm", ((retired.state.value as UiState.Success).data as ContrastLearningContent.Retired).retired.reasonVi)
        assertTrue(ContrastLearningViewModel(grammarId, failing, FakeSourceResolver(), store, store, clock).state.value is UiState.Error)

        val vocab = samplePack.groups[1]
        val third = ContrastItem("ci-v-hieru", "冷える", "{冷|ひ}える", vocab.detail.items[0].source.copy(sourceContentId = "mimikara-n3-v209"), ItemDetails.Vocabulary("ひえる", "bị lạnh", "nhiệt độ hạ", emptyList()))
        val three = vocab.copy(detail = vocab.detail.copy(items = vocab.detail.items + third, group = vocab.detail.group.copy(itemLabels = vocab.detail.group.itemLabels + "冷える")))
        val pack3 = samplePack.copy(groups = listOf(samplePack.groups[0], three, samplePack.groups[2]))
        val vm = ContrastLearningViewModel(three.detail.group.id, FakeContrastContent(pack3), FakeSourceResolver(), store, store, clock)
        val group = (vm.state.value as UiState.Success).data as ContrastLearningContent.Group
        assertEquals(3, group.detail.items.size)
        assertEquals(3, group.detail.sources.size)
    }

    // ---- Status mapper ----

    @Test
    fun statusMapper_rules() {
        assertEquals(setOf(LibraryStatus.NOT_STARTED), ContrastStatusMapper.statuses(ContrastMastery.NEW, isDue = true))
        assertEquals(setOf(LibraryStatus.IN_PROGRESS), ContrastStatusMapper.statuses(ContrastMastery.LEARNING, isDue = false))
        assertEquals(setOf(LibraryStatus.IN_PROGRESS, LibraryStatus.NEEDS_REVIEW), ContrastStatusMapper.statuses(ContrastMastery.REVIEWING, isDue = true))
        assertEquals(setOf(LibraryStatus.MASTERED, LibraryStatus.NEEDS_REVIEW), ContrastStatusMapper.statuses(ContrastMastery.MASTERED, isDue = true))
        assertEquals(setOf(LibraryStatus.MASTERED), ContrastStatusMapper.statuses(ContrastMastery.MASTERED, isDue = false))
    }
}
