package com.n3sensei.app.feature.settings

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.reminder.ReminderContent
import com.n3sensei.app.domain.reminder.ReminderLessons
import com.n3sensei.app.domain.reminder.ReminderRunner
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeReminderHistory
import com.n3sensei.app.testing.FakeReminderNotifier
import com.n3sensei.app.testing.FakeReminderScheduler
import com.n3sensei.app.testing.FakeReminderStateStore
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderLessonsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val content = FakeContentRepository().apply {
        lessons.value = listOf(TestContent.lesson("l1", 1), TestContent.lesson("l2", 2))
        vocabulary.value = (1..35).map { TestContent.vocabulary("a$it", "l1", "語$it") } + TestContent.vocabulary("b1", "l2", "言")
    }
    private val prefs = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private val saved get() = prefs.preferences.value.reminderSettings.lessons.vocabulary

    private fun ReminderLessonsViewModel.data() = (state.value as UiState.Success).data

    @Test
    fun theLessonsComeFromTheContent_andFollowIt() = runTest {
        val vm = ReminderLessonsViewModel(ItemType.VOCABULARY, prefs, content)
        vm.state.launchIn(this).also { testScheduler.advanceUntilIdle() }
        assertEquals(listOf("1.1", "1.2", "2"), vm.data().options.map { it.label })
        assertTrue(vm.data().isChecked("l1#2")) // every lesson by default
        content.lessons.value = content.lessons.value + TestContent.lesson("l3", 3)
        content.vocabulary.value = content.vocabulary.value + TestContent.vocabulary("c1", "l3", "話")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("1.1", "1.2", "2", "3"), vm.data().options.map { it.label })
        coroutineContext.cancelChildren()
    }

    @Test
    fun toggleSelectAllAndClear_areSaved() = runTest {
        val vm = ReminderLessonsViewModel(ItemType.VOCABULARY, prefs, content)
        vm.state.launchIn(this).also { testScheduler.advanceUntilIdle() }
        vm.toggle("l1#1")
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.Only(setOf("l1#2", "l2#1")), saved)
        assertFalse(vm.data().isChecked("l1#1"))
        vm.clearAll()
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.Only(emptySet()), saved)
        vm.toggle("l2#1")
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.Only(setOf("l2#1")), saved)
        vm.selectAll()
        testScheduler.advanceUntilIdle()
        assertEquals(LessonSelection.All, saved)
        // The other kinds are left alone.
        assertEquals(LessonSelection.All, prefs.preferences.value.reminderSettings.lessons.kanji)
        coroutineContext.cancelChildren()
    }

    @Test
    fun quickChoices_inTheSettings_coverEveryKindOrOnlyReviews() = runTest {
        prefs.setReminderSettings(
            ReminderSettings(content = setOf(ReminderContent.KANJI), lessons = ReminderLessons(kanji = LessonSelection.Only(emptySet()))),
        )
        val runner = ReminderRunner(prefs, FakeReminderStateStore(), content, FakeProgressRepository(), FakeReminderNotifier(), MutableClock(), FakeReminderHistory())
        val vm = ReminderSettingsViewModel(prefs, FakeReminderScheduler(), FakeReminderNotifier(), runner, content)
        vm.chooseAllContent()
        testScheduler.advanceUntilIdle()
        val all = prefs.preferences.first().reminderSettings
        assertEquals(ReminderContent.entries.toSet(), all.content)
        assertEquals(ReminderLessons(), all.lessons)
        vm.chooseReviewOnly()
        testScheduler.advanceUntilIdle()
        assertTrue(prefs.preferences.first().reminderSettings.reviewOnly)
    }
}
