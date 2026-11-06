package com.n3sensei.app.domain.translate

import com.n3sensei.app.domain.translate.Language.ENGLISH
import com.n3sensei.app.domain.translate.Language.JAPANESE
import com.n3sensei.app.domain.translate.Language.VIETNAMESE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkEntryTest {

    private fun <T> available(vararg v: T, provider: Provider = Provider.MACHINE_TRANSLATION) = FieldState.Available(v.map { Sourced(it, provider) })

    private fun result(
        text: String,
        source: Language,
        vi: FieldState<String> = FieldState.NotRequested,
        en: FieldState<String> = FieldState.NotRequested,
        ja: FieldState<JapaneseText> = FieldState.NotRequested,
        reading: FieldState<String> = FieldState.Unavailable,
        examples: FieldState<TranslationExample> = FieldState.Unavailable,
    ) = TranslationResult(text, source, vi, en, FieldState.Unavailable, ja, reading, FieldState.Unavailable, examples, FieldState.Unavailable)

    @Test
    fun vietnamese_withEnglishAndJapanese_isOneEntryHoldingBoth() {
        val e = BookmarkEntry.from(result("Kinh nghiệm", VIETNAMESE, en = available("experience"), ja = available(JapaneseText("経験", "けいけん"), provider = Provider.CONTENT_DICTIONARY)))!!
        assertEquals("経験", e.headword)
        assertEquals("Kinh nghiệm", e.meaningVi)
        assertEquals(listOf(BookmarkTranslation(ENGLISH, "experience", null), BookmarkTranslation(JAPANESE, "経験", "けいけん")), e.translations)
        assertEquals("kinh nghiệm", e.key)
    }

    @Test
    fun english_hasNoKanaMadeUp_andItsMeaning() {
        val e = BookmarkEntry.from(result("Bank", ENGLISH, vi = available("ngân hàng")))!!
        assertEquals("Bank", e.headword)
        assertNull(e.japanese)
        assertEquals("ngân hàng", e.meaningVi)
        assertEquals(emptyList<BookmarkTranslation>(), e.translations)
        // An English "bank" and a Vietnamese "bank" are two languages, two entries.
        assertEquals("bank", e.key)
    }

    @Test
    fun english_listedUnderItsJapaneseWord_keepsTheEnglishWordItself() {
        // Found in the Chrome check: "experience" was saved as 経験 and the English word was
        // nowhere to be seen or searched.
        val e = BookmarkEntry.from(result("experience", ENGLISH, vi = available("kinh nghiệm"), ja = available(JapaneseText("経験", "けいけん"))))!!
        assertEquals("経験", e.headword)
        assertEquals(listOf(BookmarkTranslation(ENGLISH, "experience", null), BookmarkTranslation(JAPANESE, "経験", "けいけん")), e.translations)
    }

    @Test
    fun japanese_keepsItsKnownReading_andHomographsWithAnotherReadingStayApart() {
        val nama = BookmarkEntry.from(result("生", JAPANESE, vi = available("sống, tươi"), reading = available("なま")))!!
        val sei = BookmarkEntry.from(result("生", JAPANESE, vi = available("sự sống"), reading = available("せい")))!!
        assertEquals(JapaneseText("生", "なま"), nama.japanese)
        assertNotEquals(nama.key, sei.key)
        // Several readings known together: one entry, no reading picked at random.
        val both = BookmarkEntry.from(result("生", JAPANESE, vi = available("sống"), reading = available("なま", "せい")))!!
        assertNull(both.japanese!!.reading)
        assertEquals("生", both.key)
    }

    @Test
    fun severalSenses_keptShort_andTheFirstExample() {
        val ex = TranslationExample("日本で働いた経験があります。", null, "Tôi có kinh nghiệm làm việc ở Nhật.")
        val e = BookmarkEntry.from(result("しっかり", JAPANESE, vi = available("a", "b", "c", "d"), examples = available(ex, provider = Provider.CONTENT_DICTIONARY)))!!
        assertEquals("a; b; c", e.meaningVi)
        assertEquals(ex, e.example)
    }

    @Test
    fun nothingToSave_noEntry() {
        assertNull(BookmarkEntry.from(result("xin chào", VIETNAMESE, en = FieldState.NeedsModel(setOf(ENGLISH)))))
        assertNull(BookmarkEntry.from(result("hello", ENGLISH, vi = FieldState.Failed(TranslationFailure.OFFLINE))))
    }
}
