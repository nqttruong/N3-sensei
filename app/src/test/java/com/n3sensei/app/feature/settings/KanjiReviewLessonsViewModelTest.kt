package com.n3sensei.app.feature.settings

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KanjiReviewLessonsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun lesson(id: String, pack: String, number: Int, level: String, kanji: Int, vocabulary: Int = 0) =
        LessonSummary(id, pack, number, "$level · bài $number", level, null, vocabulary, kanji, 0)

    private val n3 = (1..3).map { lesson("n3k-l0$it", "n3-kanji", it, "N3", 24) }
    private val n2 = (1..4).map { lesson("n2k-l0$it", "n2-kanji", it, "N2", 23) }
    private val content = FakeContentRepository().apply {
        // A vocabulary lesson (no kanji) is not offered.
        lessons.value = n2 + n3 + lesson("mimikara-l01", "mimikara", 1, "N3", 0, vocabulary = 120)
    }
    private val prefs = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private val saved get() = prefs.preferences.value.kanjiReviewLessons

    private fun KanjiReviewLessonsViewModel.data() = (state.value as UiState.Success).data

    @Test
    fun levelsN3ThenN2_kanjiLessonsOnly_n3AllAndN2NoneByDefault() = runTest {
        val vm = KanjiReviewLessonsViewModel(prefs, content)
        vm.state.launchIn(this).also { testScheduler.advanceUntilIdle() }
        assertEquals(listOf("N3", "N2"), vm.data().levels.map { it.level })
        assertEquals(n3.map { it.id }, vm.data().levels[0].lessons.map { it.id })
        assertEquals(n2.map { it.id }, vm.data().levels[1].lessons.map { it.id })
        assertTrue(n3.all { vm.data().levels[0].isChecked(it.id) })
        assertEquals(0, vm.data().levels[1].checkedCount)
        coroutineContext.cancelChildren()
    }

    @Test
    fun eachN2Lesson_canBeChosenOnItsOwn_andN3StaysAsItWas() = runTest {
        val vm = KanjiReviewLessonsViewModel(prefs, content)
        vm.state.launchIn(this).also { testScheduler.advanceUntilIdle() }
        vm.toggle("N2", "n2k-l02")
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.Only(setOf("n2k-l02")), saved.of("N2"))
        assertEquals(LessonSelection.All, saved.of("N3"))
        vm.toggle("N2", "n2k-l04")
        vm.toggle("N2", "n2k-l02")
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.Only(setOf("n2k-l04")), saved.of("N2"))
        assertEquals(1, vm.data().levels[1].checkedCount)
        assertTrue(vm.data().levels[1].isChecked("n2k-l04"))
        coroutineContext.cancelChildren()
    }

    @Test
    fun selectAllAndClearAll_ofN2_leaveN3Alone() = runTest {
        val vm = KanjiReviewLessonsViewModel(prefs, content)
        vm.state.launchIn(this).also { testScheduler.advanceUntilIdle() }
        vm.toggle("N3", "n3k-l01") // the learner left an N3 lesson out
        vm.selectAll("N2")
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.All, saved.of("N2"))
        assertEquals(4, vm.data().levels[1].checkedCount)
        assertEquals(LessonSelection.Only(setOf("n3k-l02", "n3k-l03")), saved.of("N3"))
        vm.clearAll("N2")
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.Only(emptySet()), saved.of("N2"))
        assertEquals(LessonSelection.Only(setOf("n3k-l02", "n3k-l03")), saved.of("N3"))
        // Checking every lesson one by one is "all" again (lessons added later follow).
        n2.forEach { vm.toggle("N2", it.id) }
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.All, saved.of("N2"))
        coroutineContext.cancelChildren()
    }

    @Test
    fun savingIntervalsAndLimits_neverChangesTheLessonsChosen() = runTest {
        val vm = KanjiReviewLessonsViewModel(prefs, content)
        vm.state.launchIn(this).also { testScheduler.advanceUntilIdle() }
        vm.toggle("N2", "n2k-l01")
        testScheduler.advanceUntilIdle()
        prefs.setReviewSettings(ReviewSettings(limits = DailyLimits(newKanji = 5)))
        assertEquals(LessonSelection.Only(setOf("n2k-l01")), saved.of("N2"))
        assertEquals(5, prefs.preferences.value.reviewSettings.limits.newKanji)
        coroutineContext.cancelChildren()
    }

    @Test
    fun allowedLessons_followTheChoice_perLevel() {
        val all = n2 + n3
        assertEquals(n3.map { it.id }.toSet(), KanjiReviewLessons().allowedLessons(all))
        val chosen = KanjiReviewLessons()
            .with("N2", LessonSelection.Only(setOf("n2k-l03", "gone")))
            .with("N3", LessonSelection.Only(emptySet()))
        assertEquals(setOf("n2k-l03"), chosen.allowedLessons(all))
        assertEquals(LessonSelection.All, KanjiReviewLessons.default("N3"))
        assertEquals(LessonSelection.Only(emptySet()), KanjiReviewLessons.default("N2"))
        assertFalse(KanjiReviewLessons().of("N2").allows("n2k-l01"))
    }
}
