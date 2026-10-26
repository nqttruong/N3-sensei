package com.n3sensei.app.data.local

import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity

/**
 * Reads and writes one field of a kanji / vocabulary row as the string stored in
 * `user_content_edits` (`""` = null or empty list; lists use [StringListCodec]).
 */
internal object ContentEdits {
    /** Hidden field: a word's furigana is cleared when its reading is edited (it would no longer match). */
    const val FURIGANA = "furigana"

    fun get(row: KanjiEntity, field: String): String = when (field) {
        "hanViet" -> row.hanViet.orEmpty()
        "meaningVi" -> row.meaningVi
        "onReadings" -> StringListCodec.encode(row.onReadings)
        "kunReadings" -> StringListCodec.encode(row.kunReadings)
        "strokeCount" -> row.strokeCount?.toString().orEmpty()
        "note" -> row.note.orEmpty()
        else -> error("Unknown kanji field $field")
    }

    fun set(row: KanjiEntity, field: String, value: String): KanjiEntity = when (field) {
        "hanViet" -> row.copy(hanViet = value.ifEmpty { null })
        "meaningVi" -> row.copy(meaningVi = value)
        "onReadings" -> row.copy(onReadings = StringListCodec.decode(value))
        "kunReadings" -> row.copy(kunReadings = StringListCodec.decode(value))
        "strokeCount" -> row.copy(strokeCount = value.toIntOrNull())
        "note" -> row.copy(note = value.ifEmpty { null })
        else -> error("Unknown kanji field $field")
    }

    fun get(row: VocabularyEntity, field: String): String = when (field) {
        "expression" -> row.expression
        "reading" -> row.reading
        FURIGANA -> row.furigana.orEmpty()
        "meaningVi" -> row.meaningVi
        "partOfSpeech" -> row.partOfSpeech.orEmpty()
        "note" -> row.note.orEmpty()
        else -> error("Unknown vocabulary field $field")
    }

    fun set(row: VocabularyEntity, field: String, value: String): VocabularyEntity = when (field) {
        "expression" -> row.copy(expression = value)
        "reading" -> row.copy(reading = value)
        FURIGANA -> row.copy(furigana = value.ifEmpty { null })
        "meaningVi" -> row.copy(meaningVi = value)
        "partOfSpeech" -> row.copy(partOfSpeech = value.ifEmpty { null })
        "note" -> row.copy(note = value.ifEmpty { null })
        else -> error("Unknown vocabulary field $field")
    }

    /** The pack's row: [row] (merged) with every edited field back to its base value. */
    fun base(row: KanjiEntity, edits: List<UserContentEditEntity>) = edits.fold(row) { r, e -> set(r, e.field, e.baseValue) }
    fun base(row: VocabularyEntity, edits: List<UserContentEditEntity>) = edits.fold(row) { r, e -> set(r, e.field, e.baseValue) }

    fun merged(base: KanjiEntity, edits: List<UserContentEditEntity>) = edits.fold(base) { r, e -> set(r, e.field, e.value) }
    fun merged(base: VocabularyEntity, edits: List<UserContentEditEntity>) = edits.fold(base) { r, e -> set(r, e.field, e.value) }
}
