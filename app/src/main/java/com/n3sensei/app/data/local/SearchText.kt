package com.n3sensei.app.data.local

import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes text for the `search_text` columns and for search queries, so that
 * - Vietnamese matches regardless of case and diacritics ("Kinh nghiem" finds "kinh nghiệm"),
 * - katakana and hiragana match each other ("けい" finds "ケイ"),
 * - Japanese voiced marks are kept ("が" never matches "か").
 */
object SearchText {

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var previousBase = ' '
        for (ch in decomposed) {
            val isMark = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()
            // Strip accents from Latin letters only; kana voicing marks (U+3099/U+309A) stay.
            if (isMark && previousBase.code < LATIN_LIMIT) continue
            if (!isMark) previousBase = ch
            out.append(ch)
        }
        return Normalizer.normalize(out, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace('đ', 'd')
            .map(::katakanaToHiragana)
            .joinToString("")
    }

    /** Joins searchable fields; [separator] keeps a query from matching across fields. */
    fun of(vararg parts: String?): String =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(SEPARATOR) { normalize(it) }

    private fun katakanaToHiragana(ch: Char): Char =
        if (ch in 'ァ'..'ヶ') ch - KANA_OFFSET else ch

    private const val LATIN_LIMIT = 0x0250
    private const val KANA_OFFSET = 0x60
    private const val SEPARATOR = "\n"
}

// Search text for each searchable entity; applied when content is written.

internal fun VocabularyEntity.withSearchText() =
    copy(searchText = SearchText.of(expression, reading, meaningVi))

internal fun KanjiEntity.withSearchText() =
    copy(searchText = SearchText.of(character, onReadings.joinToString(" "), kunReadings.joinToString(" "), hanViet, meaningVi))

internal fun GrammarEntity.withSearchText() =
    copy(searchText = SearchText.of(pattern, structure, meaningVi))
