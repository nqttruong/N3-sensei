package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.domain.importing.ImportCheck
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Real Room database + the JSON pack bundled with the app. */
@RunWith(AndroidJUnit4::class)
class ContentPackImporterTest {

    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private lateinit var content: OfflineContentRepository

    private val bundledJson: String = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open(AndroidContentImportRepository.BUNDLED_PACK_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        importer = ContentPackImporter(db.contentPackDao(), Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC))
        content = OfflineContentRepository(db.contentDao(), db.contentPackDao())
    }

    @After
    fun tearDown() = db.close()

    private fun bumped(json: String, version: Int) = json.replaceFirst("\"version\": 1", "\"version\": $version")

    @Test
    fun bundledPack_importsIntoRoom_withUnicodeIntact() = runTest {
        val report = importer.import(bundledJson)

        assertEquals(ImportOutcome.IMPORTED, report.outcome)
        assertEquals(30, report.counts.getValue(ImportRecordType.VOCABULARY).valid)
        assertEquals(30, db.countRows("vocabulary"))
        assertEquals(27, db.countRows("kanji"))
        assertEquals(7, db.countRows("grammar"))
        assertEquals(7, db.countRows("grammar_exercises"))

        val keiken = content.searchVocabulary("経験").first()
        val detail = content.getVocabularyDetail(keiken.id)!!
        assertEquals("けいけん", detail.vocabulary.reading)
        assertEquals("{経|けい}{験|けん}", detail.vocabulary.furigana)
        assertEquals("日本で働いた経験があります。", detail.examples.single().japanese)
        val grammar = content.getGrammarDetail("illus-l01-g1")!!
        assertEquals(listOf("{来|く}る", "{来|き}た", "{来|き}て", "{来|こ}ない"), grammar.exercises.single().options)
        // Normalized search works on imported rows.
        assertEquals(keiken.id, content.searchVocabulary("kinh nghiem").first().id)
    }

    @Test
    fun onlyIfNewer_skipsSameVersion_andUpgradesNewerVersion() = runTest {
        importer.import(bundledJson, onlyIfNewer = true)
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(bundledJson, onlyIfNewer = true).outcome)

        val upgrade = importer.import(bumped(bundledJson, 2), onlyIfNewer = true)
        assertEquals(ImportOutcome.IMPORTED, upgrade.outcome)
        assertEquals(2, db.contentPackDao().getPack("n3sensei-illustration")!!.version)
        assertEquals(30, db.countRows("vocabulary"))
    }

    @Test
    fun reimport_keepsProgressAndMarks() = runTest {
        importer.import(bundledJson)
        val progress = db.progressDao()
        progress.upsert(LearningProgressEntity(ItemType.KANJI, "illus-l01-k01", ProgressState.REVIEW, 2, 1440, 5, 4, 3, 0, 1, 4))
        progress.insertMark(ItemMarkEntity(ItemType.VOCABULARY, "illus-l01-v01", markedAt = 9))

        assertEquals(ImportOutcome.IMPORTED, importer.import(bumped(bundledJson, 2)).outcome)

        assertEquals(3, progress.get(ItemType.KANJI, "illus-l01-k01")!!.reviewCount)
        val marked = content.observeVocabularyItems(ItemQuery(markedOnly = true)).first()
        assertEquals(listOf("illus-l01-v01"), marked.map { it.item.id })
    }

    @Test
    fun check_comparesWithInstalledVersion_andWritesNothing() = runTest {
        // New pack: nothing installed, nothing removed.
        val fresh = (importer.check(bundledJson) as ImportCheck.Ready).preview
        assertNull(fresh.installedVersion)
        assertEquals(0, fresh.removedItems)
        assertTrue(content.observeVocabularyItems(ItemQuery()).first().isEmpty())

        importer.import(bundledJson)
        db.progressDao().upsert(LearningProgressEntity(ItemType.VOCABULARY, "illus-l01-v01", ProgressState.REVIEW, 2, 1440, 5, 4, 3, 0, 1, 4))
        // Version 2 drops the first two words.
        val json = JSONObject(bumped(bundledJson, 2))
        val words = json.getJSONArray("lessons").getJSONObject(0).getJSONArray("vocabulary")
        repeat(2) { words.remove(0) }
        val preview = (importer.check(json.toString()) as ImportCheck.Ready).preview
        assertEquals(1, preview.installedVersion)
        assertTrue(preview.replacesInstalled)
        assertEquals(2, preview.removedItems)
        assertEquals(1, preview.removedWithProgress)
        // Checking wrote nothing: both words are still installed.
        assertEquals(30, content.observeVocabularyItems(ItemQuery()).first().size)
    }

    @Test
    fun check_ofInvalidPack_fails() = runTest {
        assertTrue(importer.check("{ not json") is ImportCheck.Failed)
    }

    @Test
    fun olderVersion_isImportedWithWarning() = runTest {
        importer.import(bumped(bundledJson, 3))
        val report = importer.import(bundledJson)
        assertEquals(ImportOutcome.IMPORTED, report.outcome)
        assertTrue(report.issues.any { it.code == IssueCode.OLDER_VERSION })
    }

    @Test
    fun fatalProblems_writeNothing_andKeepInstalledContent() = runTest {
        importer.import(bundledJson)

        val invalidJson = importer.import("{ broken")
        assertEquals(ImportOutcome.FAILED, invalidJson.outcome)
        assertEquals(IssueCode.INVALID_JSON, invalidJson.issues.single().code)

        val wrongFormat = importer.import(bundledJson.replace("n3sensei-content-pack", "other-format"))
        assertEquals(ImportOutcome.FAILED, wrongFormat.outcome)

        assertEquals(30, db.countRows("vocabulary"))
    }

    @Test
    fun partiallyInvalidPack_importsValidRecords_andReportsSkipped() = runTest {
        val json = """
            {
              "format": "n3sensei-content-pack", "formatVersion": 1,
              "pack": { "id": "user-pack", "title": "Gói thử", "version": 1 },
              "sources": [ { "id": "s", "name": "Nguồn thử", "license": "CC BY 4.0" } ],
              "lessons": [ {
                "id": "u-l1", "number": 1, "title": "Bài 1",
                "vocabulary": [
                  { "id": "u-v1", "expression": "会議", "reading": "かいぎ", "furigana": "{会|かい}{議|ぎ}", "meaningVi": "cuộc họp" },
                  { "id": "u-v2", "expression": "資料", "reading": "しりょう", "furigana": "{資|し}{料|りょう", "meaningVi": "tài liệu" },
                  { "id": "u-v1", "expression": "残業", "reading": "ざんぎょう", "meaningVi": "làm thêm" },
                  { "id": "u-v4", "reading": "なし", "meaningVi": "thiếu chữ" }
                ],
                "kanji": [ { "id": "u-k1", "character": "議", "onReadings": ["ギ"], "meaningVi": "nghị" } ]
              } ]
            }
        """.trimIndent()

        val report = importer.import(json)

        assertEquals(ImportOutcome.IMPORTED, report.outcome)
        val vocab = report.counts.getValue(ImportRecordType.VOCABULARY)
        assertEquals(1, vocab.valid)
        assertEquals(3, vocab.skipped)
        assertEquals(1, db.countRows("vocabulary"))
        assertNotNull(content.getVocabularyDetail("u-v1"))
        assertEquals(1, db.countRows("kanji"))
    }
}
