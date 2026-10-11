package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeReminderNotifier
import com.n3sensei.app.testing.FakeReminderStateStore
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderRunnerTest {

    private val clock = MutableClock(Instant.parse("2026-10-11T05:00:00Z")) // 05:00 UTC, inside 0–23 window
    private val content = FakeContentRepository().apply {
        vocabulary.value = listOf(
            TestContent.vocabulary("v1", "l1", "経験", "けいけん", "kinh nghiệm"),
            TestContent.vocabulary("v2", "l1", "給料", "きゅうりょう", "tiền lương"),
            TestContent.vocabulary("v3", "l1", "残業", "ざんぎょう", "làm thêm giờ"),
        )
        kanji.value = listOf(TestContent.kanji("k1", "l1", "経"))
    }
    private val progress = FakeProgressRepository()
    private val prefs = FakeUserPreferencesRepository(
        UserPreferences(onboardingCompleted = true, reminderSettings = ReminderSettings(enabled = true, startHour = 0, endHour = 0)),
    )
    private val notifier = FakeReminderNotifier()
    private val state = FakeReminderStateStore()
    private val runner = ReminderRunner(prefs, state, content, progress, notifier, clock)

    private fun v(id: String) = ItemRef(ItemType.VOCABULARY, id)

    @Test
    fun picksOldestDueVocabulary_ignoringOtherTypes() = runTest {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k1"), v("v3"), v("v1"))
        progress.newItems = listOf(v("v2"))
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        val message = notifier.shown.single()
        assertEquals("v3", message.vocabulary.id)
        assertEquals(ReminderReason.DUE, message.reason)
        assertEquals(3, message.dueCount)
    }

    @Test
    fun withoutDue_usesMarked_thenNew() = runTest {
        content.marked.value = setOf("v2")
        progress.newItems = listOf(v("v1"))
        assertEquals(ReminderReason.MARKED, runner.nextMessage(ReminderSettings())!!.reason)
        content.marked.value = emptySet()
        val next = runner.nextMessage(ReminderSettings())!!
        assertEquals("v1", next.vocabulary.id)
        assertEquals(ReminderReason.NEW, next.reason)
    }

    @Test
    fun avoidsRepeatingTheLastWord_whenAnotherIsAvailable() = runTest {
        progress.dueItems = listOf(v("v1"), v("v2"))
        state.state = ReminderState(lastShownAt = null, lastVocabularyId = "v1")
        runner.runScheduled()
        assertEquals("v2", notifier.shown.single().vocabulary.id)
        // With only one candidate it is shown again rather than nothing.
        progress.dueItems = listOf(v("v2"))
        clock.advance(Duration.ofHours(3))
        runner.runScheduled()
        assertEquals("v2", notifier.shown.last().vocabulary.id)
    }

    @Test
    fun emptyDatabase_showsNothing_andKeepsState() = runTest {
        content.vocabulary.value = emptyList()
        assertEquals(ReminderOutcome.NOTHING_TO_SHOW, runner.runScheduled())
        assertTrue(notifier.shown.isEmpty())
        assertNull(state.state.lastShownAt)
    }

    @Test
    fun deletedContent_isSkipped() = runTest {
        progress.dueItems = listOf(v("gone"), v("v1"))
        runner.runScheduled()
        assertEquals("v1", notifier.shown.single().vocabulary.id)
    }

    @Test
    fun blockedNotifications_showNothing() = runTest {
        progress.newItems = listOf(v("v1"))
        notifier.allowed = false
        assertEquals(ReminderOutcome.NOTIFICATIONS_BLOCKED, runner.runScheduled())
        assertEquals(ReminderOutcome.NOTIFICATIONS_BLOCKED, runner.showNow())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun scheduledRun_respectsSettingsAndSpacing_testRunDoesNot() = runTest {
        progress.newItems = listOf(v("v1"), v("v2"))
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        assertEquals(clock.now, state.state.lastShownAt)
        assertEquals(ReminderOutcome.TOO_SOON, runner.runScheduled())
        assertEquals(ReminderOutcome.SHOWN, runner.showNow()) // "Gửi thử" ignores spacing

        prefs.preferences.value = prefs.preferences.value.copy(reminderSettings = ReminderSettings(enabled = false))
        clock.advance(Duration.ofHours(10))
        assertEquals(ReminderOutcome.DISABLED, runner.runScheduled())

        // 15:00 in the clock's zone (UTC): outside a 20:00–23:00 window.
        prefs.preferences.value = prefs.preferences.value.copy(reminderSettings = ReminderSettings(enabled = true, startHour = 20, endHour = 23))
        assertEquals(ReminderOutcome.OUTSIDE_WINDOW, runner.runScheduled())
    }

    @Test
    fun remindersNeverChangeTheReviewSchedule() = runTest {
        progress.dueItems = listOf(v("v1"))
        repeat(3) {
            runner.showNow()
            runner.runScheduled()
            clock.advance(Duration.ofHours(4))
        }
        assertEquals(0, progress.submitCalls)
        assertTrue(progress.records.isEmpty())
        assertTrue(progress.progress.isEmpty())
    }
}
