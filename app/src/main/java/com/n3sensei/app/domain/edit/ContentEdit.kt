package com.n3sensei.app.domain.edit

import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.furigana.Furigana
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
 * change. Not editable on pack items, by design:
 * - the kanji character and a pack word's spelling: they are what the item is (another
 *   character is another kanji, progress and links follow the id);
 * - example sentences: shared rows of the pack, possibly used by several items;
 * - grammar exercises: an answer key the app checks against.
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

    /** The learner's own words only: one example sentence and its translation (both or none). */
    WORD_EXAMPLE_JA(ItemType.VOCABULARY, "exampleJapanese", FieldKind.TEXT, required = false, maxLength = 200),
    WORD_EXAMPLE_VI(ItemType.VOCABULARY, "exampleTranslation", FieldKind.TEXT, required = false, maxLength = 300),

    GRAMMAR_PATTERN(ItemType.GRAMMAR, "pattern", FieldKind.TEXT, required = true, maxLength = 120),
    GRAMMAR_STRUCTURE(ItemType.GRAMMAR, "structure", FieldKind.TEXT, required = true, maxLength = 300),
    GRAMMAR_MEANING(ItemType.GRAMMAR, "meaningVi", FieldKind.TEXT, required = true, maxLength = 300),
    GRAMMAR_USAGE(ItemType.GRAMMAR, "usage", FieldKind.TEXT, required = false, maxLength = 1500),
    GRAMMAR_CAUTION(ItemType.GRAMMAR, "cautionNote", FieldKind.TEXT, required = false, maxLength = 1000),
    ;

    val multiline: Boolean get() = this in setOf(KANJI_NOTE, WORD_NOTE, GRAMMAR_USAGE, GRAMMAR_CAUTION, GRAMMAR_STRUCTURE)

    companion object {
        val KANJI_FORM = listOf(KANJI_MEANING, KANJI_HAN_VIET, KANJI_ON, KANJI_KUN, KANJI_STROKES, KANJI_NOTE)
        val PACK_WORD_FORM = listOf(WORD_READING, WORD_MEANING, WORD_PART_OF_SPEECH, WORD_NOTE)
        val USER_WORD_FORM = listOf(WORD_EXPRESSION) + PACK_WORD_FORM + listOf(WORD_EXAMPLE_JA, WORD_EXAMPLE_VI)
        val GRAMMAR_FORM = listOf(GRAMMAR_PATTERN, GRAMMAR_STRUCTURE, GRAMMAR_MEANING, GRAMMAR_USAGE, GRAMMAR_CAUTION)
    }
}

enum class FieldError {
    REQUIRED, TOO_LONG, NOT_KANA, NOT_A_NUMBER, OUT_OF_RANGE,

    /** An example sentence without its translation, or the other way round. */
    EXAMPLE_INCOMPLETE,

    /** A kanji example that does not contain the kanji. */
    MISSING_KANJI,

