package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.IssueCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled N3 grammar pack (built by tools/content/n3_grammar from the VNJPCLUB index
 * metadata and self-authored content, see docs/N3_GRAMMAR.md).
 */
class N3GrammarPackTest {

    private val file = File("src/main/assets/${AndroidContentImportRepository.N3_GRAMMAR_PACK_ASSET}")
    private val result by lazy {
        ContentPackValidator(importedAt = 0).validate((ContentPackJsonParser.parse(file.readText()) as ContentPackJsonParser.Result.Parsed).dto)
    }
    private val bundle by lazy { result.bundle!! }
    private fun grammar(n: Int) = bundle.grammar.single { it.id == "n3g-g%03d".format(n) }
    private fun examplesOf(n: Int) = bundle.grammarExamples.filter { it.grammarId == "n3g-g%03d".format(n) }
        .sortedBy { it.sortOrder }.map { ref -> bundle.examples.single { it.id == ref.exampleId } }

    @Test
    fun all110Patterns_in11Lessons_withTwoExamplesEach_importWithoutErrors() {
        val counts = result.report.counts
        assertEquals(11, counts.getValue(ImportRecordType.LESSON).valid)
        assertEquals(110, counts.getValue(ImportRecordType.GRAMMAR).valid)
        assertEquals(220, counts.getValue(ImportRecordType.EXAMPLE).valid)
        assertEquals(0, result.report.skippedRecords)
        assertEquals(setOf(IssueCode.LESSON_SIZE_DIFFERS), result.report.issues.map { it.code }.toSet())
        assertTrue((1..110).all { examplesOf(it).size == 2 })
    }

    @Test
    fun orderFollowsTheSourceIndex_tenPerLesson() {
        assertEquals((1..11).toList(), bundle.lessons.map { it.number })
        assertEquals("Ngữ pháp N3 · mục 1–10", bundle.lessons.first().title)
        assertEquals("Ngữ pháp N3 · mục 101–110", bundle.lessons.last().title)
        val first = bundle.grammar.filter { it.lessonId == "n3g-l01" }.sortedBy { it.sortOrder }
        assertEquals("〜ことにしている", first.first().pattern)
        assertEquals("〜おそれがある", grammar(110).pattern)
    }

    @Test
    fun samePatternNameInTheSource_isKeptAsTwoUses() {
        assertTrue(grammar(25).pattern.startsWith("〜に対し（て）") && grammar(34).pattern.startsWith("〜に対し（て）"))
        assertTrue(grammar(25).meaningVi != grammar(34).meaningVi)
        assertTrue(grammar(61).pattern.startsWith("〜ほど") && grammar(63).pattern.startsWith("〜ほど"))
    }

    @Test
    fun likelyTyposInTheSourceIndex_areFlaggedInTheNote() {
        assertEquals("〜っぽい", grammar(73).pattern)
        assertTrue(grammar(73).cautionNote!!.contains("〜っぱい"))
        assertEquals("〜に反し（て）", grammar(92).pattern)
        assertTrue(grammar(92).cautionNote!!.contains("〜に返し（て）"))
    }

    @Test
    fun japaneseAndVietnameseTextIsIntact() {
        assertEquals("(tôi) có thói quen/nguyên tắc là…", grammar(1).meaningVi)
        assertEquals("健康のために、毎朝三十分歩くことにしている。", examplesOf(1)[0].japanese)
        assertEquals("Vì sức khỏe, tôi có thói quen đi bộ ba mươi phút mỗi sáng.", examplesOf(1)[0].translationVi)
        assertTrue(bundle.examples.all { it.furigana == null })
    }

    @Test
    fun everyItemNamesItsSourcePage_andIsLabelledSelfAuthored() {
        assertEquals(111, bundle.sources.size) // one per pattern + the pack default
        bundle.grammar.forEach { g ->
            val source = bundle.sources.single { it.id == g.sourceId }
            assertTrue(source.url!!.startsWith("https://www.vnjpclub.com/mimi-kara-n3-bunpo/np-"))
            assertTrue(source.name.startsWith("N3 Sensei tự soạn"))
            assertFalse(source.licenseVerified)
        }
        assertEquals("https://www.vnjpclub.com/mimi-kara-n3-bunpo/np-1.html", bundle.sources.single { it.id == grammar(1).sourceId }.url)
    }
}
