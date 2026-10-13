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
 * The bundled Mimikara N3 pack (880 words) imported into a real Room database file, as the app
 * does on first launch: content, encoding, re-imports, learner data, failures, queries, reopen.
 */
@RunWith(AndroidJUnit4::class)
class MimikaraImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "mimikara-import-test.db"
    private val json = context.assets.open(AndroidContentImportRepository.MIMIKARA_PACK_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private val content get() = OfflineContentRepository(db.contentDao(), db.contentPackDao())

    private fun open() {
        db = N3Database.create(context, name)
        importer = ContentPackImporter(db.contentPackDao(), Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC))
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

    private fun query(q: ItemQuery) = runBlocking { content.observeVocabularyItems(q).first().map { it.item } }

    @Test
    fun allWordsImported_withJapaneseAndVietnameseIntact() = runBlocking {
        val report = importer.import(json)
        assertEquals(ImportOutcome.IMPORTED, report.outcome)
        assertEquals(880, db.countRows("vocabulary"))
        assertEquals(12, db.countRows("lessons"))
        val keiken = content.getVocabularyDetail("mimikara-n3-v090")!!.vocabulary
        assertEquals(listOf("作者", "さくしゃ", "tác giả"), listOf(keiken.expression, keiken.reading, keiken.meaningVi))
        assertEquals("khủng khiếp, rất nhiều", content.getVocabularyDetail("mimikara-n3-v592")!!.vocabulary.meaningVi)
        assertEquals("代わる・替わる・換わる", content.getVocabularyDetail("mimikara-n3-v437")!!.vocabulary.expression)
        assertEquals("パートナー", content.getVocabularyDetail("mimikara-n3-v796")!!.vocabulary.expression)
    }

    @Test
    fun listSearchAndLessonFilter_readFromRoom() = runBlocking {
        importer.import(json)
        assertEquals(880, query(ItemQuery()).size)
        assertEquals(listOf("作者"), query(ItemQuery(text = "tac gia")).map { it.expression }) // no diacritics
        assertEquals(listOf("作者"), query(ItemQuery(text = "さくしゃ")).map { it.expression })
        assertTrue(query(ItemQuery(text = "パートナー")).any { it.id == "mimikara-n3-v796" })
        assertEquals(2, query(ItemQuery(text = "しっかり")).size) // two senses, both kept
        assertEquals(120, query(ItemQuery(lessonId = "mimikara-n3-l01")).size)
        assertEquals(listOf("男性", "女性"), query(ItemQuery(lessonId = "mimikara-n3-l01")).take(2).map { it.expression })
    }

    @Test
    fun reimport_isIdempotent_andKeepsProgressAndMarks() = runBlocking {
        importer.import(json)
        val progress = LearningProgressEntity(ItemType.VOCABULARY, "mimikara-n3-v001", ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 4, 1, 1_000, 4_000)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, "mimikara-n3-v090", 9))

        // App start with the same version: nothing is written.
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(json, onlyIfNewer = true).outcome)
        // A newer version (or the same file picked again): the pack is replaced, never duplicated.
        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(2), onlyIfNewer = true).outcome)
        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(2)).outcome)
        assertEquals(880, db.countRows("vocabulary"))
        assertEquals(1, db.countRows("content_packs"))
        assertEquals(progress, db.progressDao().get(ItemType.VOCABULARY, "mimikara-n3-v001"))
        assertEquals(1, db.countRows("item_marks"))
        assertEquals(listOf("mimikara-n3-v090"), query(ItemQuery(markedOnly = true)).map { it.id })
    }

    @Test
    fun failureInTheMiddle_keepsInstalledContentAndProgress() = runBlocking {
        importer.import(json)
        db.progressDao().upsert(LearningProgressEntity(ItemType.VOCABULARY, "mimikara-n3-v500", ProgressState.LEARNING, 0, 20, 1, 1, 1, 0, 1, 1))
        // Fails on the 500th word while the new version is being written.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_v500 BEFORE INSERT ON vocabulary WHEN NEW.id = 'mimikara-n3-v500' BEGIN SELECT RAISE(ABORT, 'disk full (test)'); END",
        )
        val report = importer.import(bumped(2))
        assertEquals(ImportOutcome.FAILED, report.outcome)
        assertEquals(1, db.contentPackDao().getPack("mimikara-n3-vocab")!!.version) // old version still installed
        assertEquals(880, db.countRows("vocabulary"))
        assertEquals(1, db.countRows("learning_progress"))
    }

    @Test
    fun emptyOrBrokenFile_writesNothing() = runBlocking {
        assertEquals(ImportOutcome.FAILED, importer.import("").outcome)
        assertEquals(ImportOutcome.FAILED, importer.import(json.substring(0, json.length / 2)).outcome)
        assertEquals(ImportOutcome.FAILED, importer.import(json.replace("\"format\": \"n3sensei-content-pack\"", "\"format\": \"other\"")).outcome)
        assertEquals(0, db.countRows("vocabulary"))
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        importer.import(json)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, "mimikara-n3-v001", 1))
        db.close()
        open()
        assertEquals(880, db.countRows("vocabulary"))
        assertEquals("男性", content.getVocabularyDetail("mimikara-n3-v001")!!.vocabulary.expression)
        assertEquals(1, db.countRows("item_marks"))
    }

    @Test
    fun missingBundledAsset_isReported_andTheOtherPackStillInstalls() = runBlocking {
        val repository = AndroidContentImportRepository(
            context, importer, listOf("content/does-not-exist.json", AndroidContentImportRepository.MIMIKARA_PACK_ASSET),
        )
        val reports = repository.installBundledContent()
        assertEquals(listOf(ImportOutcome.FAILED, ImportOutcome.IMPORTED), reports.map { it.outcome })
        assertEquals(880, db.countRows("vocabulary"))
    }
}
