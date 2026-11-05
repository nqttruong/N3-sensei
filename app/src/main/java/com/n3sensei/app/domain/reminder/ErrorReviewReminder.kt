package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.repository.ErrorRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/** Shows and removes the one "lỗi sai cần ôn" notification. */
interface ErrorReviewNotifier {
    fun canPostNotifications(): Boolean

    /** Shows (or replaces: always the same notification) the reminder for [dueCount] errors. */
    fun showErrorReviews(dueCount: Int)

    fun cancelErrorReviews()
}

/** The day the error reminder was last shown, so it is shown at most once a day. */
interface ErrorReminderStateStore {
    suspend fun lastShownDay(): LocalDate?
    suspend fun setLastShownDay(day: LocalDate)
}

enum class ErrorReminderOutcome { SHOWN, DISABLED, OUTSIDE_WINDOW, NOTHING_DUE, ALREADY_TODAY, NOTIFICATIONS_BLOCKED }

/**
 * Reminds the learner of errors due for review, run with the study reminders. At most one
 * notification a day, inside the reminder hours, always the same one (so never duplicated), and
 * removed once nothing is due or the reminder is turned off.
 */
class ErrorReviewReminder(
    private val preferences: UserPreferencesRepository,
    private val errors: ErrorRepository,
    private val notifier: ErrorReviewNotifier,
    private val state: ErrorReminderStateStore,
    private val clock: Clock,
) {
    suspend fun run(): ErrorReminderOutcome {
        val settings = preferences.preferences.first().reminderSettings
        if (!settings.errorReviews) {
            notifier.cancelErrorReviews()
            return ErrorReminderOutcome.DISABLED
        }
        val now = clock.instant()
        val due = errors.observeDueCount(now.toEpochMilli()).first()
        if (due == 0) {
            notifier.cancelErrorReviews()
            return ErrorReminderOutcome.NOTHING_DUE
        }
        if (!ReminderPolicy.isInWindow(now, clock.zone, settings)) return ErrorReminderOutcome.OUTSIDE_WINDOW
        val today = now.atZone(clock.zone).toLocalDate()
        if (state.lastShownDay() == today) return ErrorReminderOutcome.ALREADY_TODAY
        if (!notifier.canPostNotifications()) return ErrorReminderOutcome.NOTIFICATIONS_BLOCKED
        notifier.showErrorReviews(due)
        state.setLastShownDay(today)
        return ErrorReminderOutcome.SHOWN
    }
}
