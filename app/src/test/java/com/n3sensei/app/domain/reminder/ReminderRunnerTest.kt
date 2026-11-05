package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeReminderHistory
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
    private val history = FakeReminderHistory()
    private val runner = ReminderRunner(prefs, state, content, progress, notifier, clock, history)

    private fun v(id: String) = ItemRef(ItemType.VOCABULARY, id)

    @Test
    fun picksOldestDueVocabulary_ignoringOtherTypes() = runTest {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k1"), v("v3"), v("v1"))
        progress.newItems = listOf(v("v2"))
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        val message = notifier.shown.single()
        assertEquals("v3", message.item.ref.id)
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
        assertEquals("v1", next.item.ref.id)
        assertEquals(ReminderReason.NEW, next.reason)
    }

    @Test
    fun avoidsRepeatingTheLastWord_whenAnotherIsAvailable() = runTest {
        progress.dueItems = listOf(v("v1"), v("v2"))
        state.state = ReminderState(lastShownAt = null, lastItem = v("v1"))
        runner.runScheduled()
        assertEquals("v2", notifier.shown.single().item.ref.id)
        // With only one candidate it is shown again rather than nothing.
        progress.dueItems = listOf(v("v2"))
        clock.advance(Duration.ofHours(3))
        runner.runScheduled()
        assertEquals("v2", notifier.shown.last().item.ref.id)
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
        assertEquals("v1", notifier.shown.single().item.ref.id)
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

    private suspend fun showEvery(minutes: Long, times: Int): List<String> = List(times) {
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        clock.advance(Duration.ofMinutes(minutes))
        notifier.shown.last().item.ref.id
    }

    @Test
    fun theSameFewWordsDoNotComeBackInTurn_everyCandidateIsShownFirst() = runTest {
        // Before: A, B, A, B… (only the last word was avoided). Now every word comes once first.
        prefs.preferences.value = prefs.preferences.value.copy(
            reminderSettings = ReminderSettings(enabled = true, startHour = 0, endHour = 0, frequency = ReminderFrequency.EVERY_15_MINUTES, maxPerDay = 0),
        )
        progress.dueItems = listOf(v("v1"), v("v2"), v("v3"))
        assertEquals(listOf("v1", "v2", "v3"), showEvery(15, 3))
        // All shown within 24 hours: the one shown longest ago, never the last one again.
        assertEquals(listOf("v1", "v2", "v3"), showEvery(15, 3))
        assertEquals(3, history.shown.size)
    }

    @Test
    fun aShownItemRests24Hours_thenMayComeBack() = runTest {
        progress.dueItems = listOf(v("v1"))
        progress.newItems = listOf(v("v2"))
        runner.showNow()
        assertEquals("v1", notifier.shown.last().item.ref.id)
        // v1 is still the most urgent, but was just shown: the new word goes first.
        clock.advance(Duration.ofHours(2))
        runner.showNow()
        assertEquals("v2", notifier.shown.last().item.ref.id)
        clock.advance(Duration.ofHours(23))
        runner.showNow()
        assertEquals("v1", notifier.shown.last().item.ref.id)
    }

    @Test
    fun dailyLimit_stopsScheduledRuns_untilTheNextDay() = runTest {
        prefs.preferences.value = prefs.preferences.value.copy(
            reminderSettings = ReminderSettings(enabled = true, startHour = 0, endHour = 0, frequency = ReminderFrequency.EVERY_15_MINUTES, maxPerDay = 2),
        )
        progress.newItems = listOf(v("v1"), v("v2"), v("v3"))
        clock.now = Instant.parse("2026-10-11T00:10:00Z")
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        clock.advance(Duration.ofMinutes(15))
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        clock.advance(Duration.ofMinutes(15))
        assertEquals(ReminderOutcome.DAILY_LIMIT, runner.runScheduled())
        assertEquals(2, state.state.countToday)
        clock.now = Instant.parse("2026-10-12T00:05:00Z")
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        assertEquals(1, state.state.countToday)
    }

    @Test
    fun frequencyOf15Minutes_allowsARunAfter12Minutes_notAfter10() = runTest {
        prefs.preferences.value = prefs.preferences.value.copy(
            reminderSettings = ReminderSettings(enabled = true, startHour = 0, endHour = 0, frequency = ReminderFrequency.EVERY_15_MINUTES),
        )
        progress.newItems = listOf(v("v1"), v("v2"))
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
        clock.advance(Duration.ofMinutes(10))
        assertEquals(ReminderOutcome.TOO_SOON, runner.runScheduled())
        clock.advance(Duration.ofMinutes(2))
        assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
    }

    @Test
    fun contentKinds_andReviewOnly_limitWhatIsShown() = runTest {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k1"))
        progress.newItems = listOf(v("v1"))
        val kanjiOnly = ReminderSettings(content = setOf(ReminderContent.KANJI))
        assertEquals(ItemRef(ItemType.KANJI, "k1"), runner.nextMessage(kanjiOnly)!!.item.ref)
        // Words only, review only: the kanji is not a word and v1 is new, so nothing.
        assertNull(runner.nextMessage(ReminderSettings(reviewOnly = true)))
        assertEquals("v1", runner.nextMessage(ReminderSettings())!!.item.ref.id)
    }
}
