package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.FakeReminderHistory
import com.n3sensei.app.testing.FakeReminderNotifier
import com.n3sensei.app.testing.FakeReminderStateStore
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Reminder content by kind and lesson, the turns between kinds, and the examples shown. */
class ReminderContentSelectionTest {

    private val clock = MutableClock(Instant.parse("2026-10-11T05:00:00Z"))
    private val content = FakeContentRepository().apply {
        lessons.value = listOf(TestContent.lesson("l1", 1), TestContent.lesson("l2", 2))
        // Lesson 1 has 35 words: groups 1.1 (30) and 1.2 (5). Lesson 2 has 3 words.
        vocabulary.value = (1..35).map { TestContent.vocabulary("a$it", "l1", "語$it") } + (1..3).map { TestContent.vocabulary("b$it", "l2", "言$it") }
        kanji.value = listOf(TestContent.kanji("k1", "l1", "経"), TestContent.kanji("k2", "l2", "験"))
        grammar.value = listOf(TestContent.grammar("g1", "l1", "〜ため"), TestContent.grammar("g2", "l2", "〜のに"))
        kanjiWords["k1"] = listOf(KanjiWord("経験", "{経験|けいけん}", "kinh nghiệm"))
        grammarExamples["g1"] = listOf(ExampleSentence("e1", "雨のため、中止です。", null, "Vì mưa nên hoãn."))
    }
    private val progress = FakeProgressRepository()
    private val prefs = FakeUserPreferencesRepository(UserPreferences(reminderSettings = ReminderSettings(enabled = true, startHour = 0, endHour = 0)))
    private val notifier = FakeReminderNotifier()
    private val state = FakeReminderStateStore()
    private val history = FakeReminderHistory()
    private val runner = ReminderRunner(prefs, state, content, progress, notifier, clock, history)

    private fun settings(content: Set<ReminderContent>, lessons: ReminderLessons = ReminderLessons()) =
        ReminderSettings(enabled = true, startHour = 0, endHour = 0, content = content, lessons = lessons)

    private suspend fun shownWith(settings: ReminderSettings, times: Int): List<ItemRef> {
        prefs.setReminderSettings(settings)
        repeat(times) {
            assertEquals(ReminderOutcome.SHOWN, runner.runScheduled())
            clock.advance(Duration.ofHours(1))
        }
        return notifier.shown.map { it.item.ref }
    }

    private suspend fun catalog() = ReminderLessonCatalog.observe(content).first()

    @Test
    fun theLessons_areThoseOfHoc_withSubLessonsKeptApart() = runTest {
        val c = catalog()
        val words = c.optionsOf(ItemType.VOCABULARY)
        assertEquals(listOf("1.1", "1.2", "2"), words.map { it.label })
        assertEquals(listOf(30, 5, 3), words.map { it.itemCount })
        assertEquals(listOf("l1#1", "l1#2", "l2#1"), words.map { it.key })
        assertTrue(c.optionsOf(ItemType.KANJI).isNotEmpty())
        assertTrue(c.optionsOf(ItemType.GRAMMAR).isNotEmpty())
        assertEquals(c.optionsOf(ItemType.KANJI).sumOf { it.itemCount }, 2)
        assertEquals("l1#2", c.lessonOf[ItemRef(ItemType.VOCABULARY, "a31")])
        assertEquals("l1#1", c.lessonOf[ItemRef(ItemType.VOCABULARY, "a30")])
    }

    @Test
    fun onlyOneKind_givesOnlyThatKind() = runTest {
        ReminderContent.entries.forEach { kind ->
            notifier.shown.clear()
            history.shown.clear()
            state.state = ReminderState()
            val shown = shownWith(settings(setOf(kind)), 4)
            assertTrue("$kind: $shown", shown.all { it.type == kind.type })
        }
    }

    @Test
    fun threeKinds_takeTurns_soNoKindTakesEveryReminder() = runTest {
        val shown = shownWith(settings(ReminderContent.entries.toSet()), 6)
        assertEquals(
            listOf(ItemType.VOCABULARY, ItemType.KANJI, ItemType.GRAMMAR, ItemType.VOCABULARY, ItemType.KANJI, ItemType.GRAMMAR),
            shown.map { it.type },
        )
        // Even when every due item is a word.
        notifier.shown.clear()
        history.shown.clear()
        state.state = ReminderState()
        progress.dueItems = (1..20).map { ItemRef(ItemType.VOCABULARY, "a$it") }
        val withDue = shownWith(settings(setOf(ReminderContent.VOCABULARY, ReminderContent.GRAMMAR)), 4)
        assertEquals(2, withDue.count { it.type == ItemType.GRAMMAR })
        assertEquals(2, withDue.count { it.type == ItemType.VOCABULARY })
    }

