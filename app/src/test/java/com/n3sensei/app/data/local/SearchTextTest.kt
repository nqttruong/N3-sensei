package com.n3sensei.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    private fun matches(stored: String, query: String) =
        SearchText.normalize(query) in SearchText.of(stored)

    @Test
    fun vietnamese_ignoresCaseAndDiacritics() {
        assertEquals("kinh nghiem", SearchText.normalize("Kinh Nghiệm"))
        assertEquals("tang len, gia tang", SearchText.normalize("TĂNG LÊN, GIA TĂNG"))
        assertEquals("di duong", SearchText.normalize("Đi đường"))
        assertTrue(matches("kinh nghiệm", "kinh nghiem"))
        assertTrue(matches("tăng lên", "TĂNG"))
    }

    @Test
    fun katakanaMatchesHiragana() {
        assertEquals("けい", SearchText.normalize("ケイ"))
        assertTrue(matches("ケイ キョウ", "きょう"))
    }

    @Test
    fun japaneseVoicingMarksAreKept() {
        assertEquals("が", SearchText.normalize("が"))
        assertEquals("ぎょう", SearchText.normalize("ギョウ"))
        assertFalse(matches("ざんぎょう", "さんきよう"))
    }

    @Test
    fun kanjiAndPunctuationAreUnchanged() {
        assertEquals("経験", SearchText.normalize("経験"))
        assertEquals("ふ.える", SearchText.normalize("ふ.える"))
        assertEquals("〜たばかり", SearchText.normalize("〜たばかり"))
    }

    @Test
    fun ofJoinsNonBlankFieldsOnSeparateLines() {
        assertEquals("経験\nけいけん\nkinh nghiem", SearchText.of("経験", "けいけん", null, " ", "kinh nghiệm"))
    }
}
