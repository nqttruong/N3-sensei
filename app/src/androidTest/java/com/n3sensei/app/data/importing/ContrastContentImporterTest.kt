package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContrastContentRepository
import com.n3sensei.app.data.repository.OfflineContrastPracticeRepository
import com.n3sensei.app.data.repository.OfflineContrastProgressRepository
import com.n3sensei.app.data.repository.RoomContrastSourceResolver
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.SourceRefStatus
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.contrast.SubmitOutcome
import com.n3sensei.app.domain.contrast.validation.ContrastIssueCode
import com.n3sensei.app.domain.contrast.validation.ContrastSeverity
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The Contrast importer on a real Room database file holding the real bundled source packs.
 * The pack used is a TEST-ONLY copy of docs/contrast/contrast-pack.sample.json (androidTest assets):
 * the unreviewed sample is never shipped nor installed in the app.
 */
@RunWith(AndroidJUnit4::class)
class ContrastContentImporterTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-importer-test.db"
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private var now = Instant.parse("2026-11-17T02:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private lateinit var db: N3Database
    private lateinit var importer: ContrastContentImporter
    private val sample = instrumentation.context.assets.open("contrast/test-pack-sample-copy.json").use { it.readBytes().toString(Charsets.UTF_8) }

    private fun open() {
        db = N3Database.create(context, name)
        importer = ContrastContentImporter(db.contrastDao(), clock)
    }

    @Before
    fun setUp() = runTest {
        context.deleteDatabase(name)
        open()
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

    // ---- pack edits (org.json) ----

    private fun pack(text: String = sample, edit: JSONObject.() -> Unit = {}) = JSONObject(text).apply(edit).toString()
    private fun JSONObject.group(i: Int): JSONObject = getJSONArray("groups").getJSONObject(i)
    private fun JSONObject.question(g: Int, q: Int): JSONObject = group(g).getJSONArray("questions").getJSONObject(q)
    private fun JSONObject.version(v: Int) = getJSONObject("pack").put("contentVersion", v)
    private fun JSONObject.bumpGroup(g: Int, revision: Int) {
        group(g).put("revision", revision)
        group(g).getJSONObject("approval").put("approvedRevision", revision)
    }

    private val content get() = OfflineContrastContentRepository(db.contrastDao())
    private val practice get() = OfflineContrastPracticeRepository(db.contrastDao())
    private val progress get() = OfflineContrastProgressRepository(db.contrastDao())

    private fun learnerSnapshot() = listOf("contrast_attempts", "contrast_sessions", "contrast_session_items", "contrast_progress", "contrast_reviews").associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1, 2").use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "∅" }) }
        }
    }

    private fun contentSnapshot() = listOf("contrast_packs", "contrast_groups", "contrast_items", "contrast_notes", "contrast_examples", "contrast_questions", "contrast_options", "contrast_retired_groups")
        .associateWith { table -> db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1, 2").use { c -> buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "∅" }) } } }

    /** Some learning on the installed pack: a full grammar session (with one wrong answer) and a vocabulary block. */
    private suspend fun learn() {
        var ids = 0
        val start = StartOrResumePractice(content, practice, progress, newId = { "s-${now.epochSecond}-${++ids}" })
        val submit = SubmitContrastAnswer(content, practice, progress)
        listOf("cg-g-koto-ni-shiteiru-naru", "cg-v-sameru-cold-awake").forEach { groupId ->
            val s = (start(PracticeScope.Group(groupId), now, zone) as StartPracticeResult.Started).session
            s.items.forEachIndexed { i, item ->
                val q = content.getQuestion(item.questionId)!!
                val choice = if (i == 0) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId
                assertTrue(submit(s.id, item.position, AnswerInput.Chosen(choice), "req-${s.id}-$i", now, zone) is SubmitOutcome.Recorded)
            }
        }
    }

    // ---- import ----

    @Test
    fun aValidPack_isImported_withItsSourcesVerifiedAgainstRoom_andApprovalsReported() = runTest {
        val report = importer.import(sample)
        assertEquals(ContrastImportReport.Outcome.IMPORTED, report.outcome)
        assertEquals("contrast-n3" to 1, report.packId to report.contentVersion)
        assertTrue(report.issues.none { it.severity == ContrastSeverity.ERROR })
        // Every source matched the real Room rows: no source warning.
        assertTrue(report.issues.none { it.code.name.startsWith("SOURCE_") })
        val installed = content.installedPack()!!
        assertEquals(setOf("cg-g-koto-ni-shiteiru-naru", "cg-v-sameru-cold-awake", "cg-k-seki-accumulate-achieve"), installed.groupIds)
        val detail = content.getDetail("cg-g-koto-ni-shiteiru-naru")!!
        val statuses = RoomContrastSourceResolver(db.contrastDao()).resolve(detail.items)
        assertEquals(setOf(SourceRefStatus.OK), statuses.values.map { it.status }.toSet())
        // Self-approval is allowed and reported, never hidden.
        assertTrue(report.approvals.isNotEmpty() && report.approvals.all { it.selfApproved && it.approvedRevision == it.revision })
    }

    @Test
    fun importingTheSameVersionAgain_isUpToDate_andWritesNothing() = runTest {
        importer.import(sample)
        val before = contentSnapshot()
        now = now.plusSeconds(60)
        val again = importer.import(sample)
        assertEquals(ContrastImportReport.Outcome.UP_TO_DATE, again.outcome)
        assertEquals(1, again.previousVersion)
        assertEquals(before, contentSnapshot()) // installed_at unchanged too
    }

    @Test
    fun anyError_refusesTheWholePack_andLeavesTheInstalledContent() = runTest {
        importer.import(sample)
        val before = contentSnapshot()
        val broken = pack { version(2); question(0, 0).remove("explanationVi") }
        val report = importer.import(broken)
        assertEquals(ContrastImportReport.Outcome.FAILED to ContrastImportReport.Failure.INVALID, report.outcome to report.failure)
        assertTrue(report.issues.any { it.code == ContrastIssueCode.MISSING_EXPLANATION })
        assertEquals(before, contentSnapshot())
        val bad = importer.import("{ not json")
        assertEquals(listOf(ContrastIssueCode.INVALID_JSON), bad.issues.map { it.code })
        assertEquals(before, contentSnapshot())
    }

    @Test
    fun structuralErrors_unknownFields_andAcceptedAnswers_areRefused() = runTest {
        val report = importer.import(pack { question(0, 1).put("acceptedAnswers", JSONArray(listOf("ことになっている"))) })
        assertEquals(ContrastImportReport.Outcome.FAILED, report.outcome)
        assertTrue(report.issues.any { it.code == ContrastIssueCode.SCHEMA_VIOLATION && it.path == "groups[0].questions[1].acceptedAnswers" })
        assertNull(content.installedPack())
    }

    @Test
    fun warnings_unacknowledgedRefused_acknowledgedAccepted() = runTest {
        val unacknowledged = pack { group(1).getJSONObject("approval").put("acknowledgedWarnings", JSONArray()) }
        val refused = importer.import(unacknowledged)
        assertEquals(ContrastImportReport.Outcome.FAILED to ContrastImportReport.Failure.UNACKNOWLEDGED_WARNING, refused.outcome to refused.failure)
        assertTrue(refused.issues.any { it.code == ContrastIssueCode.QUESTION_TYPES_INCOMPLETE && it.acknowledged == false && it.severity == ContrastSeverity.WARNING })
        assertNull(content.installedPack())
        val accepted = importer.import(sample) // its warnings are acknowledged
        assertEquals(ContrastImportReport.Outcome.IMPORTED, accepted.outcome)
        assertTrue(accepted.issues.filter { it.code == ContrastIssueCode.QUESTION_TYPES_INCOMPLETE }.all { it.acknowledged == true })
    }

    @Test
    fun approvalAndPublishingRules() = runTest {
        // A revision without its own approval.
        val stale = importer.import(pack { group(0).put("revision", 2) })
        assertTrue(stale.issues.any { it.code == ContrastIssueCode.MISSING_METADATA && it.path == "groups[0].approval.approvedRevision" })
        assertEquals(ContrastImportReport.Outcome.FAILED, stale.outcome)
        // Not PUBLISHED.
        val approvedOnly = importer.import(pack { group(1).put("status", "APPROVED") })
        assertTrue(approvedOnly.issues.any { it.code == ContrastIssueCode.STATUS_NOT_PUBLISHED })
        // No approval at all.
        val unapproved = importer.import(pack { group(2).remove("approval") })
        assertTrue(unapproved.issues.any { it.code == ContrastIssueCode.MISSING_METADATA && it.path == "groups[2].approval" })
        assertNull(content.installedPack())
    }

    @Test
    fun revisionsAndItemsAreComparedWithTheInstalledContent() = runTest {
        importer.import(sample)
        val notBumped = importer.import(pack { version(2); group(0).put("summaryVi", "Tóm tắt đã sửa."); question(0, 0).put("explanationVi", "Đã sửa.") })
        assertEquals(ContrastImportReport.Outcome.FAILED, notBumped.outcome)
        assertEquals(setOf("groups[0].revision", "groups[0].questions[0].revision"),
            notBumped.issues.filter { it.code == ContrastIssueCode.REVISION_NOT_INCREMENTED }.map { it.path }.toSet())
        val itemsChanged = importer.import(pack {
            version(2); bumpGroup(1, 2)
            group(1).getJSONArray("items").getJSONObject(1).put("id", "ci-v-sameru-wake")
            val values = group(1).getJSONArray("comparison").getJSONObject(0).getJSONObject("values")
            values.put("ci-v-sameru-wake", values.remove("ci-v-sameru-awake"))
            group(1).getJSONArray("examples").getJSONObject(1).put("itemId", "ci-v-sameru-wake")
            group(1).getJSONArray("commonMistakes").getJSONObject(0).put("expectedItemId", "ci-v-sameru-wake")
            question(1, 0).put("revision", 2).getJSONArray("options").getJSONObject(1).put("itemId", "ci-v-sameru-wake")
            question(1, 1).put("revision", 2).put("targetItemId", "ci-v-sameru-wake").getJSONArray("options").getJSONObject(0).put("itemId", "ci-v-sameru-wake")
        })
        assertEquals(ContrastImportReport.Outcome.FAILED, itemsChanged.outcome)
        assertTrue(itemsChanged.issues.any { it.code == ContrastIssueCode.ITEMS_CHANGED_SAME_GROUP_ID && it.path == "groups[1].items" })
        assertEquals(1, content.installedPack()!!.contentVersion)
    }

    @Test
    fun aDatabaseFailureInsideTheTransaction_rollsBack() = runTest {
        importer.import(sample)
        val before = contentSnapshot()
        val v2 = pack { version(2); bumpGroup(0, 2); group(0).put("summaryVi", "Tóm tắt mới.") }
        // A trigger makes the last insert of the replacement fail, after the old content was deleted.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_contrast_insert BEFORE INSERT ON contrast_options WHEN NEW.question_id = 'cq-k-seki-mc1' BEGIN SELECT RAISE(ABORT, 'simulated failure'); END",
        )
        val report = importer.import(v2)
        assertEquals(ContrastImportReport.Outcome.FAILED to ContrastImportReport.Failure.DATABASE, report.outcome to report.failure)
        assertTrue(report.databaseError!!.contains("simulated failure"))
        assertEquals(before, contentSnapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_contrast_insert")
        assertEquals(ContrastImportReport.Outcome.IMPORTED, importer.import(v2).outcome)
    }

    // ---- history preservation ----

    @Test
    fun contentUpdates_neverLoseOrRewriteLearnerHistory() = runTest {
        assertEquals(ContrastImportReport.Outcome.IMPORTED, importer.import(sample).outcome)
        learn()
        val learner = learnerSnapshot()
        val attemptsBefore = practice.attemptsOfGroup("cg-g-koto-ni-shiteiru-naru")
        val grammarProgress = progress.progress("cg-g-koto-ni-shiteiru-naru")
        val vocabProgress = progress.progress("cg-v-sameru-cold-awake")
        assertTrue(attemptsBefore.isNotEmpty() && grammarProgress != null && vocabProgress != null)

        // v2: theory edited (group revision 2), a new question, the answer of fb1 changed as a NEW question
        // (old id removed), and the vocabulary group retired and replaced by a group with another id.
        now = now.plusSeconds(3600)
        val v2 = pack {
            version(2)
            bumpGroup(0, 2)
            group(0).getJSONArray("theory").getJSONObject(0).put("bodyVi", "Giải thích đã được biên tập lại.")
            val questions = group(0).getJSONArray("questions")
            val fb1 = JSONObject(questions.getJSONObject(1).toString()).put("id", "cq-g-koto-fb1b").put("correctOptionId", "b")
            questions.put(1, fb1) // cq-g-koto-fb1 is gone, replaced by a new id
            questions.put(JSONObject(questions.getJSONObject(2).toString()).put("id", "cq-g-koto-fb3")
                .put("prompt", JSONObject().put("japanese", "法律で、たばこは二十歳から吸える＿＿＿。").put("furigana", "{法律|ほうりつ}で、たばこは{二十歳|はたち}から{吸|す}える＿＿＿。"))
                .put("correctOptionId", "b").put("targetItemId", "ci-g-koto-ni-natteiru").put("explanationVi", "Luật định."))
            val vocab = group(1)
            val replacement = JSONObject(vocab.toString()).put("id", "cg-v-sameru-v2")
            fun renameAll(o: Any?): Any? = when (o) {
                is JSONObject -> o.apply { keys().asSequence().toList().forEach { k -> put(k.replace("-sameru-", "-sameru2-"), renameAll(remove(k))) } }
                is JSONArray -> o.apply { (0 until length()).forEach { put(it, renameAll(get(it))) } }
                is String -> if (o.startsWith("ci-v-sameru-") || o.startsWith("cx-v-sameru-") || o.startsWith("cm-v-sameru-") || o.startsWith("ce-v-sameru-") || o.startsWith("ct-v-sameru-") || o.startsWith("cq-v-sameru-")) o.replace("-sameru-", "-sameru2-") else if (o == "cg-v-sameru-cold-awake") "cg-v-sameru-v2" else o
                else -> o
            }
            renameAll(replacement)
            replacement.put("id", "cg-v-sameru-v2")
            replacement.getJSONObject("approval").getJSONArray("acknowledgedWarnings").getJSONObject(0).put("target", "cg-v-sameru-v2")
            getJSONArray("groups").put(1, replacement)
            put("retiredGroups", JSONArray().put(JSONObject().put("id", "cg-v-sameru-cold-awake").put("retiredInVersion", 2).put("reasonVi", "Thay bằng nhóm mới").put("replacedBy", "cg-v-sameru-v2")))
        }
        val report = importer.import(v2)
        assertEquals(ContrastContentImporter.summary(report), ContrastImportReport.Outcome.IMPORTED, report.outcome)
        // Same learner rows, byte for byte.
        assertEquals(learner, learnerSnapshot())
        assertEquals(attemptsBefore, practice.attemptsOfGroup("cg-g-koto-ni-shiteiru-naru"))
        assertEquals(grammarProgress, progress.progress("cg-g-koto-ni-shiteiru-naru"))
        assertEquals(vocabProgress, progress.progress("cg-v-sameru-cold-awake"))
        // The removed question's attempts keep their snapshots and original grading.
        val oldFb1 = attemptsBefore.filter { it.questionId == "cq-g-koto-fb1" }
        assertTrue(oldFb1.isNotEmpty())
        assertNull(content.getQuestion("cq-g-koto-fb1"))
        assertTrue(oldFb1.all { it.correctText.isNotEmpty() && it.contentVersion == 1 })
        // Retired group: history kept, not offered, reason readable; no progress moved to its replacement.
        assertEquals("Thay bằng nhóm mới", content.retiredGroup("cg-v-sameru-cold-awake")!!.reasonVi)
        assertNull(content.getDetail("cg-v-sameru-cold-awake"))
        assertNull(progress.progress("cg-v-sameru-v2"))
        assertTrue(practice.attemptsOfGroup("cg-v-sameru-cold-awake").isNotEmpty())
        assertTrue("cg-v-sameru-cold-awake" !in progress.dueGroups(now.plusSeconds(86_400 * 90), 100))
        assertEquals(2 to 6, content.getDetail("cg-g-koto-ni-shiteiru-naru")!!.group.let { it.revision to it.questionCount })

        // The same v2 again: up to date. The older v1 again: refused, nothing changes.
        val contentV2 = contentSnapshot()
        assertEquals(ContrastImportReport.Outcome.UP_TO_DATE, importer.import(v2).outcome)
        val older = importer.import(sample)
        assertTrue(older.outcome != ContrastImportReport.Outcome.IMPORTED)
        assertEquals(contentV2, contentSnapshot())
        assertEquals(learner, learnerSnapshot())
        assertEquals(1, db.countRows("contrast_packs"))
    }

    @Test
    fun aRestart_keepsInstalledContentAndProgress() = runTest {
        importer.import(sample)
        learn()
        val learner = learnerSnapshot()
        db.close()
        open()
        assertEquals(ContrastImportReport.Outcome.UP_TO_DATE, importer.import(sample).outcome)
        assertEquals(learner, learnerSnapshot())
    }
}
