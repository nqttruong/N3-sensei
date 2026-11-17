package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContrastContentRepository
import com.n3sensei.app.data.repository.RoomContrastSourceResolver
import com.n3sensei.app.domain.contrast.ContrastSourceIds
import com.n3sensei.app.domain.contrast.SourceRefStatus
import com.n3sensei.app.domain.importing.ImportOutcome
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
 * The 69 AI-written, UNREVIEWED contrast groups (TEST-ONLY pack built by drafts_tool.py test-pack;
 * never in the app's assets) on a real Room v16 database with the real bundled source packs:
 * imported through the real importer, every item's source verified against the stored rows, the
 * entry-point query, and nothing written to learner tables.
 */
@RunWith(AndroidJUnit4::class)
class ContrastContentPackRoomTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-content-pack-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-18T02:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: N3Database
    private val pack = instrumentation.context.assets.open("contrast/test-pack-n3-unreviewed.json").use { it.readBytes().toString(Charsets.UTF_8) }

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        AndroidContentImportRepository.BUNDLED_PACK_ASSETS.forEach { asset ->
            val json = context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(asset, ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), clock).import(json).outcome)
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun learnerTables() = listOf("contrast_sessions", "contrast_session_items", "contrast_attempts", "contrast_progress", "contrast_reviews", "learning_progress", "review_records")
        .associateWith { db.countRows(it) }

    @Test
    fun the69Groups_import_withEverySourceVerified_andNoLearnerData() = runBlocking {
        val before = learnerTables()
        val report = ContrastContentImporter(db.contrastDao(), clock).import(pack)
        assertEquals(ContrastContentImporter.summary(report), ContrastImportReport.Outcome.IMPORTED, report.outcome)
        assertEquals(69, db.countRows("contrast_groups"))
        assertEquals(164, db.countRows("contrast_items"))
        assertEquals(277, db.countRows("contrast_questions"))
        assertEquals(328, db.countRows("contrast_examples"))
        // Every item's source matches the stored source row (fingerprint and snapshot), as the app checks it.
        val content = OfflineContrastContentRepository(db.contrastDao())
        val resolver = RoomContrastSourceResolver(db.contrastDao())
        val groups = content.observeGroups().first()
        var items = 0
        groups.forEach { g ->
            val detail = content.getDetail(g.id)!!
            val resolved = resolver.resolve(detail.items)
            detail.items.forEach { item ->
                assertEquals("${g.id} ${item.id}", SourceRefStatus.OK, resolved.getValue(item.id).status)
                items++
            }
        }
        assertEquals(164, items)
        // Importing content writes no learner data and does not touch the old SRS.
        assertEquals(before, learnerTables())
        // The same pack again: up to date, nothing written.
        assertEquals(ContrastImportReport.Outcome.UP_TO_DATE, ContrastContentImporter(db.contrastDao(), clock).import(pack).outcome)
    }

    @Test
    fun entryPoints_findTheGroupsOfAnItem_inPackOrder() = runBlocking {
        ContrastContentImporter(db.contrastDao(), clock).import(pack)
        val content = OfflineContrastContentRepository(db.contrastDao())
        suspend fun ids(s: ContrastSourceIds) = content.observeGroupsForSources(s).first().map { it.id }
        assertEquals(listOf("cg-g-koto-ni-shiteiru-naru"), ids(ContrastSourceIds(grammar = setOf("n3g-g001"))))
        // n3g-g037 (〜にとって) is in two groups.
        assertEquals(listOf("cg-g-nikanshite-nitaishite-nitotte", "cg-g-toshite-nitotte"), ids(ContrastSourceIds(grammar = setOf("n3g-g037"))))
        // 冷める is in 冷める／覚める and 冷える／冷める.
        assertEquals(listOf("cg-v-sameru-cold-awake", "cg-v-hieru-sameru"), ids(ContrastSourceIds(vocabulary = setOf("mimikara-n3-v211"))))
        assertEquals(listOf("cg-k-hi-kare-nami"), ids(ContrastSourceIds(kanji = setOf("n3k-k076ae"))))
        // Ids of another kind or of no group: nothing; a mix finds each group once.
        assertTrue(ids(ContrastSourceIds(kanji = setOf("n3g-g001"), vocabulary = setOf("mimikara-n3-v001"))).isEmpty())
        assertEquals(listOf("cg-g-koto-ni-shiteiru-naru", "cg-k-hi-kare-nami"), ids(ContrastSourceIds(grammar = setOf("n3g-g001", "n3g-g002"), kanji = setOf("n3k-k076ae", "n3k-k05f7c"))))
        assertTrue(ids(ContrastSourceIds()).isEmpty())
    }

    @Test
    fun withoutAPack_noEntryPoint() = runBlocking {
        val content = OfflineContrastContentRepository(db.contrastDao())
        assertTrue(content.observeGroupsForSources(ContrastSourceIds(grammar = setOf("n3g-g001"))).first().isEmpty())
    }
}
