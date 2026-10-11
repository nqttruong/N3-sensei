package com.n3sensei.app.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderOutcome
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Real notifications, WorkManager and deep links on the device/emulator, with SEED data.
 * The notification permission is granted here; revoking it at runtime kills the test process,
 * so the "blocked" path is covered by unit tests and a manual check instead.
 */
@RunWith(AndroidJUnit4::class)
class ReminderInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val container get() = AppTestState.appContainer

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        AppTestState.reset(seedContent = true)
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        AppTestState.reset(seedContent = false)
    }

    private fun setReminder(settings: ReminderSettings) = runBlocking {
        container.userPreferencesRepository.setReminderSettings(settings)
        container.reminderStateStore.write(com.n3sensei.app.domain.reminder.ReminderState())
    }

    private fun runWorker(): ReminderOutcome {
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context.applicationContext).build()
        val result = runBlocking { worker.doWork() }
        assertTrue(result is ListenableWorker.Result.Success)
        return ReminderOutcome.valueOf(result.outputData.getString(ReminderWorker.KEY_OUTCOME)!!)
    }

    private fun postedReminder(): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == AndroidReminderNotifier.REMINDER_NOTIFICATION_ID }?.notification

    private val allDay = ReminderSettings(enabled = true, startHour = 0, endHour = 0)

    @Test
    fun worker_postsFirstNewWord_fromRoom_withActions() {
        setReminder(allDay.copy(lockScreen = LockScreenPrivacy.SHOW_ALL))
        assertEquals(ReminderOutcome.SHOWN, runWorker())

        val n = assertNotNull(postedReminder()).let { postedReminder()!! }
        assertEquals("経験 (けいけん)", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("kinh nghiệm", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(AndroidReminderNotifier.CHANNEL_ID, n.channelId)
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertEquals(
            listOf(context.getString(R.string.reminder_action_open), context.getString(R.string.reminder_action_mark)),
            n.actions.map { it.title.toString() },
        )
        assertNotNull(n.contentIntent)
    }

    @Test
    fun privacyModes_setVisibilityAndPublicVersion_andHideMeaningEverywhereWhenAsked() {
        setReminder(allDay.copy(lockScreen = LockScreenPrivacy.HIDE_MEANING))
        runWorker()
        val private = postedReminder()!!
        assertEquals(Notification.VISIBILITY_PRIVATE, private.visibility)
        val publicText = private.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertFalse(publicText.contains("kinh nghiệm"))
        assertEquals(context.getString(R.string.reminder_tap_for_meaning), publicText)

        setReminder(allDay.copy(lockScreen = LockScreenPrivacy.HIDE_ALL))
        runWorker()
        assertEquals(Notification.VISIBILITY_SECRET, postedReminder()!!.visibility)

        setReminder(allDay.copy(showMeaning = false, showReading = false))
        runWorker()
        val noMeaning = postedReminder()!!
        assertEquals("経験", noMeaning.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertFalse(noMeaning.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("kinh nghiệm"))
        assertFalse(noMeaning.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("kinh nghiệm"))
    }

    @Test
    fun worker_disabledOrEmptyDatabase_postsNothing() {
        setReminder(allDay.copy(enabled = false))
        assertEquals(ReminderOutcome.DISABLED, runWorker())
        assertNull(postedReminder())

        AppTestState.reset(seedContent = false)
        setReminder(allDay)
        assertEquals(ReminderOutcome.NOTHING_TO_SHOW, runWorker())
        assertNull(postedReminder())
    }

    @Test
    fun worker_twiceInARow_isSpacedAndReplacesInsteadOfStacking() {
        setReminder(allDay)
        assertEquals(ReminderOutcome.SHOWN, runWorker())
        assertEquals(ReminderOutcome.TOO_SOON, runWorker())
        runBlocking { container.reminderRunner.showNow() }
        assertEquals(1, notificationManager.activeNotifications.count { it.id == AndroidReminderNotifier.REMINDER_NOTIFICATION_ID })
    }

    @Test
    fun scheduler_enqueuesUpdatesAndCancelsPeriodicWork() {
        val workManager = WorkManager.getInstance(context)
        fun info() = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get().singleOrNull()

        container.reminderScheduler.apply(allDay.copy(frequency = ReminderFrequency.EVERY_3_HOURS))
        assertEquals(WorkInfo.State.ENQUEUED, info()!!.state)
        assertEquals(TimeUnit.HOURS.toMillis(3), info()!!.periodicityInfo!!.repeatIntervalMillis)

        container.reminderScheduler.apply(allDay.copy(frequency = ReminderFrequency.ONCE_A_DAY))
        assertEquals(TimeUnit.HOURS.toMillis(24), info()!!.periodicityInfo!!.repeatIntervalMillis)

        container.reminderScheduler.apply(allDay.copy(enabled = false))
        assertEquals(WorkInfo.State.CANCELLED, info()!!.state)
    }

    @Test
    fun deepLink_opensTheWordCard_andBackGoesToLearn() {
        ActivityScenario.launch<MainActivity>(ReminderDeepLinks.openVocabularyIntent(context, SeedData.V_KEIKEN)).use {
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("kinh nghiệm")).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNode(hasText(context.getString(R.string.vocabulary_detail_title))).assertIsDisplayed()
            Espresso.pressBack()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText(context.getString(R.string.learn_lessons_description))).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun markAction_marksTheWord_withoutTouchingTheSchedule() {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_MARK)
            .putExtra(ReminderActionReceiver.EXTRA_VOCABULARY_ID, SeedData.V_FUERU)
        context.sendBroadcast(intent)

        val ref = ItemRef(ItemType.VOCABULARY, SeedData.V_FUERU)
        val deadline = System.currentTimeMillis() + 5_000
        while (!runBlocking { container.progressRepository.observeItemState(ref).first() }.isMarked) {
            check(System.currentTimeMillis() < deadline) { "Word was not marked" }
            Thread.sleep(100)
        }
        assertEquals(0, container.database.countRows("review_records"))
        assertEquals(0, container.database.countRows("learning_progress"))
    }

    /**
     * The scheduled path end to end: the periodic request built by our scheduler runs through
     * WorkManager (test driver marks the period as elapsed) and posts the reminder.
     */
    @Test
    fun scheduledPeriodicWork_runsThroughWorkManager_andPostsTheReminder() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        setReminder(allDay)
        container.reminderScheduler.apply(allDay)
        val workManager = WorkManager.getInstance(context)
        val id = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get().single().id

        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        driver.setAllConstraintsMet(id)
        driver.setPeriodDelayMet(id)

        val deadline = System.currentTimeMillis() + 5_000
        while (postedReminder() == null) {
            check(System.currentTimeMillis() < deadline) { "Periodic work did not post a reminder" }
            Thread.sleep(100)
        }
        assertEquals("経験 (けいけん)", postedReminder()!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        // Periodic work stays scheduled for the next period.
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(id).get()!!.state)
        container.reminderScheduler.apply(allDay.copy(enabled = false))
    }
}
