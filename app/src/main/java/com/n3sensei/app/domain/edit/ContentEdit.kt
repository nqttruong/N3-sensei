package com.n3sensei.app.domain.edit

import com.n3sensei.app.domain.model.ItemType
import java.text.Normalizer

/** How a field is typed in the edit form. */
enum class FieldKind {
    TEXT,

    /** Kana only (a word's reading). */
    KANA,

    /** Kana readings separated by 、 or commas (kanji On/Kun; KANJIDIC style "たす.ける" allowed). */
    READINGS,

    /** Stroke count 1–40. */
    NUMBER,
}

/**
 * Fields the learner may change. [key] is stored in `user_content_edits.field` and must not
 * change. The kanji character and a pack word's spelling are not editable: they are what the
 * item is (a different character is a different kanji).
 */
enum class EditableField(val type: ItemType, val key: String, val kind: FieldKind, val required: Boolean, val maxLength: Int) {
    KANJI_HAN_VIET(ItemType.KANJI, "hanViet", FieldKind.TEXT, required = false, maxLength = 60),
    KANJI_MEANING(ItemType.KANJI, "meaningVi", FieldKind.TEXT, required = true, maxLength = 200),
    KANJI_ON(ItemType.KANJI, "onReadings", FieldKind.READINGS, required = false, maxLength = 120),
    KANJI_KUN(ItemType.KANJI, "kunReadings", FieldKind.READINGS, required = false, maxLength = 200),
    KANJI_STROKES(ItemType.KANJI, "strokeCount", FieldKind.NUMBER, required = false, maxLength = 2),
    KANJI_NOTE(ItemType.KANJI, "note", FieldKind.TEXT, required = false, maxLength = 500),

    /** Only for the learner's own words. */
    WORD_EXPRESSION(ItemType.VOCABULARY, "expression", FieldKind.TEXT, required = true, maxLength = 60),
    WORD_READING(ItemType.VOCABULARY, "reading", FieldKind.KANA, required = true, maxLength = 60),
    WORD_MEANING(ItemType.VOCABULARY, "meaningVi", FieldKind.TEXT, required = true, maxLength = 200),
    WORD_PART_OF_SPEECH(ItemType.VOCABULARY, "partOfSpeech", FieldKind.TEXT, required = false, maxLength = 60),
    WORD_NOTE(ItemType.VOCABULARY, "note", FieldKind.TEXT, required = false, maxLength = 500),
    ;

    companion object {
        val KANJI_FORM = listOf(KANJI_MEANING, KANJI_HAN_VIET, KANJI_ON, KANJI_KUN, KANJI_STROKES, KANJI_NOTE)
        val PACK_WORD_FORM = listOf(WORD_READING, WORD_MEANING, WORD_PART_OF_SPEECH, WORD_NOTE)
        val USER_WORD_FORM = listOf(WORD_EXPRESSION) + PACK_WORD_FORM
    }
}

enum class FieldError { REQUIRED, TOO_LONG, NOT_KANA, NOT_A_NUMBER, OUT_OF_RANGE }

/**
 * Form text → normalized form text. Normalized values are what the repository stores and
 * compares: `""` for an optional field means "cleared", readings are joined with "、".
 */
object ContentEditValidator {
    private val KANA = Regex("[ぁ-ゖァ-ヺー・]+")
    private val READING = Regex("[ぁ-ゖァ-ヺー.\\-]+")
    // \s does not match the ideographic space a Japanese keyboard types.
    private val READING_SEPARATORS = Regex("[、,，;；\\s\u3000]+")
    const val MAX_STROKES = 40

    sealed interface Result {
        data class Valid(val value: String) : Result
        data class Invalid(val error: FieldError) : Result
    }

    fun readings(value: String): List<String> = value.split(READING_SEPARATORS).filter { it.isNotEmpty() }

    fun validate(field: EditableField, raw: String): Result {
        val text = Normalizer.normalize(raw, Normalizer.Form.NFC).trim()
        val value = when (field.kind) {
            FieldKind.TEXT -> text.replace(Regex("[\\t\\r]"), " ")
            FieldKind.KANA -> text.replace(Regex("[\\s\u3000]+"), "")
            FieldKind.READINGS -> readings(text).joinToString("、")
            FieldKind.NUMBER -> text
        }
        if (value.isEmpty()) return if (field.required) Result.Invalid(FieldError.REQUIRED) else Result.Valid("")
        // A number is checked by its range ("011" is 11), not its length.
        if (field.kind != FieldKind.NUMBER && value.length > field.maxLength) return Result.Invalid(FieldError.TOO_LONG)
        return when (field.kind) {
            FieldKind.TEXT -> Result.Valid(value)
            FieldKind.KANA -> if (KANA.matches(value)) Result.Valid(value) else Result.Invalid(FieldError.NOT_KANA)
            FieldKind.READINGS ->
                if (readings(value).all { READING.matches(it) }) Result.Valid(value) else Result.Invalid(FieldError.NOT_KANA)
            FieldKind.NUMBER -> when (val n = value.toIntOrNull()) {
                null -> Result.Invalid(FieldError.NOT_A_NUMBER)
                !in 1..MAX_STROKES -> Result.Invalid(FieldError.OUT_OF_RANGE)
                else -> Result.Valid(n.toString())
            }
        }
    }

    /** Validates every field of a form; returns the normalized values or the errors. */
    fun validateAll(values: Map<EditableField, String>): Pair<Map<EditableField, String>, Map<EditableField, FieldError>> {
        val ok = mutableMapOf<EditableField, String>()
        val errors = mutableMapOf<EditableField, FieldError>()
        values.forEach { (field, raw) ->
            when (val r = validate(field, raw)) {
                is Result.Valid -> ok[field] = r.value
                is Result.Invalid -> errors[field] = r.error
            }
        }
        return ok to errors
    }
}

/** The learner's own words live in this pack, which no content pack can replace. */
object UserWords {
    const val PACK_ID = "user-words"
    const val LESSON_ID = "user-words-l1"
    const val SOURCE_ID = "user-words"
    const val ID_PREFIX = "user-w-"
}

/**
 * An item opened in the edit form. [values] are the current (merged) values, [original] the
 * pack's values (equal to [values] for fields not in [edited]); both in form text.
 */
data class EditableItem(
    val type: ItemType,
    val id: String,
    /** Title shown above the form: the kanji or the word. */
    val label: String,
    val userCreated: Boolean,
    val fields: List<EditableField>,
    val values: Map<EditableField, String>,
    val original: Map<EditableField, String>,
    val edited: Set<EditableField>,
)

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Invalid(val errors: Map<EditableField, FieldError>) : SaveResult

    /** The same spelling and reading is already a word (in a pack or among the learner's words). */
    data class DuplicateWord(val existingId: String, val packTitle: String) : SaveResult
    data object NotFound : SaveResult
    data object Failed : SaveResult
}
