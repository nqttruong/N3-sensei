package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The bundled N3 kanji pack (393 kanji from N3.pdf, each with its PDF group words as kanji
 * examples) in a real Room database file, next to the Mimikara vocabulary pack.
 */
@RunWith(AndroidJUnit4::class)
class N3KanjiImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "n3-kanji-import-test.db"
    private fun asset(path: String) = context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    private val json = asset(AndroidContentImportRepository.N3_KANJI_PACK_ASSET)
    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private val content get() = OfflineContentRepository(db.contentDao(), db.contentPackDao())

    private fun open() {
        db = N3Database.create(context, name)
        importer = ContentPackImporter(db.contentPackDao(), Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC))
    }

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        open()
        // Installed as on a first launch: the vocabulary pack first.
        assertEquals(ImportOutcome.IMPORTED, runBlocking { importer.import(asset(AndroidContentImportRepository.MIMIKARA_PACK_ASSET)) }.outcome)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun kanjiId(character: String) = "n3k-k%05x".format(character.codePointAt(0))
    private fun bumped(version: Int) = json.replaceFirst("\"version\": 3", "\"version\": $version")
    private fun kanjiQuery(q: ItemQuery) = runBlocking { content.observeKanjiItems(q).first().map { it.item } }

    @Test
    fun kanjiImport_writesOnlyKanjiAndTheirWords_neverVocabulary() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(json).outcome)
        assertEquals(393, db.countRows("kanji"))
        assertEquals(1271 + 7, db.countRows("kanji_examples"))
        // Mimikara's 880 words, untouched; none added by the kanji pack.
        assertEquals(880, db.countRows("vocabulary"))
        assertEquals(880, content.observeVocabularyItems(ItemQuery()).first().size)
        val detail = content.getKanjiDetail(kanjiId("波"))!!
        assertEquals("BA", detail.kanji.hanViet)
        assertEquals("Sóng", detail.kanji.meaningVi)
        assertEquals(listOf("ハ"), detail.kanji.onReadings)
        assertEquals(listOf("なみ"), detail.kanji.kunReadings)
        assertEquals(8, detail.kanji.strokeCount)
        assertTrue(detail.source.licenseVerified)
    }

    @Test
    fun vocabularyImport_doesNotTouchKanji() = runBlocking {
        importer.import(json)
        val mimikara = asset(AndroidContentImportRepository.MIMIKARA_PACK_ASSET).replaceFirst("\"version\": 2", "\"version\": 3")
        assertEquals(ImportOutcome.IMPORTED, importer.import(mimikara).outcome)
        assertEquals(393, db.countRows("kanji"))
        assertEquals(1271 + 7, db.countRows("kanji_examples"))
        assertEquals(3, content.getKanjiDetail(kanjiId("意"))!!.examples.size)
    }

    @Test
    fun kanjiDetail_showsItsPdfGroupWords_evenWhenTheWordIsAlsoVocabulary() = runBlocking {
        importer.import(json)
        val words = content.getKanjiDetail(kanjiId("意"))!!.examples
        assertEquals(listOf("注意", "意味", "決意"), words.map { it.japanese })
        assertEquals("{注意|ちゅうい}", words.first().furigana)
        assertEquals("Chú ý", words.first().translationVi)
        // 皮 かわ is also a Mimikara word: two records, two roles, not merged.
        val vocabulary = content.searchVocabulary("かわ").single { it.expression == "皮" }
        assertTrue(vocabulary.id.startsWith("mimikara-n3-"))
        val skin = content.getKanjiDetail(kanjiId("皮"))!!.examples
        assertTrue(skin.any { it.japanese == "皮" && it.translationVi == "Da, vỏ" })
        assertTrue(skin.none { it.id == vocabulary.id })
        assertEquals("da", vocabulary.meaningVi)
        // Words the PDF prints under 坂 belong to 板: shown there, same ids.
        // 坂 itself has no word in the PDF: the words added for it in version 3.
        assertEquals(listOf("坂", "坂道", "上り坂"), content.getKanjiDetail(kanjiId("坂"))!!.examples.map { it.japanese })
        assertEquals("n3k-x-11-013", content.getKanjiDetail(kanjiId("板"))!!.examples.single { it.japanese == "黒板" }.id)
    }

    @Test
    fun listSearchAndLessonFilter() = runBlocking {
        importer.import(json)
        assertEquals(27, kanjiQuery(ItemQuery(lessonId = "n3k-l01")).size)
        assertEquals("意", kanjiQuery(ItemQuery(lessonId = "n3k-l01")).first().character)
        assertEquals(listOf("伝"), kanjiQuery(ItemQuery(text = "伝")).map { it.character })
        // Meaning and Hán Việt without Vietnamese diacritics.
        assertTrue(kanjiQuery(ItemQuery(text = "truyen dat")).any { it.character == "伝" })
    }

    @Test
    fun reimport_isIdempotent_andKeepsProgressAndMarks() = runBlocking {
        importer.import(json)
        val progress = LearningProgressEntity(ItemType.KANJI, kanjiId("意"), ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 4, 1, 1_000, 4_000)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.KANJI, kanjiId("波"), 9))
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(json, onlyIfNewer = true).outcome)
        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(4)).outcome)
        assertEquals(393, db.countRows("kanji"))
        // The kanji's 1 272 + 7 words, next to the 870 example sentences of the vocabulary pack.
        assertEquals(1272 + 7 + 870, db.countRows("example_sentences"))
        assertEquals(1271 + 7, db.countRows("kanji_examples"))
        assertEquals(progress, db.progressDao().get(ItemType.KANJI, kanjiId("意")))
        assertEquals(listOf(kanjiId("波")), kanjiQuery(ItemQuery(markedOnly = true)).map { it.id })
    }

    @Test
    fun failureInTheMiddle_keepsInstalledContentAndProgress() = runBlocking {
        importer.import(json)
        db.progressDao().upsert(LearningProgressEntity(ItemType.KANJI, kanjiId("必"), ProgressState.LEARNING, 0, 20, 1, 1, 1, 0, 1, 1))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_kanji BEFORE INSERT ON kanji WHEN NEW.character = '必' BEGIN SELECT RAISE(ABORT, 'disk full (test)'); END",
        )
        assertEquals(ImportOutcome.FAILED, importer.import(bumped(4)).outcome)
        assertEquals(3, db.contentPackDao().getPack("n3-kanji")!!.version)
        assertEquals(393, db.countRows("kanji"))
        assertEquals(1, db.countRows("learning_progress"))
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        importer.import(json)
        db.close()
        open()
        assertEquals(393, db.countRows("kanji"))
        assertEquals("Truyền, truyền đạt", content.getKanjiDetail(kanjiId("伝"))!!.kanji.meaningVi)
    }
}
