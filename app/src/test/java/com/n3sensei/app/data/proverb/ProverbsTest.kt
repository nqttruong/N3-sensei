package com.n3sensei.app.data.proverb

import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.proverb.ProverbOfTheDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The proverb set bundled with the app, and the choice of the proverb of the day. */
class ProverbsTest {

    private val set = AssetProverbRepository.parse(File("src/main/assets/" + AssetProverbRepository.ASSET).readText())

    @Test
    fun bundledSet_isComplete_andEveryEntryHasRubyAndATranslation() {
        assertEquals(32, set.proverbs.size)
        assertEquals(set.proverbs.size, set.proverbs.map { it.id }.toSet().size)
        set.proverbs.forEach { p ->
            assertTrue(p.id, Furigana.parse(p.furigana).any { it.reading != null })
            assertTrue(p.id, p.translationVi.isNotBlank())
        }
        // The stated source is the generic one, not an invented citation.
        assertTrue(set.source.startsWith("Tục ngữ, thành ngữ Nhật Bản phổ biến"))
    }

    @Test
    fun oneProverbPerDay_theSameAllDay_andCaukhacMovesOn() {
        val day = LocalDate.of(2026, 10, 29)
        val today = ProverbOfTheDay.pick(set.proverbs, day)
        assertEquals(today, ProverbOfTheDay.pick(set.proverbs, day))
        assertNotEquals(today, ProverbOfTheDay.pick(set.proverbs, day.plusDays(1)))
        assertEquals(ProverbOfTheDay.pick(set.proverbs, day.plusDays(1)), ProverbOfTheDay.pick(set.proverbs, day, skip = 1))
        // Every proverb comes in turn.
        assertEquals(set.proverbs.toSet(), (0 until set.proverbs.size).map { ProverbOfTheDay.pick(set.proverbs, day, it) }.toSet())
        assertNull(ProverbOfTheDay.pick(emptyList(), day))
    }

    @Test
    fun brokenEntries_areLeftOut() {
        val text = """
            {"format": "n3sensei-proverbs", "formatVersion": 1, "source": "x",
             "proverbs": [{"id": "a", "furigana": "{継続|けいぞく", "translationVi": "x"},
                          {"id": "b", "furigana": "{継続|けいぞく}は{力|ちから}なり", "translationVi": " "},
                          {"id": "c", "furigana": "{継続|けいぞく}は{力|ちから}なり", "translationVi": "Kiên trì là sức mạnh."}]}
        """.trimIndent()
        assertEquals(listOf("c"), AssetProverbRepository.parse(text).proverbs.map { it.id })
    }
}
