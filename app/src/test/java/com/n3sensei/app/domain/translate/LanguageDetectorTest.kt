package com.n3sensei.app.domain.translate

import com.n3sensei.app.domain.translate.Language.ENGLISH
import com.n3sensei.app.domain.translate.Language.JAPANESE
import com.n3sensei.app.domain.translate.Language.VIETNAMESE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDetectorTest {

    private val detector: LanguageDetector = ScriptLanguageDetector()
    private fun detect(text: String) = detector.detect(text)

    private fun assertSure(language: Language, text: String) = with(detect(text)) {
        assertEquals(text, language, this.language)
        assertFalse(text, isAmbiguous)
        assertNull("rule-based: no made-up confidence", confidence)
    }

    private fun assertUnsure(language: Language?, text: String) = with(detect(text)) {
        assertEquals(text, language, this.language)
        assertTrue(text, isAmbiguous)
    }

    @Test
    fun vietnameseWithItsOwnLetters() {
        assertSure(VIETNAMESE, "kinh nghiệm")
        assertSure(VIETNAMESE, "Tôi đã học tiếng Nhật được hai năm.")
        assertSure(VIETNAMESE, "đường")
        assertSure(VIETNAMESE, "ưu tiên")
        assertSure(VIETNAMESE, "KINH NGHIỆM")
    }

    @Test
    fun accentsSharedWithOtherLanguages_areOnlyAGuess() {
        // à and ê exist in French too: Vietnamese, but to be checked.
        assertUnsure(VIETNAMESE, "cà phê")
        assertEquals(listOf(VIETNAMESE, ENGLISH), detect("café").candidates)
        assertUnsure(VIETNAMESE, "café")
    }

    @Test
    fun vietnameseWithoutAccents_isNeverCalledEnglishForSure() {
        assertUnsure(VIETNAMESE, "kinh nghiem")
        assertUnsure(VIETNAMESE, "nguoi Viet Nam")
        assertEquals(listOf(VIETNAMESE, ENGLISH), detect("kinh nghiem").candidates)
        // Words that fit both: English first, still to be checked.
        assertUnsure(ENGLISH, "to be")
        assertUnsure(ENGLISH, "an")
    }

    @Test
    fun english() {
        assertSure(ENGLISH, "experience")
        assertSure(ENGLISH, "I have a cat.")
        assertSure(ENGLISH, "well-known, isn't it?")
        assertSure(ENGLISH, "strength")
    }

    @Test
    fun hiraganaKatakanaAndKanjiWithKana() {
        assertSure(JAPANESE, "ありがとう")
        assertSure(JAPANESE, "コンピューター")
        assertSure(JAPANESE, "日本で働いた経験があります。")
        assertSure(JAPANESE, "ｶﾀｶﾅ") // half-width
    }

    @Test
    fun kanjiAlone_isJapaneseOnlyAsAGuess() {
        assertUnsure(JAPANESE, "経験")
        assertUnsure(JAPANESE, "日本語")
        // 々 is written in Japanese.
        assertSure(JAPANESE, "時々")
        // Characters only simplified Chinese uses: not a supported language.
        assertUnsure(null, "我们说")
        assertTrue(detect("我们说").candidates.isEmpty())
    }

    @Test
    fun mixedScripts_takeTheLargerPart_andAskToCheck() {
        val mixed = detect("日本で働いた経験 · kinh nghiệm làm việc ở Nhật")
        assertEquals(VIETNAMESE, mixed.language)
        assertTrue(mixed.isAmbiguous && mixed.mixed)
        assertEquals(setOf(VIETNAMESE, JAPANESE), mixed.candidates.toSet())
        // A Latin abbreviation inside Japanese does not make it mixed.
        assertSure(JAPANESE, "日本語のテキストとPC")
    }

    @Test
    fun emojiDigitsAndPunctuation_areIgnored() {
        assertSure(ENGLISH, "experience 👍 2026!")
        assertSure(JAPANESE, "いいね👍🏽")
        assertUnsure(null, "123 456")
        assertUnsure(null, "👍🏽🎉")
        assertUnsure(null, "!!! ... ???")
    }

    @Test
    fun emptyOrOtherScripts_areUnknown() {
        assertUnsure(null, "")
        assertUnsure(null, "   ")
        assertUnsure(null, "Привет")
        assertUnsure(null, "안녕하세요")
        assertTrue(detect("Привет").candidates.isEmpty())
    }

    @Test
    fun targets_neverTheSourceLanguage() {
        assertEquals(listOf(ENGLISH, JAPANESE), TranslationMode.BOTH.targetsFor(VIETNAMESE))
        assertEquals(listOf(ENGLISH), TranslationMode.ENGLISH.targetsFor(VIETNAMESE))
        assertEquals(listOf(VIETNAMESE, JAPANESE), TranslationMode.BOTH.targetsFor(ENGLISH))
        assertEquals(listOf(VIETNAMESE), TranslationMode.ENGLISH.targetsFor(ENGLISH))
        assertEquals(listOf(VIETNAMESE), TranslationMode.JAPANESE.targetsFor(JAPANESE))
        assertEquals(listOf(VIETNAMESE, JAPANESE), TranslationMode.JAPANESE.targetsFor(ENGLISH))
    }
}
