package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderNotifier
import com.n3sensei.app.domain.reminder.ReminderOutcome
import com.n3sensei.app.domain.reminder.ReminderRunner
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reminder preferences. Every change is saved and the background schedule is re-applied, so
 * turning reminders off cancels the work at once and a new frequency takes effect.
 */
class ReminderSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val runner: ReminderRunner,
) : ViewModel() {

    val settings: StateFlow<ReminderSettings?> = preferencesRepository.preferences
        .map { it.reminderSettings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _notificationsAllowed = MutableStateFlow(notifier.canPostNotifications())

    /** Re-checked whenever the screen resumes (the user may have changed system settings). */
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()

    private val _testOutcome = MutableStateFlow<ReminderOutcome?>(null)
    val testOutcome: StateFlow<ReminderOutcome?> = _testOutcome.asStateFlow()

    private val writeLock = Mutex()

    fun refreshPermission() {
        _notificationsAllowed.value = notifier.canPostNotifications()
    }

    /**
     * Turning reminders on is only saved when notifications can actually be posted; the screen
     * asks for the permission first (Android 13+).
     */
    fun setEnabled(enabled: Boolean) {
        refreshPermission()
        if (enabled && !_notificationsAllowed.value) return
        update { it.copy(enabled = enabled) }
    }

    fun setStartHour(hour: Int) = update { it.copy(startHour = hour) }
    fun setEndHour(hour: Int) = update { it.copy(endHour = hour) }
    fun setFrequency(frequency: ReminderFrequency) = update { it.copy(frequency = frequency) }
    fun setShowReading(show: Boolean) = update { it.copy(showReading = show) }
    fun setShowMeaning(show: Boolean) = update { it.copy(showMeaning = show) }
    fun setLockScreen(privacy: LockScreenPrivacy) = update { it.copy(lockScreen = privacy) }

    fun sendTest() {
        viewModelScope.launch { _testOutcome.value = runner.showNow() }
    }

    private fun update(transform: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch {
            // Serialized so quick successive taps cannot interleave read-modify-write.
            writeLock.withLock {
                val current = preferencesRepository.preferences.first().reminderSettings
                val next = transform(current)
                if (next == current) return@withLock
                preferencesRepository.setReminderSettings(next)
                if (next.scheduleKey() != current.scheduleKey()) scheduler.apply(next)
            }
        }
    }
}
