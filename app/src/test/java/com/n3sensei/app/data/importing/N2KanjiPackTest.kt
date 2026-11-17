package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.IssueCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled N2 kanji pack, built by tools/content/n2_kanji straight from the user's Anki deck
 * "N2::Kanji N2" and KANJIDIC2 (see docs/N2_KANJI.md).
 */
class N2KanjiPackTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()
    private fun validate(path: String) =
        ContentPackValidator(importedAt = 0).validate((ContentPackJsonParser.parse(asset(path)) as ContentPackJsonParser.Result.Parsed).dto)

    private val result by lazy { validate(AndroidContentImportRepository.N2_KANJI_PACK_ASSET) }
    private val bundle by lazy { result.bundle!! }
    private val n3 by lazy { validate(AndroidContentImportRepository.N3_KANJI_PACK_ASSET).bundle!! }

    private val raw by lazy { Json.parseToJsonElement(asset(AndroidContentImportRepository.N2_KANJI_PACK_ASSET)).jsonObject }
    private val rawExamples by lazy { raw["examples"]!!.jsonArray.map { it.jsonObject }.associateBy { it.text("id") } }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.flags() = (this["origin"]!!.jsonObject["flags"] as JsonArray).map { it.jsonPrimitive.content }

    private fun kanji(character: String) = bundle.kanji.single { it.character == character }
    private fun wordsOf(character: String) = bundle.kanjiExamples.filter { it.kanjiId == kanji(character).id }
        .sortedBy { it.sortOrder }.map { ref -> bundle.examples.single { it.id == ref.exampleId } }
    private fun hiragana(s: String) = s.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")

    @Test
    fun importsWithoutErrors_18Lessons_448Kanji_1811Words_noVocabulary() {
        val counts = result.report.counts
        assertEquals(18, counts.getValue(ImportRecordType.LESSON).valid)
        assertEquals(448, counts.getValue(ImportRecordType.KANJI).valid)
        assertEquals(1811, counts.getValue(ImportRecordType.EXAMPLE).valid)
        assertEquals(0, counts[ImportRecordType.VOCABULARY]?.valid ?: 0)
        assertTrue(bundle.vocabulary.isEmpty() && bundle.grammar.isEmpty())
        assertEquals(0, result.report.skippedRecords)
        assertEquals(setOf(IssueCode.LESSON_SIZE_DIFFERS), result.report.issues.map { it.code }.toSet())
    }

    @Test
    fun theLessonsAreLevelN2_inDeckOrder_withStableIdsApartFromN3() {
        assertEquals("n2-kanji", bundle.pack.id)
        assertTrue(bundle.lessons.all { it.jlptLevel == "N2" })
        assertEquals((1..18).map { "n2k-l%02d".format(it) }, bundle.lessons.sortedBy { it.number }.map { it.id })
        val sizes = bundle.lessons.sortedBy { it.number }.map { l -> bundle.kanji.count { it.lessonId == l.id } }
        assertEquals(listOf(23, 23, 23, 24, 24, 24, 23, 24, 24, 24, 32, 32, 29, 19, 24, 24, 24, 28), sizes)
        val first = bundle.kanji.filter { it.lessonId == "n2k-l01" }.sortedBy { it.sortOrder }.map { it.character }
        assertEquals("井", first.first())
        assertEquals("漂", first.last())
        assertTrue(bundle.kanji.all { it.id == "n2k-k%05x".format(it.character.codePointAt(0)) })
        assertTrue(bundle.examples.all { it.id.startsWith("n2k-x-") })
        // Nothing shares an id with the N3 pack.
        assertTrue((bundle.kanji.map { it.id } intersect n3.kanji.map { it.id }.toSet()).isEmpty())
        assertTrue((bundle.examples.map { it.id } intersect n3.examples.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun noKanjiTwice_andNoneAlreadyInN3() {
        assertEquals(448, bundle.kanji.map { it.character }.toSet().size)
        val n3Characters = n3.kanji.map { it.character }.toSet()
        assertTrue(bundle.kanji.none { it.character in n3Characters })
        // The 5 N3 kanji the deck repeats stay single N3 records.
        "祭調殺寝脂".forEach { c -> assertTrue("$c", c.toString() in n3Characters) }
    }

    @Test
    fun aKanjiHasTheDecksHanVietAndMeaning_KanjidicReadings_andItsWordsInOrder() {
        val i = kanji("井")
        assertEquals("TỈNH", i.hanViet)
        assertEquals("Cái giếng", i.meaningVi)
        assertEquals(listOf("セイ", "ショウ"), i.onReadings)
        assertEquals(listOf("い"), i.kunReadings)
        assertEquals(4, i.strokeCount)
        assertEquals(listOf("井戸", "井戸水", "天井"), wordsOf("井").map { it.japanese })
        assertEquals(listOf("{井戸|いど}", "{井戸水|いどみず}", "{天井|てんじょう}"), wordsOf("井").map { it.furigana })
        assertEquals(listOf("Cái giếng", "Nước giếng", "Trần nhà"), wordsOf("井").map { it.translationVi })
        // The deck's mnemonic is kept in the note.
        assertEquals("Nghĩa theo bộ thẻ Kanji N2. Ghi nhớ: Đang Tính rửa bát trong giếng", kanji("丼").note)
        // A one-kanji word card (丼 どんぶり) is a word of the kanji, not a second kanji.
        assertTrue(wordsOf("丼").any { it.japanese == "丼" && it.furigana == "{丼|どんぶり}" })
    }

    @Test
    fun readingsAndMeaningsOnlyInTheIllustration_wereTypedIn() {
        assertEquals("Khiêm tốn, nhún nhường", kanji("謙").meaningVi)
        assertEquals("Nghĩa theo ảnh minh họa của bộ thẻ Kanji N2.", kanji("謙").note)
        val fromImage = rawExamples.values.filter { "from-image" in it.flags() }
        assertEquals(49, fromImage.size)
        assertEquals(16, bundle.kanji.count { it.note!!.startsWith("Nghĩa theo ảnh") })
        val escape = bundle.examples.single { it.japanese == "逸脱する" }
        assertEquals("{逸脱|いつだつ}する", escape.furigana)
        assertEquals("Lệch hướng, thoát khỏi, trốn thoát", escape.translationVi)
    }

    @Test
    fun everyWordHasAMeaning_andRubyThatSpellsItsReading() {
        assertTrue(bundle.examples.all { it.translationVi.isNotBlank() })
        assertTrue(bundle.kanji.all { it.meaningVi.isNotBlank() && !it.hanViet.isNullOrBlank() })
        bundle.examples.forEach { e ->
            val ruby = e.furigana
            if (Regex("\\p{IsHan}").containsMatchIn(e.japanese)) assertTrue(e.japanese, ruby != null)
            if (ruby != null) {
                assertTrue(ruby, Furigana.isValid(ruby))
                assertEquals(e.japanese, Furigana.baseText(ruby))
                val raw = rawExamples.getValue(e.id)
                val reading = raw["origin"]!!.jsonObject.text("reading")
                // A word with two readings (被る: こうむる/かぶる) has ruby of the first; both are in its meaning.
                val expected = if ("several-readings" in raw.flags()) reading.substringBefore("/") else reading
                assertEquals(e.japanese, hiragana(expected).filterNot { it == ' ' }, hiragana(Furigana.readingText(ruby)).filterNot { it == ' ' })
            }
        }
        // Typos of the deck were fixed, each one flagged.
        assertEquals("{誘|さそ}いを{断|ことわ}る", bundle.examples.single { it.japanese == "誘いを断る" }.furigana)
        assertFalse(bundle.examples.any { it.japanese == "祝いを断る" })
        assertEquals(13, rawExamples.values.count { "fixed" in it.flags() })
        assertEquals("Gánh chịu (thiệt hại) / Đội (mũ) (đọc: こうむる / かぶる)", bundle.examples.single { it.japanese == "被る" }.translationVi)
        assertEquals("{抱|だ}いた ({抱|だ}く)", bundle.examples.single { it.japanese == "抱いた (抱く)" }.furigana)
    }
}
