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

/** The bundled N3 grammar pack (110 patterns + 64 from N3.pdf) in a real Room database file. */
@RunWith(AndroidJUnit4::class)
class N3GrammarImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "n3-grammar-import-test.db"
    private val json = context.assets.open(AndroidContentImportRepository.N3_GRAMMAR_PACK_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private val content get() = OfflineContentRepository(db.contentDao(), db.contentPackDao())

    private fun open() {
        db = N3Database.create(context, name)
        importer = ContentPackImporter(db.contentPackDao(), Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC))
    }

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    /** Version 1 as committed before the N3.pdf points were added (110 points, lessons of 10). */
    private val v1 = InstrumentationRegistry.getInstrumentation().context.assets
        .open("content-history/n3-grammar-v1.json").use { it.readBytes().toString(Charsets.UTF_8) }

    private fun bumped(version: Int) = json.replaceFirst("\"version\": 3", "\"version\": $version")
    private fun query(q: ItemQuery) = runBlocking { content.observeGrammarItems(q).first().map { it.item } }

    @Test
    fun allPatternsImported_withExamplesAndSource() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(json).outcome)
        assertEquals(174, db.countRows("grammar"))
        assertEquals(220, db.countRows("example_sentences"))
        val detail = content.getGrammarDetail("n3g-g001")!!
        assertEquals("〜ことにしている", detail.grammar.pattern)
        assertEquals(2, detail.examples.size)
        assertEquals("Vì sức khỏe, tôi có thói quen đi bộ ba mươi phút mỗi sáng.", detail.examples[0].translationVi)
        assertEquals("https://www.vnjpclub.com/mimi-kara-n3-bunpo/np-1.html", detail.source.url)
        assertTrue(!detail.source.licenseVerified)
        val fromPdf = content.getGrammarDetail("n3g-g111")!!
        assertEquals("〜ため（に）", fromPdf.grammar.pattern)
        assertEquals("V普通形 + ため（に）／Nの + ため（に）", fromPdf.grammar.structure)
        assertTrue(fromPdf.examples.isEmpty())
        assertEquals("N3.pdf · Ngữ pháp, nhóm 01 thẻ 1 (tên mẫu, cách chia, nghĩa ngắn)", fromPdf.source.name)
    }

    @Test
    fun listSearchAndLessonFilter() = runBlocking {
        importer.import(json)
        assertEquals(174, query(ItemQuery()).size)
        assertEquals(listOf("〜から〜にかけて"), query(ItemQuery(text = "にかけて")).map { it.pattern })
        // Meaning without Vietnamese diacritics.
        assertTrue(query(ItemQuery(text = "so voi")).map { it.id }.containsAll(listOf("n3g-g023", "n3g-g104")))
        assertEquals(2, query(ItemQuery(text = "に対し")).size) // two uses, both kept
        assertEquals(7, query(ItemQuery(lessonId = "n3g-l01")).size)
        assertEquals(6, query(ItemQuery(lessonId = "n3g-l25")).size)
        assertEquals(listOf("〜はずがない"), query(ItemQuery(text = "はずがない")).map { it.pattern })
        assertEquals("〜ことにしている", query(ItemQuery(lessonId = "n3g-l01")).first().pattern)
    }

    @Test
    fun reimport_isIdempotent_andKeepsProgressAndMarks() = runBlocking {
        importer.import(json)
        val progress = LearningProgressEntity(ItemType.GRAMMAR, "n3g-g039", ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 4, 1, 1_000, 4_000)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.GRAMMAR, "n3g-g092", 9))
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(json, onlyIfNewer = true).outcome)
        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(4)).outcome)
        assertEquals(174, db.countRows("grammar"))
        assertEquals(220, db.countRows("example_sentences"))
        assertEquals(progress, db.progressDao().get(ItemType.GRAMMAR, "n3g-g039"))
        assertEquals(listOf("n3g-g092"), query(ItemQuery(markedOnly = true)).map { it.id })
    }

    @Test
    fun failureInTheMiddle_keepsInstalledContentAndProgress() = runBlocking {
        importer.import(json)
        db.progressDao().upsert(LearningProgressEntity(ItemType.GRAMMAR, "n3g-g060", ProgressState.LEARNING, 0, 20, 1, 1, 1, 0, 1, 1))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_g060 BEFORE INSERT ON grammar WHEN NEW.id = 'n3g-g060' BEGIN SELECT RAISE(ABORT, 'disk full (test)'); END",
        )
        assertEquals(ImportOutcome.FAILED, importer.import(bumped(4)).outcome)
        assertEquals(3, db.contentPackDao().getPack("n3-grammar")!!.version)
        assertEquals(174, db.countRows("grammar"))
        assertEquals(1, db.countRows("learning_progress"))
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        importer.import(json)
        db.close()
        open()
        assertEquals(174, db.countRows("grammar"))
        assertEquals("〜おそれがある", content.getGrammarDetail("n3g-g110")!!.grammar.pattern)
    }

    @Test
    fun upgradeFromVersion1_keepsProgressAndMarks_ofThe110() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(v1).outcome)
        assertEquals(110, db.countRows("grammar"))
        assertEquals("n3g-l03", content.getGrammarDetail("n3g-g025")!!.grammar.lessonId)
        val progress = LearningProgressEntity(ItemType.GRAMMAR, "n3g-g025", ProgressState.REVIEW, 2, 1440, 7_000, 6_000, 3, 0, 1_000, 6_000)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.GRAMMAR, "n3g-g104", 5))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO review_records (item_type, item_id, rating, reviewed_at, interval_before_minutes, interval_after_minutes, due_at_after, was_new) " +
                "VALUES ('GRAMMAR', 'n3g-g025', 'GOOD', 6000, 20, 1440, 7000, 0)",
        )

        // The bundled version 2 replaces version 1 on the next launch.
        assertEquals(ImportOutcome.IMPORTED, importer.import(json, onlyIfNewer = true).outcome)
        assertEquals(174, db.countRows("grammar"))
        assertEquals(progress, db.progressDao().get(ItemType.GRAMMAR, "n3g-g025"))
        assertEquals(listOf("n3g-g104"), query(ItemQuery(markedOnly = true)).map { it.id })
        assertEquals(1, db.countRows("review_records"))
        // Same point, now in the lesson of 7 that holds it.
        val moved = content.getGrammarDetail("n3g-g025")!!.grammar
        assertEquals("〜に対し（て）", moved.pattern)
        assertEquals("n3g-l04", moved.lessonId)
        assertEquals(25, db.countRows("lessons"))
    }
}
