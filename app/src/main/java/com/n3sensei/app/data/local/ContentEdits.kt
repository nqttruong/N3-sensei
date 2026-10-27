package com.n3sensei.app.data.local

import com.n3sensei.app.data.local.entity.GrammarEntity
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

    fun get(row: GrammarEntity, field: String): String = when (field) {
        "pattern" -> row.pattern
        "structure" -> row.structure
        "meaningVi" -> row.meaningVi
        "usage" -> row.usage
        "cautionNote" -> row.cautionNote.orEmpty()
        else -> error("Unknown grammar field $field")
    }

    fun set(row: GrammarEntity, field: String, value: String): GrammarEntity = when (field) {
        "pattern" -> row.copy(pattern = value)
        "structure" -> row.copy(structure = value)
        "meaningVi" -> row.copy(meaningVi = value)
        "usage" -> row.copy(usage = value)
        "cautionNote" -> row.copy(cautionNote = value.ifEmpty { null })
        else -> error("Unknown grammar field $field")
    }

    /**
     * Edits as they apply to a freshly written pack row: each base becomes the row's value, and
     * an edit whose base the pack changed is flagged (the learner's value still wins).
     */
    fun rebase(edits: List<UserContentEditEntity>, baseOf: (String) -> String): List<UserContentEditEntity> = edits.map { e ->
        val base = baseOf(e.field)
        if (base == e.baseValue) e else e.copy(baseValue = base, baseChanged = true)
    }

    /** The pack's row: [row] (merged) with every edited field back to its base value. */
    fun base(row: KanjiEntity, edits: List<UserContentEditEntity>) = edits.fold(row) { r, e -> set(r, e.field, e.baseValue) }
    fun base(row: VocabularyEntity, edits: List<UserContentEditEntity>) = edits.fold(row) { r, e -> set(r, e.field, e.baseValue) }

    fun base(row: GrammarEntity, edits: List<UserContentEditEntity>) = edits.fold(row) { r, e -> set(r, e.field, e.baseValue) }

    fun merged(base: KanjiEntity, edits: List<UserContentEditEntity>) = edits.fold(base) { r, e -> set(r, e.field, e.value) }
    fun merged(base: GrammarEntity, edits: List<UserContentEditEntity>) = edits.fold(base) { r, e -> set(r, e.field, e.value) }
    fun merged(base: VocabularyEntity, edits: List<UserContentEditEntity>) = edits.fold(base) { r, e -> set(r, e.field, e.value) }
}
