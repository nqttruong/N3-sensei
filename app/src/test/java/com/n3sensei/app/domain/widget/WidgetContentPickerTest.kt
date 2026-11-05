package com.n3sensei.app.domain.widget

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeProgressRepository
import com.n3sensei.app.testing.MutableClock
import com.n3sensei.app.testing.TestContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WidgetContentPickerTest {

    private val content = FakeContentRepository().apply {
        lessons.value = listOf(TestContent.lesson("lv", 1), TestContent.lesson("lk", 2), TestContent.lesson("lg", 3))
        vocabulary.value = (1..35).map { TestContent.vocabulary("v$it", "lv", "語$it") }
        kanji.value = (1..26).map { TestContent.kanji("k$it", "lk", "字") }
        grammar.value = (1..8).map { TestContent.grammar("g$it", "lg", "〜$it") }
    }
    private val progress = FakeProgressRepository()
    private val picker = WidgetContentPicker(content, progress, MutableClock(), Random(42))

    private fun WidgetPick.ref() = (this as WidgetPick.Item).item.ref
    private fun v(id: String) = ItemRef(ItemType.VOCABULARY, id)

    @Test
    fun eachMode_showsOnlyItsKind() = runTest {
        assertEquals(ItemType.KANJI, picker.current(WidgetSettings(mode = WidgetMode.KANJI), null).ref().type)
        assertEquals(ItemType.VOCABULARY, picker.current(WidgetSettings(mode = WidgetMode.VOCABULARY), null).ref().type)
        assertEquals(ItemType.GRAMMAR, picker.current(WidgetSettings(mode = WidgetMode.GRAMMAR), null).ref().type)
        assertEquals(35 + 26 + 8, picker.candidates(WidgetSettings(mode = WidgetMode.RANDOM)).size)
    }

    @Test
    fun theShownItemStays_untilAnotherIsAskedFor_whichIsNeverTheSame() = runTest {
        val settings = WidgetSettings(mode = WidgetMode.VOCABULARY)
        assertEquals(v("v7"), picker.current(settings, v("v7")).ref())
        repeat(20) { assertNotEquals(v("v7"), picker.next(settings, v("v7")).ref()) }
    }

    @Test
    fun aChosenLesson_isFollowedInOrder_andNeverLeft() = runTest {
        // Lesson 2 of Học → Bài học (24 kanji, 30 words, 7 grammar points per lesson): the rest of each list.
        val settings = WidgetSettings(mode = WidgetMode.LESSON, lesson = 2)
        val inLesson = picker.candidates(settings)
        assertEquals(listOf("k25", "k26", "v31", "v32", "v33", "v34", "v35", "g8"), inLesson.map { it.id })
        var shown = picker.current(settings, null).ref()
        assertEquals("k25", shown.id)
        val seen = mutableListOf(shown.id)
        repeat(8) { shown = picker.next(settings, shown).ref(); seen += shown.id }
        assertEquals(listOf("k25", "k26", "v31", "v32", "v33", "v34", "v35", "g8", "k25"), seen)
        // An item of another lesson is replaced by one of the chosen lesson.
        assertEquals("k25", picker.current(settings, v("v1")).ref().id)
    }

    @Test
    fun deletedItems_andEmptyModes_neverFail() = runTest {
        val settings = WidgetSettings(mode = WidgetMode.VOCABULARY)
        val before = picker.current(settings, v("v3")).ref()
        content.vocabulary.value = content.vocabulary.value.filter { it.id != "v3" }
        val after = picker.current(settings, before)
        assertTrue(after is WidgetPick.Item && after.ref() != v("v3"))
        assertEquals(WidgetPick.Empty, picker.current(WidgetSettings(mode = WidgetMode.REVIEW), null))
        assertEquals(WidgetPick.Empty, picker.current(WidgetSettings(mode = WidgetMode.LESSON, lesson = 99), null))
        content.vocabulary.value = emptyList()
        assertEquals(WidgetPick.Empty, picker.next(settings, null))
    }

    @Test
    fun reviewMode_showsDueAndMarkedItems() = runTest {
        progress.dueItems = listOf(ItemRef(ItemType.KANJI, "k2"))
        content.marked.value = setOf("v9")
        val refs = picker.candidates(WidgetSettings(mode = WidgetMode.REVIEW))
        assertEquals(listOf(ItemRef(ItemType.KANJI, "k2"), v("v9")), refs)
    }

    @Test
    fun selectionKey_changesWithTheModeAndTheLessonOnly() {
        assertEquals(WidgetSettings().selectionKey(), WidgetSettings(showMeaning = true, tap = WidgetTap.NEXT).selectionKey())
        assertNotEquals(WidgetSettings(mode = WidgetMode.LESSON, lesson = 1).selectionKey(), WidgetSettings(mode = WidgetMode.LESSON, lesson = 2).selectionKey())
    }
}
