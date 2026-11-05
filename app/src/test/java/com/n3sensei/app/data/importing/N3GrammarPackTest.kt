package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.IssueCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled N3 grammar pack (built by tools/content/n3_grammar from the VNJPCLUB index
 * metadata and self-authored content, then the points of the user's N3.pdf the 110 lack;
 * see docs/N3_GRAMMAR.md).
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
    fun the110Patterns_plus64FromThePdf_in25Lessons_importWithoutErrors() {
        val counts = result.report.counts
        assertEquals(25, counts.getValue(ImportRecordType.LESSON).valid)
        assertEquals(110 + 64, counts.getValue(ImportRecordType.GRAMMAR).valid)
        assertEquals(220 + 140, counts.getValue(ImportRecordType.EXAMPLE).valid)
        assertEquals(0, result.report.skippedRecords)
        assertEquals(setOf(IssueCode.LESSON_SIZE_DIFFERS), result.report.issues.map { it.code }.toSet())
        assertTrue((1..110).all { examplesOf(it).size == 2 })
        // The PDF points bring no examples (the cards' sentences are not bundled): each has 2–3
        // self-written ones, added later under their own source; the 110 got none added.
        assertTrue((111..174).all { examplesOf(it).size in 2..3 && examplesOf(it).all { e -> e.sourceId == EXAMPLES_SOURCE } })
        assertTrue((1..110).all { examplesOf(it).none { e -> e.sourceId == EXAMPLES_SOURCE } })
    }

    @Test
    fun sevenPerLesson_lastLessonHoldsTheRest_inListOrder() {
        assertEquals((1..25).toList(), bundle.lessons.map { it.number })
        val sizes = bundle.lessons.map { lesson -> bundle.grammar.count { it.lessonId == lesson.id } }
        assertEquals(List(24) { 7 } + 6, sizes)
        assertEquals("Ngữ pháp N3 · mục 1–7", bundle.lessons.first().title)
        assertEquals("Ngữ pháp N3 · mục 169–174", bundle.lessons.last().title)
        val ordered = bundle.lessons.flatMap { lesson -> bundle.grammar.filter { it.lessonId == lesson.id }.sortedBy { it.sortOrder } }
        assertEquals((1..174).map { "n3g-g%03d".format(it) }, ordered.map { it.id })
    }

    @Test
    fun the110KeepTheirIds_soProgressStillMatches() {
        // Same id → same point as in version 1 of the pack.
        assertEquals("〜ことにしている", grammar(1).pattern)
        assertEquals("〜に対し（て）", grammar(25).pattern)
        assertEquals("〜わりに（は）", grammar(104).pattern)
        assertEquals("〜おそれがある", grammar(110).pattern)
        assertEquals("〜ため（に）", grammar(111).pattern)
        assertEquals("〜ことがある", grammar(174).pattern)
    }

    @Test
    fun aPdfPointWithTheFormOfAnExistingOne_isToldApart() {
        assertEquals("〜とか", grammar(99).pattern)
        val listing = bundle.grammar.single { it.pattern == "〜とか〜とか" }
        assertTrue(listing.cautionNote!!.contains("〜とか (nghe nói là…)"))
        assertTrue(bundle.grammar.single { it.pattern == "〜たところだ" }.cautionNote!!.contains("〜たところ (thử… thì…)"))
        // Hearsay and appearance そうだ are two points with distinct names.
        assertTrue(bundle.grammar.any { it.pattern == "〜そうだ（伝聞）" } && bundle.grammar.any { it.pattern == "〜そうだ（様態）" })
        val names = bundle.grammar.map { it.pattern }
        assertEquals(setOf("〜に対し（て）"), names.groupBy { it }.filterValues { it.size > 1 }.keys)
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
        // The first 220 examples have no furigana; the added ones do, matching their sentence.
        assertTrue(bundle.examples.filter { it.sourceId != EXAMPLES_SOURCE }.all { it.furigana == null })
        bundle.examples.filter { it.sourceId == EXAMPLES_SOURCE }.forEach { e ->
            // Only a sentence without kanji has no furigana.
            if (e.furigana == null) assertTrue(e.japanese, Regex("\\p{IsHan}").containsMatchIn(e.japanese).not())
            else assertEquals(e.japanese, Furigana.baseText(e.furigana!!))
        }
        assertEquals("{大雨|おおあめ}のため、{電車|でんしゃ}が{止|と}まっています。", examplesOf(111)[0].furigana)
    }

    @Test
    fun everyItemNamesItsSource_selfAuthoredOrPdfCard() {
        assertEquals(1 + 110 + 64 + 1, bundle.sources.size) // pack default + one per pattern + added examples
        with(bundle.sources.single { it.id == EXAMPLES_SOURCE }) {
            assertTrue(name.startsWith("N3 Sensei tự soạn"))
            assertFalse(licenseVerified)
        }
        bundle.grammar.filter { it.id <= "n3g-g110" }.forEach { g ->
            val source = bundle.sources.single { it.id == g.sourceId }
            assertTrue(source.url!!.startsWith("https://www.vnjpclub.com/mimi-kara-n3-bunpo/np-"))
            assertTrue(source.name.startsWith("N3 Sensei tự soạn"))
            assertFalse(source.licenseVerified)
        }
        assertEquals("https://www.vnjpclub.com/mimi-kara-n3-bunpo/np-1.html", bundle.sources.single { it.id == grammar(1).sourceId }.url)
        bundle.grammar.filter { it.id > "n3g-g110" }.forEach { g ->
            val source = bundle.sources.single { it.id == g.sourceId }
            assertTrue(source.name, source.name.startsWith("N3.pdf · Ngữ pháp, nhóm "))
            // N3.pdf is the project owner's document.
            assertTrue(source.licenseVerified)
        }
        assertEquals("N3.pdf · Ngữ pháp, nhóm 01 thẻ 1 (tên mẫu, cách chia, nghĩa ngắn)", bundle.sources.single { it.id == grammar(111).sourceId }.name)
    }

    private companion object {
        const val EXAMPLES_SOURCE = "n3-grammar-examples"
    }
}
