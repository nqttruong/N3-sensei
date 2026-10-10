package com.n3sensei.app.domain.furigana

import com.n3sensei.app.domain.furigana.Furigana.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaTest {

    @Test
    fun parsesRubyAndPlainSegments() {
        assertEquals(
            listOf(Segment("日本", "にほん"), Segment("に"), Segment("来", "き"), Segment("たばかりです。")),
            Furigana.parse("{日本|にほん}に{来|き}たばかりです。"),
        )
    }

    @Test
    fun plainTextIsOneSegment() {
        assertEquals(listOf(Segment("しっかり")), Furigana.parse("しっかり"))
    }

    @Test
    fun emptyMarkupHasNoSegments() {
        assertEquals(emptyList<Segment>(), Furigana.parse(""))
    }

    @Test
    fun baseAndReadingText() {
        val markup = "{増|ふ}える"
        assertEquals("増える", Furigana.baseText(markup))
        assertEquals("ふえる", Furigana.readingText(markup))
    }

    @Test
    fun rejectsMalformedMarkup() {
        listOf("{日本|にほん", "{日本}", "{|にほん}", "{日本|}", "日本}", "a|b", "{a|b|c}", "{{a|b}")
            .forEach { assertFalse("Should reject \"$it\"", Furigana.isValid(it)) }
        assertTrue(Furigana.isValid("{経験|けいけん}"))
    }
}
