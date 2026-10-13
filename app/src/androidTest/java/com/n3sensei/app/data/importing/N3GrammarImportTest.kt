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

/** The bundled N3 grammar pack (110 patterns) in a real Room database file. */
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

    private fun bumped(version: Int) = json.replaceFirst("\"version\": 1", "\"version\": $version")
    private fun query(q: ItemQuery) = runBlocking { content.observeGrammarItems(q).first().map { it.item } }

    @Test
    fun allPatternsImported_withExamplesAndSource() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(json).outcome)
        assertEquals(110, db.countRows("grammar"))
        assertEquals(220, db.countRows("example_sentences"))
        val detail = content.getGrammarDetail("n3g-g001")!!
        assertEquals("〜ことにしている", detail.grammar.pattern)
        assertEquals(2, detail.examples.size)
        assertEquals("Vì sức khỏe, tôi có thói quen đi bộ ba mươi phút mỗi sáng.", detail.examples[0].translationVi)
        assertEquals("https://www.vnjpclub.com/mimi-kara-n3-bunpo/np-1.html", detail.source.url)
        assertTrue(!detail.source.licenseVerified)
    }

    @Test
    fun listSearchAndLessonFilter() = runBlocking {
        importer.import(json)
        assertEquals(110, query(ItemQuery()).size)
        assertEquals(listOf("〜から〜にかけて"), query(ItemQuery(text = "にかけて")).map { it.pattern })
        // Meaning without Vietnamese diacritics.
        assertTrue(query(ItemQuery(text = "so voi")).map { it.id }.containsAll(listOf("n3g-g023", "n3g-g104")))
        assertEquals(2, query(ItemQuery(text = "に対し")).size) // two uses, both kept
        assertEquals(10, query(ItemQuery(lessonId = "n3g-l01")).size)
        assertEquals("〜ことにしている", query(ItemQuery(lessonId = "n3g-l01")).first().pattern)
    }

    @Test
    fun reimport_isIdempotent_andKeepsProgressAndMarks() = runBlocking {
        importer.import(json)
        val progress = LearningProgressEntity(ItemType.GRAMMAR, "n3g-g039", ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 4, 1, 1_000, 4_000)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.GRAMMAR, "n3g-g092", 9))
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(json, onlyIfNewer = true).outcome)
        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(2)).outcome)
        assertEquals(110, db.countRows("grammar"))
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
        assertEquals(ImportOutcome.FAILED, importer.import(bumped(2)).outcome)
        assertEquals(1, db.contentPackDao().getPack("n3-grammar")!!.version)
        assertEquals(110, db.countRows("grammar"))
        assertEquals(1, db.countRows("learning_progress"))
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        importer.import(json)
        db.close()
        open()
        assertEquals(110, db.countRows("grammar"))
        assertEquals("〜おそれがある", content.getGrammarDetail("n3g-g110")!!.grammar.pattern)
    }
}
