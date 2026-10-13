package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.IssueCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Mimikara N3 vocabulary pack shipped in assets (built by tools/content/ from the shared
 * sheet, see docs/CONTENT_FORMAT.md): every row of the sheet arrives, text unchanged apart
 * from the documented corrections, and nothing is invented.
 */
class MimikaraPackTest {

    private val file = File("src/main/assets/${AndroidContentImportRepository.MIMIKARA_PACK_ASSET}")

    private val result: ContentPackValidator.Result by lazy {
        val parsed = ContentPackJsonParser.parse(file.readText()) as ContentPackJsonParser.Result.Parsed
        ContentPackValidator(importedAt = 0).validate(parsed.dto)
    }
    private val vocabulary by lazy { result.bundle!!.vocabulary }
    private fun word(stt: Int) = vocabulary.single { it.id == "mimikara-n3-v%03d".format(stt) }

    @Test
    fun all880WordsIn12Lessons_importWithoutErrors() {
        val counts = result.report.counts
        assertEquals(12, counts.getValue(ImportRecordType.LESSON).valid)
        assertEquals(880, counts.getValue(ImportRecordType.VOCABULARY).valid)
        assertEquals(0, result.report.skippedRecords)
        // Only the "lesson size differs from the illustration target" notices remain.
        assertEquals(setOf(IssueCode.LESSON_SIZE_DIFFERS), result.report.issues.map { it.code }.toSet())
        assertEquals((1..880).map { "mimikara-n3-v%03d".format(it) }.toSet(), vocabulary.map { it.id }.toSet())
    }

    @Test
    fun lessonsKeepTheSheetOrderAndSizes() {
        val lessons = result.bundle!!.lessons
        assertEquals((1..12).toList(), lessons.map { it.number })
        assertEquals("Mimikara N3 · 名詞", lessons[0].title)
        val sizes = lessons.map { l -> vocabulary.count { it.lessonId == l.id } }
        assertEquals(listOf(120, 100, 78, 112, 100, 40, 40, 45, 80, 80, 50, 35), sizes)
        assertEquals(880, sizes.sum())
        // Order inside a lesson follows the sheet's numbering.
        assertEquals(listOf("男性", "女性", "高齢"), vocabulary.filter { it.lessonId == "mimikara-n3-l01" }.sortedBy { it.sortOrder }.take(3).map { it.expression })
    }

    @Test
    fun textIsKeptAsInTheSheet() {
        with(word(1)) { assertEquals(listOf("男性", "だんせい", "đàn ông"), listOf(expression, reading, meaningVi)) }
        with(word(437)) { assertEquals(listOf("代わる・替わる・換わる", "かわる"), listOf(expression, reading)) }
        with(word(592)) { assertEquals("khủng khiếp, rất nhiều", meaningVi) }
        // Nothing invented: no furigana, part of speech or examples in the sheet.
        assertTrue(vocabulary.all { it.furigana == null && it.partOfSpeech == null })
        assertTrue(result.bundle!!.vocabularyExamples.isEmpty())
    }

    @Test
    fun documentedCorrectionsAndDecisions() {
        assertEquals("さくしゃ", word(90).reading) // sheet: 作者
        assertEquals("すませる・すます", word(750).reading) // sheet: すませる・済ます
        assertEquals("パートナー", word(796).expression) // sheet: パートナ
        assertEquals(word(627).expression, word(627).reading) // kana-only word, reading cell empty
        // Lesson 6 has no reading column: the katakana word is its reading.
        assertTrue(vocabulary.filter { it.lessonId == "mimikara-n3-l06" }.all { it.reading == it.expression })
        // しっかり: two senses in two lessons, both kept.
        assertEquals(listOf("toàn bộ", "kiên quyết, chắc chắn, đáng tin cậy"), listOf(word(600).meaningVi, word(848).meaningVi))
    }

    @Test
    fun sourceIsNamed_andLicenseIsNotClaimed() {
        val source = result.bundle!!.sources.single()
        assertTrue(source.name.contains("Mimikara"))
        assertFalse(source.licenseVerified)
        assertNull(source.url)
        assertTrue(vocabulary.all { it.jlptLevel == "N3" })
    }
}
