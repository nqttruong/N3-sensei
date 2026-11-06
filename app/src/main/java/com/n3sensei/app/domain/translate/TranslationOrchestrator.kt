package com.n3sensei.app.domain.translate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import java.util.Locale

/**
 * "Dịch nhanh" translation: dictionary first, on-device machine translation for what the
 * dictionary does not have.
 *
 * - Dictionary entries (exact matches of the whole text) give meanings, Japanese words with
 *   their readings, notes and real examples. They are never replaced by machine translation.
 * - Machine translation fills a requested field the dictionary leaves empty, marked as such.
 *   A machine Japanese translation gets a reading only if the dictionary has that exact word.
 * - Fields without any source (English IPA, romaji, examples beyond the dictionary) are
 *   [FieldState.Unavailable], never guessed.
 * - Results with nothing pending are cached ([TranslationCache]); keys hold the text, the
 *   languages and the dictionary/engine version, so no result serves another language.
 */
class TranslationOrchestrator(
    private val dictionary: DictionaryProvider,
    private val machine: MachineTranslator,
    private val cache: TranslationCache = TranslationCache(),
) : TranslationRepository {

    override suspend fun translate(request: TranslationRequest, allowModelDownload: Boolean): TranslationResult = coroutineScope {
        val text = normalize(request.originalText)
        val source = request.sourceLanguage
        val targets = request.requestedTargets.filter { it != source }.distinct()
        val key = TranslationCache.Key(text, source, targets.toSet(), "$ENGINE_VERSION/${dictionary.version()}")
        cache[key]?.let { return@coroutineScope it }

        val entries = when (source) {
            Language.JAPANESE -> dictionary.lookupJapanese(text)
            Language.VIETNAMESE -> dictionary.lookupVietnamese(text)
            Language.ENGLISH -> dictionary.lookupEnglish(text)
        }
        val sourced = { value: String, entry: DictionaryEntry -> Sourced(value, Provider.CONTENT_DICTIONARY, entry.sourceName) }

        suspend fun machine(to: Language): FieldState<String> = machineField(text, source, to, allowModelDownload)

        // The three fields run together, so every model download the user allowed starts at
        // once: a cancelled request (dialog closed) leaves no download waiting its turn.
        val vietnameseField = async {
            when {
                Language.VIETNAMESE !in targets -> FieldState.NotRequested
                else -> entries.mapNotNull { e -> e.meaningVi?.let { sourced(it, e) } }.distinctBy { it.value }.available()
                    ?: machine(Language.VIETNAMESE)
            }
        }
        val englishField = async {
            when {
                Language.ENGLISH !in targets -> FieldState.NotRequested
                else -> entries.mapNotNull { e -> e.english?.let { sourced(it, e) } }.distinctBy { it.value }.available()
                    ?: machine(Language.ENGLISH)
            }
        }
        val japaneseField = async {
            when {
                Language.JAPANESE !in targets -> FieldState.NotRequested
                else -> entries.mapNotNull { e -> e.japanese?.let { Sourced(JapaneseText(it, e.reading), Provider.CONTENT_DICTIONARY, e.sourceName) } }
                    .distinctBy { it.value }.available()
                    ?: machine(Language.JAPANESE).withReadings()
            }
        }
        val vietnamese: FieldState<String> = vietnameseField.await()
        val english: FieldState<String> = englishField.await()
        val japanese: FieldState<JapaneseText> = japaneseField.await()
        val reading: FieldState<String> = when (source) {
            Language.JAPANESE -> entries.mapNotNull { e -> e.reading?.let { sourced(it, e) } }.distinctBy { it.value }.available() ?: FieldState.Unavailable
            else -> when (japanese) {
                is FieldState.Available -> japanese.values.mapNotNull { j -> j.value.reading?.let { Sourced(it, Provider.CONTENT_DICTIONARY, j.sourceName) } }
                    .distinctBy { it.value }.available() ?: FieldState.Unavailable
                else -> if (Language.JAPANESE in targets) FieldState.Unavailable else FieldState.NotRequested
            }
        }
        val result = TranslationResult(
            originalText = request.originalText,
            sourceLanguage = source,
            vietnameseMeaning = vietnamese,
            englishTranslation = english,
            englishPronunciation = if (source == Language.ENGLISH || Language.ENGLISH in targets) FieldState.Unavailable else FieldState.NotRequested,
            japaneseTranslation = japanese,
            japaneseReading = reading,
            japaneseRomaji = if (source == Language.JAPANESE || Language.JAPANESE in targets) FieldState.Unavailable else FieldState.NotRequested,
            examples = entries.flatMap { e -> e.examples.map { Sourced(it, Provider.CONTENT_DICTIONARY, e.sourceName) } }
                .distinctBy { it.value }.available() ?: FieldState.Unavailable,
            usageNotes = entries.flatMap { e -> e.notes.map { sourced(it, e) } }.distinctBy { it.value }.available() ?: FieldState.Unavailable,
        )
        if (result.isFinal) cache[key] = result
        result
    }

    override fun approximateDownloadMb(languages: Set<Language>): Int? = machine.approximateDownloadMb(languages)

    override fun release() = machine.release()

    private suspend fun machineField(text: String, from: Language, to: Language, allowDownload: Boolean): FieldState<String> {
        val missing = machine.missingModels(from, to)
        if (missing.isNotEmpty()) {
            if (!allowDownload) return FieldState.NeedsModel(missing)
            try {
                machine.downloadModels(from, to)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelDownloadException) {
                return FieldState.Failed(e.failure)
            }
        }
        return try {
            val translated = machine.translate(text, from, to).trim()
            if (translated.isEmpty()) FieldState.Failed(TranslationFailure.TRANSLATION_FAILED)
            else FieldState.Available(listOf(Sourced(translated, Provider.MACHINE_TRANSLATION)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FieldState.Failed(TranslationFailure.TRANSLATION_FAILED)
        }
    }

    /** A machine Japanese word gets the reading of the dictionary's identical word, if there is one. */
    private suspend fun FieldState<String>.withReadings(): FieldState<JapaneseText> = when (this) {
        is FieldState.Available -> FieldState.Available(
            values.map { s ->
                val reading = dictionary.lookupJapanese(s.value).firstNotNullOfOrNull { it.reading }
                Sourced(JapaneseText(s.value, reading), s.provider, s.sourceName)
            },
        )
        is FieldState.NeedsModel -> this
        is FieldState.Failed -> this
        FieldState.NotRequested -> FieldState.NotRequested
        FieldState.Unavailable -> FieldState.Unavailable
    }

    private fun <T> List<Sourced<T>>.available(): FieldState<T>? = if (isEmpty()) null else FieldState.Available(this)

    companion object {
        /** Raise when the way results are built changes, so cached results are not reused. */
        const val ENGINE_VERSION = 1

        /** NFC, outer whitespace trimmed and inner runs of whitespace as one space. */
        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFC).trim().replace(WHITESPACE, " ")

        /** For comparing dictionary text: [normalize]d and lower case (Vietnamese and English). */
        fun comparable(text: String): String = normalize(text).lowercase(Locale.ROOT)

        private val WHITESPACE = Regex("\\s+")
    }
}

/** Recent results, in memory only (never written to disk or logs). */
class TranslationCache(private val capacity: Int = 100) {
    data class Key(val text: String, val source: Language, val targets: Set<Language>, val version: String)

    private val entries = object : LinkedHashMap<Key, TranslationResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TranslationResult>?) = size > capacity
    }

    @Synchronized
    operator fun get(key: Key): TranslationResult? = entries[key]

    @Synchronized
    operator fun set(key: Key, result: TranslationResult) {
        entries[key] = result
    }

    @get:Synchronized
    val size: Int get() = entries.size
}
