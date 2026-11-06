package com.n3sensei.app.domain.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTextTest {

    private fun ready(raw: CharSequence?) = SelectedText.from(raw) as SelectedText.Ready

    @Test
    fun aWordOrSentence_isKeptAsIt_is() {
        assertEquals("experience", ready("experience").text)
        assertEquals("Tôi có kinh nghiệm làm việc ở Nhật.", ready("Tôi có kinh nghiệm làm việc ở Nhật.").text)
        assertEquals("日本で働いた経験があります。", ready("日本で働いた経験があります。").text)
        assertEquals("カタカナとひらがなと漢字", ready("カタカナとひらがなと漢字").text)
        // Punctuation and the spaces inside stay; only the outer whitespace goes.
        assertEquals("well-known, isn't it?  Yes!", ready("  \n\twell-known, isn't it?  Yes!　 ").text)
    }

    @Test
    fun vietnamese_isComposed_NFC_withoutLosingItsMarks() {
        val decomposed = "Việt Nam" // e + circumflex + dot below
        assertEquals("Việt Nam", ready(decomposed).text)
        assertEquals("ệ", ready("ệ").text)
        // Japanese voiced kana given as base + combining mark become one character.
        assertEquals("が", ready("が").text)
    }

    @Test
    fun emojiAndSurrogatePairs_areKeptWhole() {
        assertEquals("いいね👍🏽", ready("いいね👍🏽").text)
        assertEquals("𠮷野家", ready("𠮷野家").text) // 𠮷 is outside the BMP
    }

    @Test
    fun theSourceTextIsNotChanged() {
        val source = StringBuilder("  経験  ")
        ready(source)
        assertEquals("  経験  ", source.toString())
    }

    @Test
    fun missingBlankOrWhitespaceOnly_areNotText() {
        assertEquals(SelectedText.Missing, SelectedText.from(null))
        assertEquals(SelectedText.Blank, SelectedText.from(""))
        assertEquals(SelectedText.Blank, SelectedText.from(" \n\t　 "))
    }

    @Test
    fun theLimitCountsCodePoints_andLongerTextIsRefusedNotCut() {
        val max = SelectedText.MAX_CODE_POINTS
        // 200 characters outside the BMP (400 UTF-16 units) still fit.
        assertEquals(max, ready("𠮷".repeat(max)).text.codePointCount(0, 2 * max))
        assertEquals(SelectedText.TooLong(max + 1, max), SelectedText.from("a".repeat(max + 1)))
        assertEquals(SelectedText.TooLong(max + 1, max), SelectedText.from("𠮷".repeat(max + 1)))
        // A huge selection is refused by counting only.
        assertEquals(SelectedText.TooLong(100_000, max), SelectedText.from("x".repeat(100_000)))
        // Decomposed Vietnamese counts after composing: 200 letters of 3 units each still fit.
        assertTrue(SelectedText.from("ệ".repeat(max)) is SelectedText.Ready)
    }

    @Test
    fun theReadOnlyFlag_isCarried() {
        assertEquals(true, (SelectedText.from("経験", readOnly = true) as SelectedText.Ready).readOnly)
        assertEquals(false, ready("経験").readOnly)
    }
}
