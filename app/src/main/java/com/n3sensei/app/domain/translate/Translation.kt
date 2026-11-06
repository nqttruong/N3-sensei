package com.n3sensei.app.domain.translate

/** A translation asked for by "Dịch nhanh". */
data class TranslationRequest(
    val originalText: String,
    val sourceLanguage: Language,
    val mode: TranslationMode,
    /** By default what [mode] gives for [sourceLanguage] (Vietnamese always among them unless it is the source). */
    val requestedTargets: List<Language> = mode.targetsFor(sourceLanguage),
    /** Surrounding text, if the caller has it; not used by the current providers. */
    val context: String? = null,
)

/** Where a value comes from. Dictionary data is checked content; machine translation is a guess of a model. */
enum class Provider {
    /** N3 Sensei's own content (vocabulary, kanji and their words, the learner's own words). */
    CONTENT_DICTIONARY,

    /** On-device machine translation (Google ML Kit). */
    MACHINE_TRANSLATION,
}

/** A value with its origin. [sourceName]: the content pack or source, when known. */
data class Sourced<out T>(val value: T, val provider: Provider, val sourceName: String? = null)

/** Why a field has no value although it was asked for. */
enum class TranslationFailure {
    /** The language model must be downloaded and the device is offline. */
    OFFLINE,

    /** Downloading the language model failed (network error, storage…). */
    MODEL_DOWNLOAD_FAILED,

    /** The on-device translator failed. */
    TRANSLATION_FAILED,
}

/** The state of one field of a [TranslationResult]. Never an empty string standing for "nothing". */
sealed interface FieldState<out T> {
    /** One value or more (a word with several senses has several meanings), best first. */
    data class Available<T>(val values: List<Sourced<T>>) : FieldState<T> {
        init {
            require(values.isNotEmpty()) { "Available needs a value" }
        }
    }

    /** Not part of this request (e.g. the English translation of an English text). */
    data object NotRequested : FieldState<Nothing>

    /** No source has it (e.g. no pronunciation dictionary yet). */
    data object Unavailable : FieldState<Nothing>

    /** Machine translation could give it once these language models are on the device. */
    data class NeedsModel(val languages: Set<Language>) : FieldState<Nothing>

    data class Failed(val failure: TranslationFailure) : FieldState<Nothing>

    val valuesOrEmpty: List<Sourced<T>> get() = (this as? Available)?.values.orEmpty()
}

/** A Japanese word or text, with its kana reading when a dictionary gives one. */
data class JapaneseText(val text: String, val reading: String?)

/** A real example from a source (never made up). */
data class TranslationExample(val japanese: String, val furigana: String?, val translationVi: String)

/**
 * The translation of [originalText]. Each field says whether it has a value, was not asked for,
 * has no source, needs a language model, or failed, so the screen can show partial results
 * (a meaning without a reading, for instance) honestly.
 */
data class TranslationResult(
    val originalText: String,
    val sourceLanguage: Language,
    val vietnameseMeaning: FieldState<String>,
    val englishTranslation: FieldState<String>,
    /** IPA; no pronunciation dictionary is bundled yet. */
    val englishPronunciation: FieldState<String>,
    val japaneseTranslation: FieldState<JapaneseText>,
    /** Kana reading of the Japanese word shown: the original when it is Japanese, else the translation's. */
    val japaneseReading: FieldState<String>,
    /** No romanization source yet. */
    val japaneseRomaji: FieldState<String>,
    val examples: FieldState<TranslationExample>,
    /** Part of speech, notes, kanji readings and Hán Việt from the dictionary. */
    val usageNotes: FieldState<String>,
) {
    /** Every provider a value came from. */
    val providers: Set<Provider>
        get() = listOf(vietnameseMeaning, englishTranslation, englishPronunciation, japaneseTranslation, japaneseReading, japaneseRomaji, examples, usageNotes)
            .flatMap { field -> field.valuesOrEmpty.map { it.provider } }
            .toSet()

    /** Models still needed for the fields that wait for one. */
    val missingModels: Set<Language>
        get() = listOf(vietnameseMeaning, englishTranslation, japaneseTranslation)
            .filterIsInstance<FieldState.NeedsModel>()
            .flatMap { it.languages }
            .toSet()

    /** Complete enough to keep in the cache: nothing waits for a model or failed. */
    val isFinal: Boolean
        get() = listOf(vietnameseMeaning, englishTranslation, japaneseTranslation)
            .none { it is FieldState.NeedsModel || it is FieldState.Failed }
}

/** Translates a selected text. */
interface TranslationRepository {
    /**
     * [allowModelDownload]: whether missing language models may be downloaded now (over the
     * network); when false, the fields that need one say [FieldState.NeedsModel]. Cancellable:
     * cancelling stops waiting for the download or the translation.
     */
    suspend fun translate(request: TranslationRequest, allowModelDownload: Boolean = false): TranslationResult

    /** Approximate download size of the language models for [languages], in MB; null when unknown. */
    fun approximateDownloadMb(languages: Set<Language>): Int?

    /** Frees the on-device translators (call when no dialog needs them any more). */
    fun release()
}
