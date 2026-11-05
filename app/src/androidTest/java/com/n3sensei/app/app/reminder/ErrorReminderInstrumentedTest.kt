package com.n3sensei.app.app.reminder

import android.Manifest
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.reminder.ErrorReminderOutcome
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.ExamTestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The error review reminder with the app's real container and Android's notification manager:
 * one notification when errors are due, never a second one, removed when nothing is due or the
 * reminder is turned off. Exam data is TEST DATA ONLY.
 */
@RunWith(AndroidJUnit4::class)
class ErrorReminderInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val container get() = AppTestState.appContainer
    private val day = 24 * 60 * 60_000L

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        AppTestState.reset(seedContent = false)
        notificationManager.cancelAll()
        runBlocking {
            container.reminderStateStore.setLastShownDay(LocalDate.of(2000, 1, 1))
            container.userPreferencesRepository.setReminderSettings(ReminderSettings(errorReviews = true, startHour = 0, endHour = 0))
            val exams = container.examRepository
            exams.saveExamSet(ExamTestData.set())
            val a = exams.startAttempt(ExamTestData.SET_ID)!!
            exams.answer(a.id, ExamTestData.Q_KANJI, AnswerInput.Choice(1), 1_000)
            exams.submit(a.id)
        }
    }

    private fun setDue(at: Long) = container.database.openHelper.writableDatabase.execSQL("UPDATE review_items SET next_review_at = $at")

    private fun shown() = notificationManager.activeNotifications.count { it.id == AndroidReminderNotifier.ERROR_REVIEW_NOTIFICATION_ID }

    /** The system removes a notification asynchronously: wait (a few seconds at most) for it to go. */
    private fun awaitGone() {
        val until = System.currentTimeMillis() + 5_000
        while (shown() > 0 && System.currentTimeMillis() < until) Thread.sleep(50)
        assertEquals(0, shown())
    }

    @Test
    fun errorsJustMade_areNotDueYet_soNothingIsShown() = runBlocking {
        assertEquals(ErrorReminderOutcome.NOTHING_DUE, container.errorReviewReminder.run())
        assertEquals(0, shown())
    }

    @Test
    fun dueErrors_showOneNotification_neverTwo_andItGoesWhenNothingIsDue() = runBlocking {
        setDue(System.currentTimeMillis() - day)
        assertEquals(ErrorReminderOutcome.SHOWN, container.errorReviewReminder.run())
        assertEquals(1, shown())
        repeat(3) { assertEquals(ErrorReminderOutcome.ALREADY_TODAY, container.errorReviewReminder.run()) }
        assertEquals(1, shown())

        setDue(System.currentTimeMillis() + 30 * day)
        assertEquals(ErrorReminderOutcome.NOTHING_DUE, container.errorReviewReminder.run())
        awaitGone()
    }

    @Test
    fun turningTheReminderOff_removesItsNotification() = runBlocking {
        setDue(System.currentTimeMillis() - day)
        container.errorReviewReminder.run()
        assertEquals(1, shown())
        container.userPreferencesRepository.setReminderSettings(ReminderSettings(errorReviews = false))
        assertEquals(ErrorReminderOutcome.DISABLED, container.errorReviewReminder.run())
        awaitGone()
    }
}
