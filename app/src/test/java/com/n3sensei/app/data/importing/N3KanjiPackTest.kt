package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.furigana.Furigana
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled N3 kanji pack, built by tools/content/n3_kanji from the kanji part of the
 * user's N3.pdf and KANJIDIC2 (see docs/N3_KANJI.md).
 */
class N3KanjiPackTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()
    private fun validate(path: String) =
        ContentPackValidator(importedAt = 0).validate((ContentPackJsonParser.parse(asset(path)) as ContentPackJsonParser.Result.Parsed).dto)

    private val result by lazy { validate(AndroidContentImportRepository.N3_KANJI_PACK_ASSET) }
    private val bundle by lazy { result.bundle!! }

    /** The pack file itself, for the traceability fields the app does not read. */
    private val raw by lazy { Json.parseToJsonElement(asset(AndroidContentImportRepository.N3_KANJI_PACK_ASSET)).jsonObject }
    private val rawKanji by lazy { raw["lessons"]!!.jsonArray.flatMap { it.jsonObject["kanji"]!!.jsonArray.map { k -> k.jsonObject } } }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.origin() = this["origin"]!!.jsonObject

    private fun kanji(character: String) = bundle.kanji.single { it.character == character }
    private fun wordsOf(character: String) = bundle.kanjiExamples.filter { it.kanjiId == kanji(character).id }
        .sortedBy { it.sortOrder }.map { ref -> bundle.examples.single { it.id == ref.exampleId } }
    private fun hiragana(s: String) = s.map { if (it in '\u30A1'..'\u30F6') it - 0x60 else it }.joinToString("")
    private val rawExamples by lazy { raw["examples"]!!.jsonArray.map { it.jsonObject }.associateBy { it.text("id") } }

    @Test
    fun importsWithoutErrors_392Kanji_with1272ExampleWords_andNoVocabulary() {
        val counts = result.report.counts
        assertEquals(16, counts.getValue(ImportRecordType.LESSON).valid)
        assertEquals(392, counts.getValue(ImportRecordType.KANJI).valid)
        assertEquals(1272, counts.getValue(ImportRecordType.EXAMPLE).valid)
        // The kanji's words are not vocabulary items: nothing goes to the vocabulary domain.
        assertEquals(0, counts[ImportRecordType.VOCABULARY]?.valid ?: 0)
        assertTrue(bundle.vocabulary.isEmpty())
        assertEquals(0, result.report.skippedRecords)
        assertEquals(setOf(IssueCode.LESSON_SIZE_DIFFERS), result.report.issues.map { it.code }.toSet())
    }

    @Test
    fun eachKanjiIsOneUnit_andItsWordsAreNotCountedAsKanji() {
        assertEquals(392, bundle.kanji.map { it.character }.toSet().size)
        assertTrue(bundle.kanji.all { it.character.codePointCount(0, it.character.length) == 1 })
        // A one-kanji word (波 なみ) is an example word of the kanji 波, not a second kanji record.
        assertEquals(listOf("{波|なみ}"), wordsOf("波").filter { it.japanese == "波" }.map { it.furigana })
    }

    @Test
    fun lessonsKeepThePdfOrder_24To27KanjiEach_noGroupSplit() {
        val sizes = bundle.lessons.sortedBy { it.number }.map { l -> bundle.kanji.count { it.lessonId == l.id } }
        // Decks 第01–第10 as in the PDF (第01 has 27 with 狼), then 第11–第18 re-cut in order.
        assertEquals(listOf(27, 26, 27, 24, 24, 24, 24, 24, 24, 23, 25, 24, 24, 24, 24, 24), sizes)
        val order = rawKanji.map { it.origin().text("deck") to it.origin()["card"]!!.jsonPrimitive.int }
        assertEquals(order.sortedWith(compareBy({ it.first }, { it.second })), order)
        val first = bundle.kanji.filter { it.lessonId == "n3k-l01" }.sortedBy { it.sortOrder }.map { it.character }
        assertEquals("意", first.first())
        assertEquals("距", first.last())
    }

    @Test
    fun eachKanjiHasItsPdfGroupWords_inOrder_withReadingsAsFurigana() {
        assertEquals(listOf("注意", "意味", "決意"), wordsOf("意").map { it.japanese })
        assertEquals(listOf("Chú ý", "Ý nghĩa", "Quyết tâm"), wordsOf("意").map { it.translationVi })
        assertEquals("{吸|す}い{取|と}る", bundle.examples.single { it.japanese == "吸い取る" }.furigana)
        // Every word card is attached to exactly the kanji card it follows in the PDF.
        bundle.kanjiExamples.forEach { ref ->
            val origin = rawExamples.getValue(ref.exampleId).origin()
            assertEquals(ref.exampleId, origin.text("kanji"), bundle.kanji.single { it.id == ref.kanjiId }.character)
        }
        assertEquals(1272, bundle.kanjiExamples.map { it.exampleId }.toSet().size)
        bundle.examples.forEach { e ->
            val reading = rawExamples.getValue(e.id).origin().text("reading")
            // Kana written in katakana (ゴミ箱) stays katakana in the ruby text.
            if (e.furigana != null) assertEquals(e.japanese, hiragana(reading), hiragana(Furigana.readingText(e.furigana!!)))
        }
        // お仕事 is read しごと in the PDF: no furigana, flagged for review.
        assertEquals(listOf("お仕事"), bundle.examples.filter { it.furigana == null && Regex("\\p{IsHan}").containsMatchIn(it.japanese) }.map { it.japanese })
    }

    @Test
    fun aWordMayAlsoBeAVocabularyItem_asTwoIndependentRecords() {
        val mimikara = validate(AndroidContentImportRepository.MIMIKARA_PACK_ASSET).bundle!!
        val skin = mimikara.vocabulary.single { it.expression == "皮" && it.reading == "かわ" }
        assertTrue(wordsOf("皮").any { it.japanese == "皮" && it.furigana == "{皮|かわ}" })
        // Distinct ids, nothing in the kanji pack points at the vocabulary record.
        assertTrue(bundle.examples.none { it.id == skin.id })
        assertTrue(bundle.kanjiExamples.all { ref -> bundle.examples.any { it.id == ref.exampleId } })
    }

    @Test
    fun wordsUnderTheWrongKanjiInThePdf_areFlagged_andLeftWhereThePdfPutsThem() {
        val flagged = rawExamples.values.filter { e ->
            (e.origin()["flags"] as JsonArray).any { it.jsonPrimitive.content == "word-without-its-kanji" }
        }.map { it.origin().text("kanji") + ":" + it.text("japanese") }
        assertEquals(
            listOf("伸:身長", "坂:木の板", "坂:黒板", "坂:板書", "坂:板前", "坂:看板", "州:連合"),
            flagged,
        )
        assertTrue(wordsOf("坂").map { it.japanese }.containsAll(listOf("黒板", "看板")))
    }

    @Test
    fun everyMeaningSaysWhereItComesFrom() {
        bundle.kanji.forEach { k ->
            assertTrue(k.character, k.meaningVi.isNotBlank() && !k.hanViet.isNullOrBlank())
            assertTrue(k.character, k.note == "Nghĩa theo N3.pdf." || k.note!!.startsWith("Nghĩa dịch từ KANJIDIC2 (tiếng Anh: "))
            assertTrue(k.character, k.onReadings.isNotEmpty() || k.kunReadings.isNotEmpty())
            assertTrue(k.character, k.strokeCount != null)
        }
        val statuses = rawKanji.groupingBy { it.origin()["meaning"]!!.jsonObject.text("status") }.eachCount()
        assertEquals(273 + 14, statuses.filterKeys { it.startsWith("kanjidic2-translated") }.values.sum())
        assertEquals(103 + 2, statuses.filterKeys { it.startsWith("pdf") }.values.sum())
        // A compound's meaning in the PDF is not taken as the kanji's meaning.
        assertEquals("Truyền, truyền đạt", kanji("伝").meaningVi)
        assertEquals("Sóng", kanji("波").meaningVi)
        assertEquals("BA", kanji("波").hanViet)
        assertEquals(listOf("ハ"), kanji("波").onReadings)
        assertEquals(8, kanji("波").strokeCount)
        val source = bundle.sources.single { it.id == kanji("波").sourceId }
        assertFalse(source.licenseVerified)
        assertTrue(source.attribution!!.contains("Electronic Dictionary Research and Development Group"))
    }

    @Test
    fun japaneseText_isTheJapaneseCodePoint_notARadicalOrAChineseForm() {
        val radicals = Regex("[⺀-⿟]")
        bundle.kanji.forEach { assertFalse(it.character, radicals.containsMatchIn(it.character)) }
        bundle.examples.forEach { assertFalse(it.japanese, radicals.containsMatchIn(it.japanese)) }
        val all = bundle.kanji.map { it.character }.toSet()
        assertTrue(all.containsAll(listOf("戸", "賛", "労")))
        assertTrue(all.none { it in setOf("戶", "贊", "劳") })
        // ⻭ (CJK Radicals Supplement) in the PDF text is the kanji 歯.
        assertEquals("{虫歯|むしば}", bundle.examples.single { it.japanese == "虫歯" }.furigana)
        assertEquals("Ý", kanji("意").hanViet)
    }

    @Test
    fun noMnemonicOrExampleSentenceIsBundled() {
        (bundle.kanji.map { it.meaningVi } + bundle.examples.map { it.translationVi }).forEach { m ->
            assertFalse(m, m.contains("Mẹo") || m.startsWith("#") || m.contains('"') || m.contains("→"))
        }
        // The examples are the group's words, not the cards' sentences (no 。).
        assertTrue(bundle.examples.none { it.japanese.endsWith("。") })
    }
}
