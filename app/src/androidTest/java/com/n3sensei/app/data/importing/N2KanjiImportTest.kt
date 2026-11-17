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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The bundled Kanji N2 pack (448 kanji, 1 811 words, from the user's Anki deck) in a real Room
 * database file, installed after the N3 packs as on a device that already has them.
 */
@RunWith(AndroidJUnit4::class)
class N2KanjiImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "n2-kanji-import-test.db"
    private fun asset(path: String) = context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    private val json = asset(AndroidContentImportRepository.N2_KANJI_PACK_ASSET)
    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private val content get() = OfflineContentRepository(db.contentDao(), db.contentPackDao())

    private fun open() {
        db = N3Database.create(context, name)
        importer = ContentPackImporter(db.contentPackDao(), Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC))
    }

    private fun n2(character: String) = "n2k-k%05x".format(character.codePointAt(0))
    private fun n3(character: String) = "n3k-k%05x".format(character.codePointAt(0))
    private fun bumped(version: Int) = json.replaceFirst("\"version\": 1", "\"version\": $version")
    private fun kanji(q: ItemQuery) = runBlocking { content.observeKanjiItems(q).first().map { it.item } }
    private fun table(sql: String) = db.openHelper.readableDatabase.query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "∅" }) }
    }

    /** Every N3 row the N2 pack must leave as it is. */
    private fun n3Snapshot() = listOf(
        table("SELECT * FROM kanji WHERE id LIKE 'n3k-%' ORDER BY id"),
        table("SELECT * FROM lessons WHERE pack_id <> 'n2-kanji' ORDER BY id"),
        table("SELECT * FROM vocabulary ORDER BY id"),
        table("SELECT * FROM grammar ORDER BY id"),
        table("SELECT * FROM kanji_examples WHERE kanji_id LIKE 'n3k-%' ORDER BY kanji_id, example_id"),
        table("SELECT * FROM learning_progress ORDER BY item_type, item_id"),
        table("SELECT * FROM review_records ORDER BY id"),
    )

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        open()
        // A device that already has the N3 packs and some progress on them.
        listOf(
            AndroidContentImportRepository.MIMIKARA_PACK_ASSET,
            AndroidContentImportRepository.N3_GRAMMAR_PACK_ASSET,
            AndroidContentImportRepository.N3_KANJI_PACK_ASSET,
        ).forEach { assertEquals(it, ImportOutcome.IMPORTED, runBlocking { importer.import(asset(it)) }.outcome) }
        runBlocking {
            db.progressDao().upsert(LearningProgressEntity(ItemType.KANJI, n3("意"), ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 4, 1, 1_000, 4_000))
            db.progressDao().insertMark(ItemMarkEntity(ItemType.KANJI, n3("波"), 9))
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun import_adds18N2Lessons_448Kanji_1811Words_andChangesNothingOfN3() = runBlocking {
        val before = n3Snapshot()
        assertEquals(ImportOutcome.IMPORTED, importer.import(json).outcome)
        assertEquals(393 + 448, db.countRows("kanji"))
        assertEquals(448, db.countRows("kanji WHERE id LIKE 'n2k-%'"))
        assertEquals(18, db.countRows("lessons WHERE pack_id = 'n2-kanji' AND jlpt_level = 'N2'"))
        assertEquals(1811, db.countRows("kanji_examples WHERE kanji_id LIKE 'n2k-%'"))
        // N3 kanji, lessons, words, grammar, progress and history: exactly as before.
        assertEquals(before, n3Snapshot())
        assertEquals(880, db.countRows("vocabulary"))
        // No schedule is created by installing content.
        assertEquals(1, db.countRows("learning_progress"))
        assertEquals(0, db.countRows("review_records"))
        // A kanji of both levels (祭) stays one N3 record.
        assertEquals(1, db.countRows("kanji WHERE character = '祭'"))
    }

    @Test
    fun anN2Kanji_hasTheDecksDataAndItsWords() = runBlocking {
        importer.import(json)
        val detail = content.getKanjiDetail(n2("井"))!!
        assertEquals("TỈNH", detail.kanji.hanViet)
        assertEquals("Cái giếng", detail.kanji.meaningVi)
        assertEquals(listOf("セイ", "ショウ"), detail.kanji.onReadings)
        assertEquals(listOf("い"), detail.kanji.kunReadings)
        assertEquals(4, detail.kanji.strokeCount)
        assertEquals(listOf("井戸", "井戸水", "天井"), detail.examples.map { it.japanese })
        assertEquals("{天井|てんじょう}", detail.examples.last().furigana)
        assertFalse(detail.source.licenseVerified)
    }

    @Test
    fun theLevelFilter_keepsN3AndN2Apart() = runBlocking {
        importer.import(json)
        val n3List = kanji(ItemQuery(jlptLevel = "N3"))
        val n2List = kanji(ItemQuery(jlptLevel = "N2"))
        assertEquals(393, n3List.size)
        assertEquals(448, n2List.size)
        assertTrue(n3List.all { it.id.startsWith("n3k-") } && n2List.all { it.id.startsWith("n2k-") })
        assertEquals("意", n3List.first().character)
        assertEquals("井", n2List.first().character)
        // Every level together: N3 first, as before N2 existed.
        assertEquals(n3List + n2List, kanji(ItemQuery()))
        assertEquals(23, kanji(ItemQuery(lessonId = "n2k-l01")).size)
    }

    @Test
    fun installingAgain_writesNothing_andANewVersionKeepsIdsProgressAndMarks() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(json).outcome)
        val progress = LearningProgressEntity(ItemType.KANJI, n2("井"), ProgressState.LEARNING, 1, 60, 2_000, 1_500, 1, 0, 1_000, 1_500)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.KANJI, n2("免"), 10))
        val rows = table("SELECT * FROM kanji WHERE id LIKE 'n2k-%' ORDER BY id")
        // Bundled content on every start: the same version is not written again.
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(json, onlyIfNewer = true).outcome)
        // A later version replaces the pack's rows: no duplicates, same ids, learner state kept.
        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(2)).outcome)
        assertEquals(rows, table("SELECT * FROM kanji WHERE id LIKE 'n2k-%' ORDER BY id"))
        assertEquals(393 + 448, db.countRows("kanji"))
        assertEquals(18, db.countRows("lessons WHERE pack_id = 'n2-kanji'"))
        assertEquals(1811, db.countRows("kanji_examples WHERE kanji_id LIKE 'n2k-%'"))
        assertEquals(1811, db.countRows("example_sentences WHERE pack_id = 'n2-kanji'"))
        assertEquals(progress, db.progressDao().get(ItemType.KANJI, n2("井")))
        assertEquals(setOf(n3("波"), n2("免")), kanji(ItemQuery(markedOnly = true)).map { it.id }.toSet())
        assertEquals(2, db.countRows("learning_progress"))
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        importer.import(json)
        db.close()
        open()
        assertEquals(448, db.countRows("kanji WHERE id LIKE 'n2k-%'"))
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(json, onlyIfNewer = true).outcome)
    }
}
