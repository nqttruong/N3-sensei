package com.n3sensei.app.feature.kanji

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Học → Kanji with N3 and N2 lessons: one level at a time, N3 first, never mixed. */
class KanjiListLevelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun lesson(id: String, pack: String, number: Int, level: String) =
        LessonSummary(id, pack, number, "Kanji $level · bài $number", level, null, 0, 2, 0)

    private val content = FakeContentRepository().apply {
        lessons.value = listOf(lesson("n2-l1", "n2-kanji", 1, "N2"), lesson("n3-l1", "n3-kanji", 1, "N3"), lesson("n3-l2", "n3-kanji", 2, "N3"))
        kanji.value = listOf(
            TestContent.kanji("n2a", "n2-l1", "井"), TestContent.kanji("n2b", "n2-l1", "丼"),
            TestContent.kanji("n3a", "n3-l1", "意"), TestContent.kanji("n3b", "n3-l2", "味"),
        )
    }

    private fun KanjiListViewModel.ids() = (items.value as UiState.Success).data.map { it.item.id }

    @Test
    fun n3IsShownFirst_n2OnlyWhenChosen_neverBoth() = runTest {
        val vm = KanjiListViewModel(content)
        listOf(vm.items, vm.levels, vm.level, vm.lessons).forEach { it.launchIn(this) }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("N3", "N2"), vm.levels.value)
        assertEquals("N3", vm.level.value)
        assertEquals(listOf("n3a", "n3b"), vm.ids())
        assertEquals(listOf("n3-l1", "n3-l2"), vm.lessons.value.map { it.id })

        vm.onLevelSelected("N2")
        testScheduler.advanceUntilIdle()
        assertEquals("N2", vm.level.value)
        assertEquals(listOf("n2a", "n2b"), vm.ids())
        assertEquals(listOf("n2-l1"), vm.lessons.value.map { it.id })
        coroutineContext.cancelChildren()
    }

    @Test
    fun aLessonFilter_isClearedWhenTheLevelChanges_andALessonShowsItsOwnLevel() = runTest {
        val vm = KanjiListViewModel(content)
        listOf(vm.items, vm.levels, vm.level, vm.lessons).forEach { it.launchIn(this) }
        vm.onLessonSelected("n3-l2")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("n3b"), vm.ids())
        vm.onLevelSelected("N3") // the level shown: nothing changes
        testScheduler.advanceUntilIdle()
        assertEquals("n3-l2", vm.filters.value.lessonId)
        vm.onLevelSelected("N2")
        testScheduler.advanceUntilIdle()
        assertNull(vm.filters.value.lessonId)
        assertEquals(listOf("n2a", "n2b"), vm.ids())
        coroutineContext.cancelChildren()
    }

    @Test
    fun withOneLevelOnly_noLevelChoice_andTheListIsAsBefore() = runTest {
        content.lessons.value = content.lessons.value.filter { it.jlptLevel == "N3" }
        content.kanji.value = content.kanji.value.filter { it.lessonId.startsWith("n3") }
        val vm = KanjiListViewModel(content)
        listOf(vm.items, vm.levels, vm.level, vm.lessons).forEach { it.launchIn(this) }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("N3"), vm.levels.value)
        assertNull(vm.level.value)
        assertEquals(listOf("n3a", "n3b"), vm.ids())
        assertNull(content.browseQueries.last().jlptLevel)
        coroutineContext.cancelChildren()
    }
}
