package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.furigana.Furigana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The examples added to the bundled packs (tools/content/examples, docs/EXAMPLES.md): only items
 * that had none got some, each example belongs to one item and shows it, no sentence is
 * repeated, and the readings of the word match the item.
 */
class BundledExamplesTest {

    private fun validate(asset: String): ContentPackValidator.Result {
        val parsed = ContentPackJsonParser.parse(File("src/main/assets/$asset").readText()) as ContentPackJsonParser.Result.Parsed
        return ContentPackValidator(importedAt = 0).validate(parsed.dto)
    }

    private val vocabulary by lazy { validate(AndroidContentImportRepository.MIMIKARA_PACK_ASSET).bundle!! }
    private val grammar by lazy { validate(AndroidContentImportRepository.N3_GRAMMAR_PACK_ASSET).bundle!! }
    private val kanji by lazy { validate(AndroidContentImportRepository.N3_KANJI_PACK_ASSET).bundle!! }

    /** Words whose sheet entry is contradictory (docs/EXAMPLES.md): left without an example. */
    private val toReview = listOf(358, 360, 419, 445, 580, 598, 657, 664, 682, 739).map { "mimikara-n3-v%03d".format(it) }.toSet()

    @Test
    fun everyWordHasOneExample_exceptTheOnesToReview() {
        val withExamples = vocabulary.vocabularyExamples.groupBy { it.vocabularyId }
        assertEquals(870, withExamples.size)
        assertEquals(toReview, vocabulary.vocabulary.map { it.id }.toSet() - withExamples.keys)
        assertTrue(withExamples.values.all { it.size == 1 })
        // Example ids follow their word, so a later run never makes a second copy.
        vocabulary.vocabularyExamples.forEach { assertEquals("${it.vocabularyId}-ex1", it.exampleId) }
    }

    @Test
    fun eachExample_belongsToOneItem_andIsNotRepeated() {
        listOf(vocabulary, grammar).forEach { b ->
            val refs = b.vocabularyExamples.map { it.exampleId } + b.grammarExamples.map { it.exampleId }
            assertEquals(refs.size, refs.toSet().size)
            assertEquals(b.examples.map { it.id }.toSet(), refs.toSet())
            assertEquals(b.examples.size, b.examples.map { it.japanese }.toSet().size)
        }
    }

    @Test
    fun everyVocabularyExample_containsItsWord_withTheWordsReading() {
        val fixedReadings = mapOf("mimikara-n3-v065" to "めいわく", "mimikara-n3-v178" to "すれちがう", "mimikara-n3-v343" to "ほんやく")
        vocabulary.vocabularyExamples.forEach { ref ->
            val word = vocabulary.vocabulary.single { it.id == ref.vocabularyId }
            val example = vocabulary.examples.single { it.id == ref.exampleId }
            val range = ExampleHighlight.ofVocabulary(example.japanese, word.expression, word.reading).singleOrNull()
            assertTrue("${word.expression}: ${example.japanese}", range != null)
            assertTrue(example.japanese, example.japanese.endsWith("。") || example.japanese.endsWith("」") || example.japanese.endsWith("？"))
            // The ruby over the word's kanji is read as the word is (or as its fixed reading).
            val furigana = example.furigana ?: return@forEach
            var offset = 0
            val reading = StringBuilder()
            Furigana.parse(furigana).forEach { segment ->
                val end = offset + segment.text.length
                if (offset <= range!!.last && range.first < end) {
                    // A ruby group counts whole; plain text only where it overlaps the word.
                    reading.append(segment.reading ?: segment.text.substring(maxOf(0, range.first - offset), minOf(segment.text.length, range.last + 1 - offset)))
                }
                offset = end
            }
            val expected = (fixedReadings[word.id] ?: word.reading).split("・")
            assertTrue("${word.expression} read ${reading}: $furigana", expected.any { r -> hiragana(r).startsWith(hiragana(reading.toString()).take(1)) && sharesKanaPrefix(r, reading.toString()) })
        }
    }

    @Test
    fun grammarExamples_areOnlyAddedToPointsWithout_andShowThePattern() {
        val added = grammar.examples.filter { it.sourceId == "n3-grammar-examples" }.map { it.id }.toSet()
        val byPoint = grammar.grammarExamples.groupBy { it.grammarId }
        // The 110 points kept exactly their two examples; the 64 got 2–3.
        assertTrue(byPoint.filterKeys { it <= "n3g-g110" }.values.all { refs -> refs.size == 2 && refs.none { it.exampleId in added } })
        assertTrue(byPoint.filterKeys { it > "n3g-g110" }.values.all { refs -> refs.size in 2..3 && refs.all { it.exampleId in added } })
        assertEquals(174, byPoint.size)
        val missing = grammar.grammarExamples.filter { it.exampleId in added }.filter { ref ->
            val pattern = grammar.grammar.single { it.id == ref.grammarId }.pattern
            ExampleHighlight.ofGrammar(grammar.examples.single { it.id == ref.exampleId }.japanese, pattern).isEmpty()
        }
        assertEquals(emptyList<Any>(), missing.map { it.exampleId })
    }

    @Test
    fun kanjiWords_containTheirKanji() {
        kanji.kanjiExamples.forEach { ref ->
            val k = kanji.kanji.single { it.id == ref.kanjiId }.character
            assertTrue(ExampleHighlight.ofKanji(kanji.examples.single { it.id == ref.exampleId }.japanese, k).isNotEmpty())
        }
        assertEquals(kanji.kanji.map { it.id }.toSet(), kanji.kanjiExamples.map { it.kanjiId }.toSet())
    }

    private fun hiragana(s: String) = s.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")

    /** The reading of the word's span (stem or whole word) is the start of the word's reading. */
    private fun sharesKanaPrefix(wordReading: String, spanReading: String): Boolean {
        val w = hiragana(wordReading)
        val s = hiragana(spanReading)
        // A conjugated stem drops the end (働く → はたら), a kana-only span is the word itself.
        return w.startsWith(s) || s.startsWith(w.dropLast(1)) || w.startsWith(s.dropLast(1))
    }
}
