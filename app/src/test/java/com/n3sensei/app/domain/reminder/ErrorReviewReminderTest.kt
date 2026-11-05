package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.mistakes.ErrorReviewEntry
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.ReviewQuestionKind
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.ErrorRepository
import com.n3sensei.app.domain.repository.ReviewResult
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ErrorReviewReminderTest {

    /** Only the due count matters here. */
    private class DueErrors(var due: Int) : ErrorRepository {
        override fun observeDueCount(nowMillis: Long): Flow<Int> = flowOf(due)
        override fun observeErrors(): Flow<List<NotebookError>> = flowOf(emptyList())
        override fun observeError(id: Long): Flow<NotebookError?> = flowOf(null)
        override fun observeReviewLog(id: Long): Flow<List<ErrorReviewEntry>> = flowOf(emptyList())
        override suspend fun findByQuestion(questionId: String): Long? = null
        override suspend fun setImportant(id: Long, important: Boolean) = false
        override suspend fun setNote(id: Long, note: String?) = false
        override suspend fun setUnderstood(id: Long, understood: Boolean) = false
        override suspend fun review(id: Long, question: ExamQuestion, kind: ReviewQuestionKind, answer: AnswerInput): ReviewResult? = null
    }

    private class Notifier(var allowed: Boolean = true) : ErrorReviewNotifier {
        /** The notification on screen (one id: at most one), with the count it shows. */
        var shown: Int? = null
        var posts = 0
        override fun canPostNotifications() = allowed
        override fun showErrorReviews(dueCount: Int) {
            shown = dueCount
            posts++
        }
        override fun cancelErrorReviews() {
            shown = null
        }
    }

    private class State : ErrorReminderStateStore {
        var day: LocalDate? = null
        override suspend fun lastShownDay() = day
        override suspend fun setLastShownDay(day: LocalDate) {
            this.day = day
        }
    }

    private val clock = MutableClock(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC)
    private val errors = DueErrors(due = 3)
    private val notifier = Notifier()
    private val state = State()
    private val prefs = FakeUserPreferencesRepository(
        UserPreferences(reminderSettings = ReminderSettings(errorReviews = true, startHour = 8, endHour = 21)),
    )
    private val reminder = ErrorReviewReminder(prefs, errors, notifier, state, clock)

    @Test
    fun dueErrors_areRemindedOnceADay_withOneNotification() = runTest {
        assertEquals(ErrorReminderOutcome.SHOWN, reminder.run())
        assertEquals(3, notifier.shown)
        clock.advance(Duration.ofMinutes((60).toLong()))
        assertEquals(ErrorReminderOutcome.ALREADY_TODAY, reminder.run())
        clock.advance(Duration.ofMinutes((5 * 60).toLong()))
        assertEquals(ErrorReminderOutcome.ALREADY_TODAY, reminder.run())
        assertEquals(1, notifier.posts)
        // The next day, again.
        clock.advance(Duration.ofMinutes((18 * 60).toLong()))
        errors.due = 4
        assertEquals(ErrorReminderOutcome.SHOWN, reminder.run())
        assertEquals(4, notifier.shown)
        assertEquals(2, notifier.posts)
    }

    @Test
    fun nothingDueOrTurnedOff_removesTheNotification() = runTest {
        reminder.run()
        errors.due = 0
        assertEquals(ErrorReminderOutcome.NOTHING_DUE, reminder.run())
        assertEquals(null, notifier.shown)
        errors.due = 2
        state.day = null
        reminder.run()
        prefs.preferences.value = prefs.preferences.value.copy(reminderSettings = ReminderSettings(errorReviews = false))
        assertEquals(ErrorReminderOutcome.DISABLED, reminder.run())
        assertEquals(null, notifier.shown)
    }

    @Test
    fun outsideTheHoursOrWithoutPermission_nothingIsShownAndTheDayIsNotUsedUp() = runTest {
        clock.now = Instant.parse("2026-11-05T22:00:00Z")
        assertEquals(ErrorReminderOutcome.OUTSIDE_WINDOW, reminder.run())
        clock.now = Instant.parse("2026-11-05T10:00:00Z")
        notifier.allowed = false
        assertEquals(ErrorReminderOutcome.NOTIFICATIONS_BLOCKED, reminder.run())
        assertEquals(null, state.day)
        notifier.allowed = true
        assertEquals(ErrorReminderOutcome.SHOWN, reminder.run())
    }
}
