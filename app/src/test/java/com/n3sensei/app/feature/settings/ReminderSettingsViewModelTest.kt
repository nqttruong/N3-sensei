package com.n3sensei.app.feature.settings

import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderOutcome
import com.n3sensei.app.domain.reminder.ReminderRunner
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeReminderNotifier
import com.n3sensei.app.testing.FakeReminderScheduler
import com.n3sensei.app.testing.FakeReminderStateStore
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private val scheduler = FakeReminderScheduler()
    private val notifier = FakeReminderNotifier(allowed = false)
    private val runner = ReminderRunner(prefs, FakeReminderStateStore(), FakeContentRepository(), FakeProgressRepository(), notifier, MutableClock())
    private val viewModel by lazy { ReminderSettingsViewModel(prefs, scheduler, notifier, runner) }

    private val saved get() = prefs.preferences.value.reminderSettings

    @Test
    fun enabling_withoutPermission_isNotSaved() {
        viewModel.setEnabled(true)
        assertFalse(saved.enabled)
        assertFalse(viewModel.notificationsAllowed.value)
        assertTrue(scheduler.applied.isEmpty())
    }

    @Test
    fun enabling_afterPermissionGranted_savesAndSchedules_disablingCancels() {
        notifier.allowed = true
        viewModel.setEnabled(true)
        assertTrue(saved.enabled)
        assertTrue(scheduler.applied.single().enabled)

        viewModel.setEnabled(false)
        assertFalse(saved.enabled)
        assertFalse(scheduler.applied.last().enabled)
    }

    @Test
    fun frequencyChange_reschedules_windowAndContentChangesDoNot() {
        notifier.allowed = true
        viewModel.setEnabled(true)
        viewModel.setStartHour(7)
        viewModel.setEndHour(23)
        viewModel.setShowMeaning(false)
        viewModel.setLockScreen(LockScreenPrivacy.HIDE_ALL)
        assertEquals(1, scheduler.applied.size)

        viewModel.setFrequency(ReminderFrequency.EVERY_6_HOURS)
        assertEquals(2, scheduler.applied.size)
        assertEquals(ReminderFrequency.EVERY_6_HOURS, scheduler.applied.last().frequency)
        assertEquals(7, saved.startHour)
        assertEquals(23, saved.endHour)
        assertFalse(saved.showMeaning)
        assertEquals(LockScreenPrivacy.HIDE_ALL, saved.lockScreen)
    }

    @Test
    fun permissionStateIsRefreshedOnResume() {
        assertFalse(viewModel.notificationsAllowed.value)
        notifier.allowed = true
        viewModel.refreshPermission()
        assertTrue(viewModel.notificationsAllowed.value)
    }

    @Test
    fun testNotification_reportsOutcome() {
        viewModel.sendTest()
        assertEquals(ReminderOutcome.NOTIFICATIONS_BLOCKED, viewModel.testOutcome.value)
        notifier.allowed = true
        viewModel.sendTest()
        assertEquals(ReminderOutcome.NOTHING_TO_SHOW, viewModel.testOutcome.value) // no content
    }
}
