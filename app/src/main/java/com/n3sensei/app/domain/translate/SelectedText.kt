package com.n3sensei.app.domain.translate

import java.text.Normalizer

/**
 * Text another app hands over to "Dịch nhanh" (Android's text selection menu), checked before
 * it is shown. The text itself is never changed beyond trimming the outer whitespace and
 * Unicode NFC (the composed form, so "ệ" typed as e + two marks compares like "ệ"): no
 * spelling fix, no script conversion, no character removed inside the text.
 */
sealed interface SelectedText {

    /** Text to show. [readOnly]: the source app said its text cannot be replaced (never replaced anyway). */
    data class Ready(val text: String, val readOnly: Boolean) : SelectedText

    /** No text was given (wrong action, missing extra). */
    data object Missing : SelectedText

    /** Only whitespace. */
    data object Blank : SelectedText

    /** Longer than [limit] Unicode code points: refused, never cut (a cut could change the meaning). */
    data class TooLong(val codePoints: Int, val limit: Int) : SelectedText

    companion object {
        /** Words, phrases and short sentences. */
        const val MAX_CODE_POINTS = 200

        /**
         * Beyond this many UTF-16 units the text is refused before normalizing, so a huge
         * selection costs one count, not a normalization on the main thread. Generous on
         * purpose: a decomposed Vietnamese letter takes up to 3 units, a surrogate pair 2.
         */
        private const val MAX_RAW_LENGTH = MAX_CODE_POINTS * 8

        fun from(raw: CharSequence?, readOnly: Boolean = false): SelectedText {
            if (raw == null) return Missing
            if (raw.length > MAX_RAW_LENGTH) {
                return TooLong(Character.codePointCount(raw, 0, raw.length), MAX_CODE_POINTS)
            }
            val text = Normalizer.normalize(raw.toString(), Normalizer.Form.NFC).trim()
            if (text.isEmpty()) return Blank
            val codePoints = text.codePointCount(0, text.length)
            if (codePoints > MAX_CODE_POINTS) return TooLong(codePoints, MAX_CODE_POINTS)
            return Ready(text, readOnly)
        }
    }
}
