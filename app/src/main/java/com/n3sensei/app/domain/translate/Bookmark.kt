package com.n3sensei.app.domain.translate

/** A translation kept with a bookmarked word. */
data class BookmarkTranslation(val language: Language, val text: String, val reading: String?)

/**
 * What one bookmark of the "Dịch nhanh" dialog saves into "Từ của tôi": a single entry with every
 * translation the dialog has (English and Japanese together in "Cả hai"), never invented values.
 *
 * @property key identifies the entry: the normalized selected text, plus its reading for a
 *   Japanese word whose one reading is known (so 生 なま and 生 せい stay apart). With several
 *   possible readings, or none known, the text alone is the key: one entry holding them all.
 * @property japanese the Japanese word of the entry, if any (the selection itself when Japanese).
 * @property meaningVi the Vietnamese meaning (the selection itself when Vietnamese), if any.
 */
data class BookmarkEntry(
    val sourceLanguage: Language,
    val sourceText: String,
    val key: String,
    val japanese: JapaneseText?,
    val meaningVi: String?,
    val translations: List<BookmarkTranslation>,
    val example: TranslationExample?,
) {
    /** The word as listed: the Japanese word when there is one, else the selected text. */
    val headword: String get() = japanese?.text ?: sourceText

    companion object {
        /** Senses kept in the saved Vietnamese meaning. */
        private const val SENSES = 3

        fun key(text: String, reading: String?): String =
            TranslationOrchestrator.comparable(text) + reading?.let { "|$it" }.orEmpty()

        /** The entry for [result]; null when it has nothing to save yet (no meaning, no translation). */
        fun from(result: TranslationResult): BookmarkEntry? {
            val source = result.sourceLanguage
            val text = TranslationOrchestrator.normalize(result.originalText)
            val readings = result.japaneseReading.valuesOrEmpty.map { it.value }.distinct()
            val japaneseValues = result.japaneseTranslation.valuesOrEmpty.map { it.value }
            val english = result.englishTranslation.valuesOrEmpty.map { it.value }
            val vietnamese = result.vietnameseMeaning.valuesOrEmpty.map { it.value }

            val japanese = when (source) {
                Language.JAPANESE -> JapaneseText(text, readings.singleOrNull())
                else -> japaneseValues.firstOrNull()
            }
            val meaning = when (source) {
                Language.VIETNAMESE -> text
                else -> vietnamese.take(SENSES).joinToString("; ").ifEmpty { null }
            }
            // An English selection listed under its Japanese word keeps the English word itself
            // (seen and searched); without a Japanese word it is the headword already.
            val sourceEnglish = if (source == Language.ENGLISH && japanese != null) listOf(BookmarkTranslation(Language.ENGLISH, text, null)) else emptyList()
            val translations = sourceEnglish + english.map { BookmarkTranslation(Language.ENGLISH, it, null) } +
                japaneseValues.map { BookmarkTranslation(Language.JAPANESE, it.text, it.reading) }
            if (translations.isEmpty() && (source == Language.VIETNAMESE || meaning == null)) return null
            return BookmarkEntry(
                sourceLanguage = source,
                sourceText = text,
                key = key(text, if (source == Language.JAPANESE) readings.singleOrNull() else null),
                japanese = japanese,
                meaningVi = meaning,
                translations = translations,
                example = result.examples.valuesOrEmpty.firstOrNull()?.value,
            )
        }
    }
}

/** What happened to a bookmark. */
sealed interface BookmarkResult {
    /** Saved as [vocabularyId]; [created] false when the entry existed and only got what it lacked. */
    data class Saved(val vocabularyId: String, val created: Boolean) : BookmarkResult

    /** Nothing written (storage error): the dialog says so, the entry is as before. */
    data object Failed : BookmarkResult
}
