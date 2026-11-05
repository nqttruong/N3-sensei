package com.n3sensei.app.domain.edit

import com.n3sensei.app.domain.edit.ContentEditValidator.Result.Invalid
import com.n3sensei.app.domain.edit.ContentEditValidator.Result.Valid
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentEditValidatorTest {

    private fun check(field: EditableField, raw: String) = ContentEditValidator.validate(field, raw)

    @Test
    fun requiredFields_cannotBeEmpty_optionalOnesCanBeCleared() {
        assertEquals(Invalid(FieldError.REQUIRED), check(EditableField.KANJI_MEANING, "   "))
        assertEquals(Invalid(FieldError.REQUIRED), check(EditableField.WORD_READING, ""))
        // "" is a value (cleared), not "unchanged".
        assertEquals(Valid(""), check(EditableField.KANJI_HAN_VIET, "  "))
        assertEquals(Valid(""), check(EditableField.KANJI_ON, " 、 "))
    }

    @Test
    fun readings_areKanaOnly_andNormalized() {
        assertEquals(Valid("ケイ、キョウ"), check(EditableField.KANJI_ON, "ケイ, キョウ"))
        assertEquals(Valid("へ.る、たす.ける"), check(EditableField.KANJI_KUN, "へ.る　たす.ける"))
        assertEquals(Invalid(FieldError.NOT_KANA), check(EditableField.KANJI_KUN, "he.ru"))
        assertEquals(Valid("けいけん"), check(EditableField.WORD_READING, "けい けん"))
        assertEquals(Invalid(FieldError.NOT_KANA), check(EditableField.WORD_READING, "経験"))
    }

    @Test
    fun strokes_areANumberFrom1To40() {
        assertEquals(Valid("11"), check(EditableField.KANJI_STROKES, " 011 "))
        assertEquals(Invalid(FieldError.NOT_A_NUMBER), check(EditableField.KANJI_STROKES, "mười"))
        assertEquals(Invalid(FieldError.OUT_OF_RANGE), check(EditableField.KANJI_STROKES, "0"))
        assertEquals(Invalid(FieldError.OUT_OF_RANGE), check(EditableField.KANJI_STROKES, "41"))
    }

    @Test
    fun textIsTrimmedAndNfc_andLengthIsLimited() {
        // "ệ" typed as e + combining marks becomes one code point.
        assertEquals(Valid("nghiệm"), check(EditableField.WORD_MEANING, " nghiệm "))
        assertEquals(Invalid(FieldError.TOO_LONG), check(EditableField.WORD_MEANING, "a".repeat(201)))
    }

    @Test
    fun kanjiExample_needsJapaneseWithTheKanji_andAMeaning() {
        assertEquals(
            ValidExample("経験", null, "kinh nghiệm") to emptyMap<ExampleField, FieldError>(),
            KanjiExampleValidator.validate("経", ExampleDraft(" 経験 ", "", " kinh nghiệm ")),
        )
        assertEquals(
            mapOf(ExampleField.JAPANESE to FieldError.REQUIRED, ExampleField.MEANING to FieldError.REQUIRED),
            KanjiExampleValidator.validate("経", ExampleDraft()).second,
        )
        assertEquals(mapOf(ExampleField.JAPANESE to FieldError.MISSING_KANJI), KanjiExampleValidator.validate("経", ExampleDraft("増える", "", "tăng")).second)
        assertEquals(mapOf(ExampleField.JAPANESE to FieldError.TOO_LONG), KanjiExampleValidator.validate("経", ExampleDraft("経".repeat(201), "", "x")).second)
    }

    @Test
    fun kanjiExample_furiganaIsMarkupForTheSameText_orKanaForAnAllKanjiWord() {
        // Kana over an all-kanji word: one ruby over the whole word.
        assertEquals("{経験|けいけん}", KanjiExampleValidator.validate("経", ExampleDraft("経験", "けい けん", "x")).first!!.furigana)
        // Markup whose base text is the example.
        assertEquals("{経験|けいけん}があります", KanjiExampleValidator.validate("経", ExampleDraft("経験があります", "{経験|けいけん}があります", "x")).first!!.furigana)
        // Kana over a text with kana in it would put the reading in the wrong place.
        assertEquals(mapOf(ExampleField.FURIGANA to FieldError.FURIGANA_MISMATCH), KanjiExampleValidator.validate("経", ExampleDraft("経験がある", "けいけんがある", "x")).second)
        // Markup for another text, or broken markup.
        listOf("{経由|けいゆ}", "{経験|けいけん", "{経験}").forEach {
            assertEquals(it, mapOf(ExampleField.FURIGANA to FieldError.FURIGANA_MISMATCH), KanjiExampleValidator.validate("経", ExampleDraft("経験", it, "x")).second)
        }
        assertEquals(mapOf(ExampleField.FURIGANA to FieldError.NOT_KANA), KanjiExampleValidator.validate("経", ExampleDraft("経験", "keiken", "x")).second)
    }
}
