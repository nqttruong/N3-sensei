package com.n3sensei.app.data.translate

import com.n3sensei.app.data.local.SearchText
import com.n3sensei.app.data.local.dao.ContentDao
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.translate.DictionaryEntry
import com.n3sensei.app.domain.translate.DictionaryProvider
import com.n3sensei.app.domain.translate.TranslationExample
import com.n3sensei.app.domain.translate.TranslationOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * N3 Sensei's own content as a Japanese–Vietnamese dictionary: vocabulary of every pack and the
 * learner's own words ("Từ của tôi"), kanji, and the kanji's example words. Exact matches only:
 * SQL narrows the candidates, then the whole text must equal a form, a reading or one sense of
 * the meaning. It has no English, so English lookups find nothing.
 */
class ContentDictionaryProvider(private val dao: ContentDao) : DictionaryProvider {

    override suspend fun lookupJapanese(text: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        val words = dao.vocabularyByJapanese(text).mapNotNull { v -> readingOfForm(v, text)?.let { v to it } }
            .map { (v, reading) -> v.toEntry(reading.ifEmpty { null }) }
        val kanji = if (text.codePointCount(0, text.length) == 1) dao.kanjiByCharacter(text).map { it.toEntry() } else emptyList()
        val kanjiWords = dao.kanjiWords().filter { it.japanese == text }.map { it.toWordEntry() }
        words + kanji + kanjiWords
    }

    override suspend fun lookupVietnamese(text: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        val wanted = TranslationOrchestrator.comparable(text)
        if (wanted.isEmpty()) return@withContext emptyList()
        val words = dao.vocabularyContaining(escapeLike(SearchText.normalize(wanted)))
            .filter { wanted in senses(it.meaningVi) }
            .map { v -> v.toEntry(v.reading.split('・').first().ifEmpty { null }) }
        val kanjiWords = dao.kanjiWords().filter { wanted in senses(it.translationVi) }.map { it.toWordEntry() }
        words + kanjiWords
    }

    override suspend fun lookupEnglish(text: String): List<DictionaryEntry> = emptyList()

    override suspend fun version(): String = withContext(Dispatchers.IO) { dao.dictionaryVersion() }

    /**
     * The reading of the form of [v] that is exactly [text] (forms and readings `・`-separated,
     * paired when they have as many), "" for a word without a reading; null when no form or
     * reading is exactly [text] (a partial hit of the SQL candidates).
     */
    private fun readingOfForm(v: VocabularyEntity, text: String): String? {
        val forms = v.expression.split('・')
        val readings = v.reading.split('・')
        val form = forms.indexOf(text)
        return when {
            form >= 0 -> if (readings.size == forms.size) readings[form] else readings.first()
            text in readings -> text
            else -> null
        }
    }

    private suspend fun VocabularyEntity.toEntry(reading: String?) = DictionaryEntry(
        japanese = expression,
        reading = reading,
        meaningVi = meaningVi,
        notes = listOfNotNull(partOfSpeech, note).filter { it.isNotBlank() },
        examples = dao.getVocabularyExamples(id).map { it.toExample() },
        sourceName = dao.getSource(sourceId)?.name,
    )

    private suspend fun KanjiEntity.toEntry() = DictionaryEntry(
        japanese = character,
        // On/kun readings are the kanji's, not a word's reading: given as notes.
        reading = null,
        meaningVi = meaningVi,
        notes = listOfNotNull(
            hanViet?.let { "Hán Việt: $it" },
            onReadings.takeIf { it.isNotEmpty() }?.let { "On: ${it.joinToString("、")}" },
            kunReadings.takeIf { it.isNotEmpty() }?.let { "Kun: ${it.joinToString("、")}" },
        ),
        examples = dao.getKanjiExamples(id).map { it.toExample() },
        sourceName = dao.getSource(sourceId)?.name,
    )

    /** A kanji's example word: written form, reading (from its furigana) and meaning. */
    private suspend fun ExampleSentenceEntity.toWordEntry() = DictionaryEntry(
        japanese = japanese,
        reading = furigana?.let { runCatching { Furigana.readingText(it) }.getOrNull() },
        meaningVi = translationVi,
        sourceName = dao.getSource(sourceId)?.name,
    )

    private fun ExampleSentenceEntity.toExample() = TranslationExample(japanese, furigana, translationVi)

    companion object {
        /**
         * The senses of a Vietnamese meaning, compared like [TranslationOrchestrator.comparable]:
         * "khủng khiếp, rất nhiều" → both, each also without a part in brackets.
         */
        fun senses(meaning: String): Set<String> {
            val parts = meaning.split(',', ';', '/', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            return (parts + parts.map { it.replace(BRACKETED, " ") } + meaning)
                .map { TranslationOrchestrator.comparable(it) }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        private val BRACKETED = Regex("\\([^)]*\\)|（[^）]*）")

        private fun escapeLike(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