    @Test
    fun aChosenLesson_limitsTheWords_toThatSubLesson() = runTest {
        val lessons = ReminderLessons(vocabulary = LessonSelection.Only(setOf("l1#2")))
        val shown = shownWith(settings(setOf(ReminderContent.VOCABULARY), lessons), 5)
        assertEquals((31..35).map { "a$it" }.toSet(), shown.map { it.id }.toSet())
    }

    @Test
    fun severalLessons_andKindsTogether_stayWithinTheChoice() = runTest {
        val c = catalog()
        val kanjiOfLesson2 = c.lessonOf.getValue(ItemRef(ItemType.KANJI, "k2"))
        val lessons = ReminderLessons(
            vocabulary = LessonSelection.Only(setOf("l1#2", "l2#1")),
            kanji = LessonSelection.Only(setOf(kanjiOfLesson2)),
        )
        val shown = shownWith(settings(setOf(ReminderContent.VOCABULARY, ReminderContent.KANJI), lessons), 6)
        assertTrue(shown.filter { it.type == ItemType.VOCABULARY }.all { it.id in (31..35).map { n -> "a$n" } + listOf("b1", "b2", "b3") })
        assertTrue(shown.filter { it.type == ItemType.KANJI }.all { c.lessonOf[it] == kanjiOfLesson2 })
    }

    @Test
    fun noLessonOfAKind_leavesOnlyTheOthers_andNothingMatchingShowsNothing() = runTest {
        val noWords = ReminderLessons(vocabulary = LessonSelection.Only(emptySet()))
        val shown = shownWith(settings(setOf(ReminderContent.VOCABULARY, ReminderContent.GRAMMAR), noWords), 3)
        assertTrue(shown.all { it.type == ItemType.GRAMMAR })

        prefs.setReminderSettings(settings(setOf(ReminderContent.VOCABULARY), noWords))
        assertEquals(ReminderOutcome.NOTHING_TO_SHOW, runner.runScheduled())
        // A lesson removed from the content matches nothing either.
        prefs.setReminderSettings(settings(setOf(ReminderContent.VOCABULARY), ReminderLessons(vocabulary = LessonSelection.Only(setOf("gone#1")))))
        assertEquals(ReminderOutcome.NOTHING_TO_SHOW, runner.runScheduled())
    }

    @Test
    fun aKanjiComesWithAWord_andAGrammarPointWithAnExample() = runTest {
        val kanji = runner.nextMessage(settings(setOf(ReminderContent.KANJI)))!!
        assertEquals("k1", kanji.item.ref.id)
        assertEquals(ReminderExample("経験", "けいけん", "kinh nghiệm"), kanji.example)
        val grammar = runner.nextMessage(settings(setOf(ReminderContent.GRAMMAR)))!!
        assertEquals("g1", grammar.item.ref.id)
        assertEquals(ReminderExample("雨のため、中止です。", null, "Vì mưa nên hoãn."), grammar.example)
        assertNull(runner.nextMessage(settings(setOf(ReminderContent.GRAMMAR)), avoid = ItemRef(ItemType.GRAMMAR, "g1"))!!.example)
    }

    @Test
    fun toggling_movesBetweenAllAndOnly_andDropsLessonsGoneFromTheContent() = runTest {
        val c = catalog()
        val minusOne = c.toggle(ItemType.VOCABULARY, LessonSelection.All, "l1#1")
        assertEquals(LessonSelection.Only(setOf("l1#2", "l2#1")), minusOne)
        assertEquals(LessonSelection.All, c.toggle(ItemType.VOCABULARY, minusOne, "l1#1"))
        assertEquals(LessonSelection.Only(setOf("l2#1")), c.current(ItemType.VOCABULARY, LessonSelection.Only(setOf("l2#1", "gone#1"))))
        assertTrue(LessonSelection.All.allows(null))
        assertTrue(!LessonSelection.Only(setOf("x")).allows(null))
    }
}
