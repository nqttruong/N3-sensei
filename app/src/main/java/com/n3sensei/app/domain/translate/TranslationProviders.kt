package com.n3sensei.app.domain.translate

/**
 * A dictionary entry matched **exactly** (never a partial or "similar" hit).
 *
 * @property japanese the Japanese word, or null when the entry has none.
 * @property reading its kana reading, when known.
 * @property meaningVi the Vietnamese meaning, as the source gives it.
 * @property english the English equivalent, when the source has one (N3 Sensei's content has none).
 * @property notes part of speech, notes, kanji readings or Hán Việt.
 */
data class DictionaryEntry(
    val japanese: String?,
    val reading: String?,
    val meaningVi: String?,
    val english: String? = null,
    val notes: List<String> = emptyList(),
    val examples: List<TranslationExample> = emptyList(),
    val sourceName: String? = null,
)

/** Exact lookups in a dictionary. */
interface DictionaryProvider {
    /** Entries whose Japanese word or kana reading is exactly [text]. */
    suspend fun lookupJapanese(text: String): List<DictionaryEntry>

    /** Entries one of whose Vietnamese senses is exactly [text] (case and spacing aside). */
    suspend fun lookupVietnamese(text: String): List<DictionaryEntry>

    /** Entries whose English word is exactly [text]. */
    suspend fun lookupEnglish(text: String): List<DictionaryEntry>

    /** Changes when the dictionary content changes (a pack update, a new word of the learner). */
    suspend fun version(): String
}

/** A model download that did not happen. */
class ModelDownloadException(val failure: TranslationFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** On-device machine translation. Text never leaves the device for translation. */
interface MachineTranslator {
    /** Languages whose model is missing for translating [from] → [to] (empty: ready). */
    suspend fun missingModels(from: Language, to: Language): Set<Language>

    /** Downloads the models [from] → [to] needs, once even when asked concurrently; throws [ModelDownloadException]. */
    suspend fun downloadModels(from: Language, to: Language)

    /** Translates on the device; the models must be there. */
    suspend fun translate(text: String, from: Language, to: Language): String

    /** Approximate download size of the models [languages] need, in MB; null when unknown. */
    fun approximateDownloadMb(languages: Set<Language>): Int?

    /** Frees loaded translators. */
    fun release()
}
