package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.importing.ImportRecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The illustration pack shipped in assets must import cleanly and meet the lesson target. */
class BundledContentPackTest {

    // Gradle runs unit tests with the module directory as working directory.
    private val file = File("src/main/assets/${AndroidContentImportRepository.BUNDLED_PACK_ASSET}")

    private fun validate(): ContentPackValidator.Result {
        val parsed = ContentPackJsonParser.parse(file.readText())
        assertTrue("Bundled pack is not valid JSON: $parsed", parsed is ContentPackJsonParser.Result.Parsed)
        return ContentPackValidator(importedAt = 0).validate((parsed as ContentPackJsonParser.Result.Parsed).dto)
    }

    @Test
    fun importsWithoutErrorsOrWarnings() {
        val result = validate()
        assertEquals(emptyList<Any>(), result.report.issues)
        assertEquals(0, result.report.skippedRecords)
    }

    @Test
    fun lessonMeetsTarget_30vocabulary_27kanji_7grammar() {
        val counts = validate().report.counts
        assertEquals(1, counts.getValue(ImportRecordType.LESSON).valid)
        assertEquals(30, counts.getValue(ImportRecordType.VOCABULARY).valid)
        assertEquals(27, counts.getValue(ImportRecordType.KANJI).valid)
        assertEquals(7, counts.getValue(ImportRecordType.GRAMMAR).valid)
    }

    @Test
    fun isClearlyLabelledAsIllustrationWithUnverifiedLicense() {
        val bundle = validate().bundle!!
        val source = bundle.sources.single()
        assertTrue(source.name.contains("minh họa"))
        assertFalse(source.licenseVerified)
        assertTrue(bundle.lessons.all { it.title.contains("minh họa") })
        assertFalse(bundle.pack.isSeed)
    }

    @Test
    fun everyKanjiHasExamplesAndEveryItemHasJapaneseText() {
        val bundle = validate().bundle!!
        val kanjiWithExamples = bundle.kanjiExamples.map { it.kanjiId }.toSet()
        assertEquals(bundle.kanji.map { it.id }.toSet(), kanjiWithExamples)
        bundle.examples.forEach { assertEquals(it.japanese, Furigana.baseText(it.furigana!!)) }
        bundle.kanji.forEach { k ->
            val linked = bundle.kanjiExamples.filter { it.kanjiId == k.id }.map { ref -> bundle.examples.first { it.id == ref.exampleId } }
            assertTrue("${k.character} not in its example", linked.any { k.character in it.japanese })
        }
    }
}
