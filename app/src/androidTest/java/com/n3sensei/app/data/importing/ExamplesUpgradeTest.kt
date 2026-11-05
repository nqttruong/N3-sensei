package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineUserContentRepository
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.testing.countRows
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
 * The bundled packs with the added examples replace the versions already installed (exactly as
 * committed before, in content-history/) the way the app does at launch: the learner's progress,
 * marks, review history and own edits stay, and the items and examples that were there are
 * unchanged; only examples are added.
 */
@RunWith(AndroidJUnit4::class)
class ExamplesUpgradeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val name = "examples-upgrade-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private val content get() = OfflineContentRepository(db.contentDao(), db.contentPackDao())

    private fun bundled(asset: String) = context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun before(file: String) = testAssets.open("content-history/$file").use { it.readBytes().toString(Charsets.UTF_8) }

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        importer = ContentPackImporter(db.contentPackDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun studied(type: ItemType, id: String) = LearningProgressEntity(type, id, ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 4, 1, 1_000, 4_000)

    private fun reviewed(type: ItemType, id: String) = db.openHelper.writableDatabase.execSQL(
        "INSERT INTO review_records (item_type, item_id, rating, reviewed_at, interval_before_minutes, interval_after_minutes, due_at_after, was_new) " +
            "VALUES ('${type.name}', '$id', 'GOOD', 6000, 20, 1440, 7000, 0)",
    )

    @Test
    fun vocabulary_v1ToV2_addsOneExamplePerWord_andKeepsEverythingElse() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(before("mimikara-n3-vocab-v1.json")).outcome)
        assertEquals(0, db.countRows("example_sentences"))
        val progress = studied(ItemType.VOCABULARY, "mimikara-n3-v046")
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, "mimikara-n3-v046", 9))
        reviewed(ItemType.VOCABULARY, "mimikara-n3-v046")
        // The learner's own meaning for a word stays after the update.
        OfflineUserContentRepository(db.userContentDao(), clock).save(ItemType.VOCABULARY, "mimikara-n3-v047", mapOf(EditableField.WORD_MEANING to "sắp xếp"))
        val wordsBefore = (1..880).map { db.contentDao().getVocabulary("mimikara-n3-v%03d".format(it)) }

        // App launch: the bundled version 2 is newer and replaces version 1.
        assertEquals(ImportOutcome.IMPORTED, importer.import(bundled(AndroidContentImportRepository.MIMIKARA_PACK_ASSET), onlyIfNewer = true).outcome)
        assertEquals(2, db.contentPackDao().getPack("mimikara-n3-vocab")!!.version)
        assertEquals(880, db.countRows("vocabulary"))
        assertEquals(870, db.countRows("example_sentences"))
        assertEquals(870, db.countRows("vocabulary_examples"))
        assertEquals(wordsBefore, (1..880).map { db.contentDao().getVocabulary("mimikara-n3-v%03d".format(it)) })
        assertEquals(progress, db.progressDao().get(ItemType.VOCABULARY, "mimikara-n3-v046"))
        assertEquals(1, db.countRows("item_marks"))
        assertEquals(1, db.countRows("review_records"))
        assertEquals("sắp xếp", content.getVocabularyDetail("mimikara-n3-v047")!!.vocabulary.meaningVi)

        val detail = content.getVocabularyDetail("mimikara-n3-v046")!!
        assertEquals("準備", detail.vocabulary.expression)
        with(detail.examples.single()) {
            assertEquals("旅行の準備はもうできましたか。", japanese)
            assertEquals("{旅行|りょこう}の{準備|じゅんび}はもうできましたか。", furigana)
            assertEquals("Bạn đã chuẩn bị xong cho chuyến đi chưa?", translationVi)
        }
        // A word whose sheet entry is contradictory gets no example (to be checked by the owner).
        assertTrue(content.getVocabularyDetail("mimikara-n3-v598")!!.examples.isEmpty())
        // Opening the app again installs nothing more: no duplicate examples.
        assertEquals(ImportOutcome.UP_TO_DATE, importer.import(bundled(AndroidContentImportRepository.MIMIKARA_PACK_ASSET), onlyIfNewer = true).outcome)
        assertEquals(870, db.countRows("example_sentences"))
    }

    @Test
    fun grammar_v3ToV4_keepsTheOldExamples_andAddsToThePointsWithout() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(before("n3-grammar-v3.json")).outcome)
        val oldExamples = (1..110).associate { n -> "n3g-g%03d".format(n).let { it to content.getGrammarDetail(it)!!.examples } }
        val progress = studied(ItemType.GRAMMAR, "n3g-g111")
        db.progressDao().upsert(progress)
        reviewed(ItemType.GRAMMAR, "n3g-g001")

        assertEquals(ImportOutcome.IMPORTED, importer.import(bundled(AndroidContentImportRepository.N3_GRAMMAR_PACK_ASSET), onlyIfNewer = true).outcome)
        assertEquals(4, db.contentPackDao().getPack("n3-grammar")!!.version)
        assertEquals(220 + 140, db.countRows("example_sentences"))
        // The 110 points: the same examples (ids, text, order) as before.
        oldExamples.forEach { (id, examples) -> assertEquals(id, examples, content.getGrammarDetail(id)!!.examples) }
        val added = content.getGrammarDetail("n3g-g111")!!.examples
        assertEquals(listOf("n3g-g111-ex1", "n3g-g111-ex2"), added.map { it.id })
        assertEquals("Do mưa lớn nên tàu điện đang ngừng chạy.", added.first().translationVi)
        assertEquals(3, content.getGrammarDetail("n3g-g113")!!.examples.size)
        assertEquals(progress, db.progressDao().get(ItemType.GRAMMAR, "n3g-g111"))
        assertEquals(1, db.countRows("review_records"))
    }

    @Test
    fun kanji_v2ToV3_addsWordsOnlyToTheKanjiWithout() = runBlocking {
        assertEquals(ImportOutcome.IMPORTED, importer.import(before("n3-kanji-v2.json")).outcome)
        val sakaId = "n3k-k05742"
        val ii = "n3k-k0610f" // 意
        val wordsOfIi = content.getKanjiDetail(ii)!!.examples
        assertTrue(content.getKanjiDetail(sakaId)!!.examples.isEmpty())
        db.progressDao().upsert(studied(ItemType.KANJI, sakaId))

        assertEquals(ImportOutcome.IMPORTED, importer.import(bundled(AndroidContentImportRepository.N3_KANJI_PACK_ASSET), onlyIfNewer = true).outcome)
        assertEquals(1272 + 7, db.countRows("example_sentences"))
        assertEquals(wordsOfIi, content.getKanjiDetail(ii)!!.examples)
        assertEquals(listOf("{坂|さか}", "{坂道|さかみち}", "{上|のぼ}り{坂|ざか}"), content.getKanjiDetail(sakaId)!!.examples.map { it.furigana })
        assertEquals(studied(ItemType.KANJI, sakaId), db.progressDao().get(ItemType.KANJI, sakaId))
    }
}
