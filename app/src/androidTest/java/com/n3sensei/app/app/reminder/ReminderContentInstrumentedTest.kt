package com.n3sensei.app.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.reminder.ReminderContent
import com.n3sensei.app.domain.reminder.ReminderLessonCatalog
import com.n3sensei.app.domain.reminder.ReminderLessons
import com.n3sensei.app.domain.reminder.ReminderOutcome
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.reminder.ReminderState
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reminder content by kind and lesson with the real container: Room (SEED TEST DATA content),
 * DataStore and Android's notification manager.
 */
@RunWith(AndroidJUnit4::class)
class ReminderContentInstrumentedTest {

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

    private fun show(settings: ReminderSettings): Pair<ReminderOutcome, Notification?> = runBlocking {
        container.userPreferencesRepository.setReminderSettings(settings)
        container.reminderStateStore.write(ReminderState())
        val outcome = container.reminderRunner.showNow()
        outcome to notificationManager.activeNotifications.firstOrNull { it.id == AndroidReminderNotifier.REMINDER_NOTIFICATION_ID }?.notification
    }

    private fun only(kind: ReminderContent, lessons: ReminderLessons = ReminderLessons()) =
        ReminderSettings(enabled = true, startHour = 0, endHour = 0, showMeaning = true, content = setOf(kind), lessons = lessons)

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)!!
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun aKanjiReminder_showsTheKanjiLarge_withReadingsAndMeaning() {
        val (outcome, n) = show(only(ReminderContent.KANJI))
        assertEquals(ReminderOutcome.SHOWN, outcome)
        val title = n!!.title()
        assertEquals("経 (ケイ、キョウ、へ.る)", title.toString())
        assertTrue(n.text(), n.text().startsWith("KINH · trải qua, kinh qua"))
        // The large kanji layout (the plain title and text above stay for TalkBack).
        assertEquals("android.app.Notification\$DecoratedCustomViewStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(com.n3sensei.app.R.layout.notification_kanji, n.contentView!!.layoutId)
        assertEquals(com.n3sensei.app.R.layout.notification_kanji, n.bigContentView!!.layoutId)
    }

    @Test
    fun aGrammarReminder_showsThePatternMeaningAndAnExample() {
        val (outcome, n) = show(only(ReminderContent.GRAMMAR))
        assertEquals(ReminderOutcome.SHOWN, outcome)
        assertEquals("〜たばかり", n!!.title().toString())
        assertEquals("vừa mới (làm gì)\nVí dụ: 日本に来たばかりです。: Tôi vừa mới đến Nhật.", n.text())
        // Self-test: meanings hidden, the Japanese example still there.
        val (_, hidden) = show(only(ReminderContent.GRAMMAR).copy(showMeaning = false))
        assertTrue(hidden!!.text(), hidden.text().endsWith("Ví dụ: 日本に来たばかりです。"))
        assertTrue(!hidden.text().contains("vừa mới"))
    }

    @Test
    fun allThreeKinds_takeTurns() {
        runBlocking {
            container.userPreferencesRepository.setReminderSettings(
                ReminderSettings(enabled = true, startHour = 0, endHour = 0, content = ReminderContent.entries.toSet()),
            )
            container.reminderStateStore.write(ReminderState())
        }
        val titles = (1..3).map {
            runBlocking { container.reminderRunner.showNow() }
            notificationManager.activeNotifications.first { it.id == AndroidReminderNotifier.REMINDER_NOTIFICATION_ID }.notification.title().toString()
        }
        assertEquals("経験 (けいけん)", titles[0])
        assertTrue(titles[1], titles[1].startsWith("経"))
        assertEquals("〜たばかり", titles[2])
    }

    @Test
    fun theLessonChoice_isSavedInDataStore_andAppliedToTheReminder() = runBlocking {
        val catalog = ReminderLessonCatalog.observe(container.contentRepository).first()
        val wordLesson = catalog.optionsOf(com.n3sensei.app.domain.model.ItemType.VOCABULARY).first().key
        val chosen = ReminderLessons(vocabulary = LessonSelection.Only(setOf(wordLesson)), kanji = LessonSelection.Only(emptySet()))
        val (outcome, n) = show(only(ReminderContent.VOCABULARY, chosen))
        assertEquals(ReminderOutcome.SHOWN, outcome)
        assertEquals("経験 (けいけん)", n!!.title().toString())
        assertEquals(chosen, container.userPreferencesRepository.preferences.first().reminderSettings.lessons)

        // No lesson chosen for the only kind: nothing to show, and the choice is kept as "none".
        val none = ReminderLessons(vocabulary = LessonSelection.Only(emptySet()))
        assertEquals(ReminderOutcome.NOTHING_TO_SHOW, show(only(ReminderContent.VOCABULARY, none)).first)
        assertEquals(none, container.userPreferencesRepository.preferences.first().reminderSettings.lessons)
        // History is kept whatever the choice (one row per kind and id shown).
        assertTrue(container.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM reminder_history").use { it.moveToFirst(); it.getInt(0) } >= 1)
    }
}