    /** Furigana whose base text is not the example, or kana for a text that is not all kanji. */
    FURIGANA_MISMATCH,
}

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
            FieldKind.TEXT -> if (field.multiline) text.replace("\r", "") else text.replace(Regex("[\\t\\r\\n]"), " ")
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

    private fun isJapanese(c: Char): Boolean = c in '\u3040'..'\u30FF' || c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c == '々'

    /**
     * Validates every field of a form; returns the normalized values or the errors.
     *
     * @param readingOptional a word saved from "Dịch nhanh" and not studied: its reading may stay
     *   empty when the app has none (a machine-translated word), so nothing is made up to save it.
     */
    fun validateAll(values: Map<EditableField, String>, readingOptional: Boolean = false): Pair<Map<EditableField, String>, Map<EditableField, FieldError>> {
        val ok = mutableMapOf<EditableField, String>()
        val errors = mutableMapOf<EditableField, FieldError>()
        values.forEach { (field, raw) ->
            when (val r = validate(field, raw)) {
                is Result.Valid -> ok[field] = r.value
                is Result.Invalid -> errors[field] = r.error
            }
        }
        // A kana reading is required for a Japanese word only: a word with no Japanese in it (an
        // English or Vietnamese "Dịch nhanh" bookmark) has none, and none is made up.
        val expression = ok[EditableField.WORD_EXPRESSION]
        if (errors[EditableField.WORD_READING] == FieldError.REQUIRED && (readingOptional || expression != null && expression.none(::isJapanese))) {
            errors.remove(EditableField.WORD_READING)
            ok[EditableField.WORD_READING] = ""
        }
        val ja = ok[EditableField.WORD_EXAMPLE_JA]
        val vi = ok[EditableField.WORD_EXAMPLE_VI]
        if (ja != null && vi != null && ja.isEmpty() != vi.isEmpty()) {
            errors[if (ja.isEmpty()) EditableField.WORD_EXAMPLE_JA else EditableField.WORD_EXAMPLE_VI] = FieldError.EXAMPLE_INCOMPLETE
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
    const val EXAMPLE_ID_PREFIX = "user-x-"

    /** The learner's examples of a kanji (in the same pack as the learner's words). */
    const val KANJI_EXAMPLE_ID_PREFIX = "user-x-k-"

    /** Ids a content pack may not use: they belong to the learner's words and their examples. */
    const val RESERVED_ID_PREFIX = "user-"
}

/** The parts of a kanji example in the edit form. */
enum class ExampleField(val maxLength: Int) { JAPANESE(200), FURIGANA(400), MEANING(300) }

/** Example form text, as typed. */
data class ExampleDraft(val japanese: String = "", val furigana: String = "", val meaningVi: String = "")

/** A word or sentence shown with a kanji: the pack's own ([own] false, read only) or the learner's. */
data class KanjiExample(val id: String, val japanese: String, val furigana: String?, val meaningVi: String, val own: Boolean) {
    fun toDraft() = ExampleDraft(japanese, furigana.orEmpty(), meaningVi)
}

/** A valid example, normalized: [furigana] is `{base|reading}` markup or null. */
data class ValidExample(val japanese: String, val furigana: String?, val meaningVi: String)

sealed interface ExampleSaveResult {
    data class Saved(val id: String) : ExampleSaveResult
    data class Invalid(val errors: Map<ExampleField, FieldError>) : ExampleSaveResult
    data object NotFound : ExampleSaveResult
    data object Failed : ExampleSaveResult
}

/**
 * Checks a learner's example for [kanji]: Japanese and meaning are required, the Japanese must
 * contain the kanji (so no word is attached to the wrong kanji). Furigana is optional, either
 * `{base|reading}` markup whose base text is the Japanese, or kana only when the Japanese is
 * written in kanji only (then it becomes one ruby over the whole text).
 */
object KanjiExampleValidator {
    private val KANA = Regex("[ぁ-ゖァ-ヺー・]+")
    private val HAS_KANA = Regex("[ぁ-ゖァ-ヺー]")

    fun validate(kanji: String, draft: ExampleDraft): Pair<ValidExample?, Map<ExampleField, FieldError>> {
        fun clean(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC).replace(Regex("[\\t\\r\\n]"), " ").trim()
        val japanese = clean(draft.japanese)
        val meaning = clean(draft.meaningVi)
        val furiganaInput = clean(draft.furigana).replace(Regex("[\\s\u3000]+"), "")
        val errors = mutableMapOf<ExampleField, FieldError>()
        when {
            japanese.isEmpty() -> errors[ExampleField.JAPANESE] = FieldError.REQUIRED
            japanese.length > ExampleField.JAPANESE.maxLength -> errors[ExampleField.JAPANESE] = FieldError.TOO_LONG
            kanji !in japanese -> errors[ExampleField.JAPANESE] = FieldError.MISSING_KANJI
        }
        when {
            meaning.isEmpty() -> errors[ExampleField.MEANING] = FieldError.REQUIRED
            meaning.length > ExampleField.MEANING.maxLength -> errors[ExampleField.MEANING] = FieldError.TOO_LONG
        }
        val furigana: String? = when {
            furiganaInput.isEmpty() -> null
            furiganaInput.length > ExampleField.FURIGANA.maxLength -> null.also { errors[ExampleField.FURIGANA] = FieldError.TOO_LONG }
            furiganaInput.contains('{') -> furiganaInput.takeIf {
                Furigana.isValid(it) && Furigana.baseText(it) == japanese && Furigana.parse(it).any { s -> s.reading != null }
            } ?: null.also { errors[ExampleField.FURIGANA] = FieldError.FURIGANA_MISMATCH }
            !KANA.matches(furiganaInput) -> null.also { errors[ExampleField.FURIGANA] = FieldError.NOT_KANA }
            japanese.isEmpty() || HAS_KANA.containsMatchIn(japanese) -> null.also { errors[ExampleField.FURIGANA] = FieldError.FURIGANA_MISMATCH }
            else -> "{$japanese|$furiganaInput}"
        }
        return (if (errors.isEmpty()) ValidExample(japanese, furigana, meaning) else null) to errors
    }
}

/** A kanji the learner can link a word to (any installed pack). */
data class KanjiChoice(val id: String, val character: String, val hanViet: String?, val meaningVi: String, val packTitle: String)

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
    /** Edited fields whose value the pack changed since (conflict: the learner's value is kept). */
    val changedInPack: Set<EditableField> = emptySet(),
    /** Vocabulary only: kanji the word is linked to. */
    val linkedKanji: List<KanjiChoice> = emptyList(),
    /** The item has a schedule (deleting it loses the schedule). */
    val hasProgress: Boolean = false,
    /** A saved, not studied, word: its reading may stay empty ([ContentEditValidator.validateAll]). */
    val readingOptional: Boolean = false,
)

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Invalid(val errors: Map<EditableField, FieldError>) : SaveResult

    /**
     * The same spelling and reading is already a word (in a pack or among the learner's words).
     * Nothing was written; the learner may open that word or save again with `allowDuplicate`.
     */
    data class DuplicateWord(val existingId: String, val packTitle: String) : SaveResult
    data object NotFound : SaveResult
    data object Failed : SaveResult
}
