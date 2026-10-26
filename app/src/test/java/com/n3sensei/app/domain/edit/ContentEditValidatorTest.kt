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
}
