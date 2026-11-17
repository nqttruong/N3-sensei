package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastFingerprint
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.SourceRefStatus
import com.n3sensei.app.domain.contrast.SourceReference
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
 * Source verification against the grammar / vocabulary / kanji rows really stored in Room by the
 * existing importer (which trims strings and drops empty reading entries). Expected fingerprints
 * were computed by tools/content/contrast/fingerprint.py, not by the app.
 */
@RunWith(AndroidJUnit4::class)
class RoomContrastSourceResolverTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: N3Database
    private lateinit var resolver: RoomContrastSourceResolver
    private val clock = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC)

    /** A source pack with untrimmed values and an empty kun reading, as a pack author might write it. */
    private val sourcePack = """
        {"format": "n3sensei-content-pack", "formatVersion": 1,
         "pack": {"id": "resolver-src", "title": "Nguồn thử", "version": 1, "jlptLevel": "N3", "defaultSourceId": "s"},
         "sources": [{"id": "s", "name": "Thử", "url": null, "license": "thử", "attribution": null, "licenseVerified": false}],
         "examples": [],
         "lessons": [
          {"id": "rl1", "number": 1, "title": "Bài 1",
           "vocabulary": [{"id": "rk-v1", "expression": "冷める", "reading": "さめる", "furigana": null, "meaningVi": "bị nguội", "examples": []}],
           "kanji": [{"id": "rk-k1", "character": "積", "onReadings": [" セキ "], "kunReadings": ["つ.む", "", " つ.もる "], "hanViet": "TÍCH", "meaningVi": " Tích lũy ", "strokeCount": 16, "examples": []}],
           "grammar": [
             {"id": "rk-g1", "pattern": "〜ことにしている", "structure": "Vる ＋ ことにしている", "meaningVi": "thói quen", "usage": "…", "examples": [], "exercises": []},
             {"id": "rk-g3", "pattern": "〜ばかり", "structure": "Vた ＋ ばかり", "meaningVi": "vừa mới", "usage": "…", "examples": [], "exercises": []}
           ]},
          {"id": "rl2", "number": 2, "title": "Bài 2",
           "grammar": [{"id": "rk-g2", "pattern": "〜ことにしている", "structure": "Vる ＋ ことにしている", "meaningVi": "thói quen", "usage": "…", "examples": [], "exercises": []}]}
         ]}
    """.trimIndent()

    // From fingerprint.py (stored values: trimmed, empty readings dropped).
    private val kanjiFingerprint = "sha256:e6e04b7f9a26a81cd45c0bebed004a06c15c2e88d1f434482f9e3c99e5646d4d"
    /** The same kanji fingerprinted WITHOUT dropping the empty reading: must not match what Room stores. */
    private val kanjiFingerprintOfUnstoredValues = "sha256:e8c221eba7de033c23c2d42dbada29e243f3f1ca784060804d6138fc15649bde"
    private val grammarFingerprint = "sha256:1f686d0e987bd0653e99c20265ad72d6a7146bab8aaf1e73bf98e41e0c0134ef"
    private val grammar3Fingerprint = "sha256:b8bce0f140003d2b4cfd6118c4c034837ed83fde9b9ee669d969c935227e38b4"
    private val vocabFingerprint = "sha256:75749f0af3a9311c29d1c30534f454152ab91a7dca13c8ca04f3984c184001a5"

    private fun text(v: String) = SnapshotValue.Text(v)
    private val kanjiSnapshot = mapOf("character" to text("積"), "onReadings" to SnapshotValue.Texts(listOf("セキ")),
        "kunReadings" to SnapshotValue.Texts(listOf("つ.む", "つ.もる")), "meaningVi" to text("Tích lũy"))
    private val grammarSnapshot = mapOf("pattern" to text("〜ことにしている"), "structure" to text("Vる ＋ ことにしている"), "meaningVi" to text("thói quen"))

    private fun ref(type: ContrastContentType, id: String, snapshot: Map<String, SnapshotValue>, fingerprint: String, schema: Int = 1) =
        SourceReference(type, id, "resolver-src", schema, snapshot, fingerprint)

    private fun item(id: String, source: SourceReference) = ContrastItem(id, id, null, source, ItemDetails.Grammar("m", "c", "n"))

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        assertEquals(ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), clock).import(sourcePack).outcome)
        resolver = RoomContrastSourceResolver(db.contrastDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun theImporterStoresTrimmedValues_andTheirFingerprintIsTheToolsOne() = runTest {
        val row = db.contrastDao().kanjiRows(listOf("rk-k1")).single()
        assertEquals(listOf("セキ") to listOf("つ.む", "つ.もる"), row.onReadings to row.kunReadings)
        assertEquals("Tích lũy", row.meaningVi)
        assertEquals(kanjiFingerprint, ContrastFingerprint.of(ContrastContentType.KANJI, row.toSourceRecord().fields))
        assertTrue(kanjiFingerprint != kanjiFingerprintOfUnstoredValues)
    }

    @Test
    fun matched_changed_missing_ambiguous_unverifiable() = runTest {
        val statuses = resolver.resolve(
            listOf(
                item("ok-kanji", ref(ContrastContentType.KANJI, "rk-k1", kanjiSnapshot, kanjiFingerprint)),
                item("ok-vocab", ref(ContrastContentType.VOCABULARY, "rk-v1", mapOf("expression" to text("冷める"), "reading" to text("さめる"), "meaningVi" to text("bị nguội")), vocabFingerprint)),
                item("changed", ref(ContrastContentType.KANJI, "rk-k1", kanjiSnapshot, kanjiFingerprintOfUnstoredValues)),
                item("missing-one-candidate", ref(ContrastContentType.GRAMMAR, "gone", mapOf("pattern" to text("〜ばかり"), "structure" to text("Vた ＋ ばかり"), "meaningVi" to text("vừa mới")), grammar3Fingerprint)),
                item("missing-none", ref(ContrastContentType.GRAMMAR, "gone", mapOf("pattern" to text("〜ない"), "structure" to text("x"), "meaningVi" to text("y")), grammarFingerprint)),
                item("ambiguous", ref(ContrastContentType.GRAMMAR, "gone", grammarSnapshot, grammarFingerprint)),
                item("future-schema", ref(ContrastContentType.KANJI, "rk-k1", kanjiSnapshot, kanjiFingerprint, schema = 2)),
            ),
        )
        assertEquals(SourceRefStatus.OK, statuses.getValue("ok-kanji").status)
        assertEquals("rk-k1", statuses.getValue("ok-kanji").currentId)
        assertEquals(SourceRefStatus.OK, statuses.getValue("ok-vocab").status)
        assertEquals(SourceRefStatus.SOURCE_CHANGED, statuses.getValue("changed").status)
        // One snapshot match: reported as a candidate, never relinked.
        assertEquals(SourceRefStatus.SOURCE_MISSING, statuses.getValue("missing-one-candidate").status)
        assertEquals(listOf("rk-g3"), statuses.getValue("missing-one-candidate").candidateIds)
        assertEquals(null, statuses.getValue("missing-one-candidate").currentId)
        assertEquals(emptyList<String>(), statuses.getValue("missing-none").candidateIds)
        assertEquals(SourceRefStatus.AMBIGUOUS, statuses.getValue("ambiguous").status)
        assertEquals(listOf("rk-g1", "rk-g2"), statuses.getValue("ambiguous").candidateIds)
        assertEquals(SourceRefStatus.UNVERIFIABLE, statuses.getValue("future-schema").status)
    }

    @Test
    fun aLearnersOwnMeaning_doesNotChangeTheSourceFingerprint() = runTest {
        val edits = OfflineUserContentRepository(db.userContentDao(), clock)
        edits.save(ItemType.KANJI, "rk-k1", mapOf(EditableField.KANJI_MEANING to "Chất đống (nghĩa tôi tự ghi)"))
        edits.save(ItemType.GRAMMAR, "rk-g3", mapOf(EditableField.GRAMMAR_PATTERN to "〜たばかり", EditableField.GRAMMAR_MEANING to "vừa mới xong"))
        // The content rows really hold the learner's values (merged, docs/USER_CONTENT.md)…
        assertEquals("Chất đống (nghĩa tôi tự ghi)", db.contrastDao().kanjiRows(listOf("rk-k1")).single().meaningVi)
        assertEquals("〜たばかり", db.contrastDao().grammarRows(listOf("rk-g3")).single().pattern)
        // …but the fingerprint is computed on the pack values (user_content_edits.base_value).
        val g3Snapshot = mapOf("pattern" to text("〜ばかり"), "structure" to text("Vた ＋ ばかり"), "meaningVi" to text("vừa mới"))
        val statuses = resolver.resolve(
            listOf(
                item("k", ref(ContrastContentType.KANJI, "rk-k1", kanjiSnapshot, kanjiFingerprint)),
                item("g", ref(ContrastContentType.GRAMMAR, "rk-g3", g3Snapshot, grammar3Fingerprint)),
                // Id gone: the edited row is still found by its pack pattern.
                item("gone", ref(ContrastContentType.GRAMMAR, "gone", g3Snapshot, grammar3Fingerprint)),
            ),
        )
        assertEquals(SourceRefStatus.OK, statuses.getValue("k").status)
        assertEquals(SourceRefStatus.OK, statuses.getValue("g").status)
        assertEquals(listOf("rk-g3"), statuses.getValue("gone").candidateIds)
    }

    @Test
    fun aSourcePackUpdateThatChangesTheRow_isSeenAsChanged_andNothingIsDeleted() = runTest {
        val updated = JSONObject(sourcePack).apply {
            getJSONObject("pack").put("version", 2)
            getJSONArray("lessons").getJSONObject(0).getJSONArray("kanji").getJSONObject(0).put("meaningVi", "Chồng chất")
        }.toString()
        assertEquals(ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), clock).import(updated).outcome)
        val status = resolver.resolve(listOf(item("k", ref(ContrastContentType.KANJI, "rk-k1", kanjiSnapshot, kanjiFingerprint)))).getValue("k")
        assertEquals(SourceRefStatus.SOURCE_CHANGED, status.status)
        assertEquals("rk-k1", status.currentId)
    }

    /** Every bundled source row: the app's fingerprint of the Room row = fingerprint.py's of the pack row. */
    @Test
    fun everyBundledSourceRow_hasTheToolsFingerprintInRoom() = runTest {
        val app = instrumentation.targetContext
        AndroidContentImportRepository.BUNDLED_PACK_ASSETS.forEach { asset ->
            val json = app.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            val outcome = ContentPackImporter(db.contentPackDao(), clock).import(json).outcome
            assertTrue(asset, outcome == ImportOutcome.IMPORTED)
        }
        val expected = JSONObject(instrumentation.context.assets.open("contrast/source-fingerprints.json").use { it.readBytes().toString(Charsets.UTF_8) })
            .getJSONArray("rows").let { rows -> (0 until rows.length()).associate { rows.getJSONObject(it).let { r -> (r.getString("type") to r.getString("id")) to r.getString("fingerprint") } } }
        val dao = db.contrastDao()
        val byType = expected.keys.groupBy({ it.first }, { it.second })
        val actual = mutableMapOf<Pair<String, String>, String>()
        byType.forEach { (type, ids) ->
            ids.chunked(500).forEach { chunk ->
                val records = when (type) {
                    "GRAMMAR" -> dao.grammarRows(chunk).map { it.toSourceRecord() }
                    "VOCABULARY" -> dao.vocabularyRows(chunk).map { it.toSourceRecord() }
                    else -> dao.kanjiRows(chunk).map { it.toSourceRecord() }
                }
                records.forEach { actual[it.type.name to it.id] = ContrastFingerprint.of(it.type, it.fields) }
            }
        }
        assertEquals(1959, expected.size)
        assertEquals("rows stored in Room", expected.keys, actual.keys)
        val different = expected.filter { (key, fp) -> actual[key] != fp }.keys
        assertEquals("rows whose Room fingerprint differs from the tool's", emptySet<Pair<String, String>>(), different)
    }
}
