package com.n3sensei.app.feature.settings

import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private val viewModel by lazy { ReviewSettingsViewModel(prefs) }

    private fun draft() = viewModel.draft.value!!

    @Test
    fun loadsStoredSettings() {
        assertEquals(7, draft().steps.size)
        assertEquals("20", draft().steps.first().amount)
        assertEquals(IntervalUnit.MONTHS, draft().steps.last().unit)
        assertEquals("20", draft().newPerDay)
    }

    @Test
    fun validEdit_isSaved() {
        viewModel.setAmount(0, "10")
        viewModel.setUnit(6, IntervalUnit.WEEKS)
        viewModel.setAmount(6, "4")
        viewModel.setNewPerDay("5")
        viewModel.save()

        val saved = prefs.preferences.value.reviewSettings
        assertEquals("10m,1h,1d,2d,1w,2w,4w", saved.ladder.encode())
        assertEquals(5, saved.limits.newPerDay)
        assertTrue(draft().saved)
    }

    @Test
    fun invalidEdit_isNotSaved_andProblemsAreReported() {
        viewModel.setAmount(2, "") // empty amount
        viewModel.setAmount(4, "1")
        viewModel.setUnit(4, IntervalUnit.HOURS) // 1h after 2 days: not increasing (checked once amounts are valid)
        viewModel.save()
        assertEquals(listOf(SettingsProblem.InvalidAmount(2)), draft().problems)

        viewModel.setAmount(2, "1")
        viewModel.setReviewsPerDay("0")
        viewModel.save()
        assertEquals(listOf(SettingsProblem.NotIncreasing(4), SettingsProblem.InvalidLimits), draft().problems)
        assertEquals(ReviewSettings(), prefs.preferences.value.reviewSettings)
    }

    @Test
    fun removingAllSteps_isRejected_resetRestoresDefault() {
        repeat(7) { viewModel.removeStep(0) }
        viewModel.save()
        assertEquals(listOf(SettingsProblem.Empty), draft().problems)

        viewModel.resetToDefault()
        viewModel.save()
        assertEquals(ReviewLadder.DEFAULT, prefs.preferences.value.reviewSettings.ladder)
    }

    @Test
    fun parse_acceptsAddedSteps() {
        viewModel.addStep()
        assertEquals(8, draft().steps.size)
        assertEquals("2", draft().steps.last().amount)
        assertEquals(IntervalUnit.MONTHS, draft().steps.last().unit)
        viewModel.save()
        assertEquals("20m,1h,1d,2d,1w,2w,1mo,2mo", prefs.preferences.value.reviewSettings.ladder.encode())
    }
}
