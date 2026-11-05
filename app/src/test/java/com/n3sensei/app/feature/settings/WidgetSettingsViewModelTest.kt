package com.n3sensei.app.feature.settings

import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.widget.WidgetMode
import com.n3sensei.app.domain.widget.WidgetTap
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WidgetSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private var refreshes = 0
    private val viewModel by lazy { WidgetSettingsViewModel(prefs, FakeContentRepository()) { refreshes++ } }
    private val saved get() = prefs.preferences.value.widgetSettings

    @Test
    fun everyChange_isSaved_andRefreshesTheWidgets() {
        viewModel.setMode(WidgetMode.KANJI)
        viewModel.setShowFurigana(false)
        viewModel.setShowMeaning(true)
        viewModel.setTap(WidgetTap.NEXT)
        assertEquals(WidgetMode.KANJI, saved.mode)
        assertFalse(saved.showFurigana)
        assertTrue(saved.showMeaning)
        assertEquals(WidgetTap.NEXT, saved.tap)
        assertEquals(4, refreshes)
    }

    @Test
    fun choosingALesson_switchesToLessonMode_andNoChangeMeansNoRefresh() {
        viewModel.setLesson(3)
        assertEquals(WidgetMode.LESSON, saved.mode)
        assertEquals(3, saved.lesson)
        viewModel.setLesson(3)
        assertEquals(1, refreshes)
    }
}
