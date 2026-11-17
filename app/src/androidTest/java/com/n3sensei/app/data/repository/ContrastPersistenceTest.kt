package com.n3sensei.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ComparisonRow
import com.n3sensei.app.domain.contrast.CommonMistake
import com.n3sensei.app.domain.contrast.Confusion
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.ContrastExample
import com.n3sensei.app.domain.contrast.ContrastFilter
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ContrastOption
import com.n3sensei.app.domain.contrast.ContrastProgress
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.ContrastReview
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.Correction
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.MemoryTip
import com.n3sensei.app.domain.contrast.PendingReview
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.QuestionContext
import com.n3sensei.app.domain.contrast.QuestionPrompt
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.Sentence
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.SourceReference
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.contrast.SubmitOutcome
import com.n3sensei.app.domain.contrast.TheoryBlock
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastGroup
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastPack
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.repository.RecordAnswerResult
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Contrast Lab persistence (v16) in a real Room database FILE: DAO reads and filters, unique
 * constraints, the recordAnswer transaction (idempotency, rollback), restarts, and content
 * updates that never lose learner history. Test content is built here (the unreviewed sample pack
 * is never put in the app database).
 */
@RunWith(AndroidJUnit4::class)
class ContrastPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "contrast-persistence-test.db"
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val t0 = Instant.parse("2026-11-17T02:00:00Z")
    private lateinit var db: N3Database
    private lateinit var content: OfflineContrastContentRepository
    private lateinit var practice: OfflineContrastPracticeRepository
    private lateinit var progress: OfflineContrastProgressRepository

    private fun open() {
        db = N3Database.create(context, name)
        content = OfflineContrastContentRepository(db.contrastDao())
        practice = OfflineContrastPracticeRepository(db.contrastDao())
        progress = OfflineContrastProgressRepository(db.contrastDao())
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

    // ---- Test content: a 2-item grammar group and a 3-item vocabulary group ----

    private fun source(type: ContrastContentType, id: String, vararg fields: Pair<String, String>) =
        SourceReference(type, id, "src-pack", 1, fields.associate { it.first to SnapshotValue.Text(it.second) }, "sha256:" + "a".repeat(64))

    private fun option(id: String, text: String, item: String?) = ContrastOption(id, text, null, item, if (item == null) "Nhiễu" else null)

    private fun question(id: String, group: String, type: ContrastQuestionType, target: String, options: List<ContrastOption>, correct: String, revision: Int = 1) =
        ContrastQuestion(
            id, group, revision, type, 1, target, "Chọn đáp án.",
            prompt = if (type == ContrastQuestionType.FILL_IN_THE_BLANK || type == ContrastQuestionType.ERROR_DETECTION) QuestionPrompt(options.joinToString("") { it.text }, null, "dịch") else null,
            context = if (type == ContrastQuestionType.CONTEXT_SELECTION) QuestionContext("Tình huống", null, null) else null,
            options = options, correctOptionId = correct,
            correction = if (type == ContrastQuestionType.ERROR_DETECTION) Correction("ことにしている。", null, target) else null,
            explanationVi = "Giải thích $id",
        )

    private fun group(
        id: String,
        type: ContrastContentType,
        items: List<ContrastItem>,
        questions: List<ContrastQuestion>,
        revision: Int = 1,
        title: String = "Nhóm $id",
    ): ValidatedContrastGroup {
        val summary = ContrastGroup(id, "contrast-test", type, "N3", title, "Tóm tắt quy định", revision, items.map { it.label }, questions.size)
        val detail = ContrastDetail(
            group = summary,
            items = items,
            theory = listOf(TheoryBlock("$id-t1", "Lý thuyết", "Nội dung")),
            comparison = listOf(ComparisonRow("Khía cạnh", items.associate { it.id to "giá trị ${it.id}" })),
            examples = listOf(ContrastExample("$id-x1", items[0].id, "私は日記を書く。", "{私|わたし}は{日記|にっき}を{書|か}く。", "Tôi viết nhật ký.")),
            memoryTips = listOf(MemoryTip("$id-m1", "Mẹo")),
            commonMistakes = listOf(CommonMistake("$id-e1", "Khi…", Sentence("誤", null), Sentence("正", null), "Vì…", Confusion(items[0].id, items[1].id))),
            licenseText = "Giấy phép thử",
            licenseVerified = false,
        )
        return ValidatedContrastGroup(detail, questions)
    }

    private val grammar = group(
        "cg-grammar", ContrastContentType.GRAMMAR,
        listOf(
            ContrastItem("gi-a", "〜ことにしている", null, source(ContrastContentType.GRAMMAR, "n3g-g001", "pattern" to "〜ことにしている"), ItemDetails.Grammar("thói quen", "Vる＋ことにしている", "tự quyết")),
            ContrastItem("gi-b", "〜ことになっている", null, source(ContrastContentType.GRAMMAR, "n3g-g002", "pattern" to "〜ことになっている"), ItemDetails.Grammar("quy định", "Vる＋ことになっている", "người khác quyết")),
        ),
        listOf(
            question("gq-mc", "cg-grammar", ContrastQuestionType.MULTIPLE_CHOICE, "gi-b", listOf(option("a", "Aにしている", "gi-a"), option("b", "Bになっている", "gi-b"), option("c", "Cにした", null)), "b"),
            question("gq-fb", "cg-grammar", ContrastQuestionType.FILL_IN_THE_BLANK, "gi-a", listOf(option("a", "ことにしている", "gi-a"), option("b", "ことになっている", "gi-b")), "a"),
            question("gq-ed", "cg-grammar", ContrastQuestionType.ERROR_DETECTION, "gi-a", listOf(option("a", "私は", null), option("b", "毎日", null), option("c", "ことになっている。", "gi-b")), "c"),
            question("gq-cs", "cg-grammar", ContrastQuestionType.CONTEXT_SELECTION, "gi-b", listOf(option("a", "規則です。", "gi-b"), option("b", "習慣です。", "gi-a"), option("c", "決めた。", null)), "a"),
        ),
    )

    private val vocabulary = group(
        "cg-vocab3", ContrastContentType.VOCABULARY,
        listOf(
            ContrastItem("vi-a", "冷める", "{冷|さ}める", source(ContrastContentType.VOCABULARY, "mimikara-n3-v211"), ItemDetails.Vocabulary("さめる", "nguội", "nhiệt", emptyList())),
            ContrastItem("vi-b", "覚める", "{覚|さ}める", source(ContrastContentType.VOCABULARY, "mimikara-n3-v505"), ItemDetails.Vocabulary("さめる", "tỉnh", "ý thức", emptyList())),
            ContrastItem("vi-c", "冷える", null, source(ContrastContentType.VOCABULARY, "mimikara-n3-v209"), ItemDetails.Vocabulary("ひえる", "bị lạnh", "nhiệt độ", emptyList())),
        ),
        listOf(
            question("vq-1", "cg-vocab3", ContrastQuestionType.MULTIPLE_CHOICE, "vi-a", listOf(option("a", "冷める", "vi-a"), option("b", "覚める", "vi-b"), option("c", "冷える", "vi-c")), "a"),
            question("vq-2", "cg-vocab3", ContrastQuestionType.CONTEXT_SELECTION, "vi-b", listOf(option("a", "目が冷めた", "vi-a"), option("b", "目が覚めた", "vi-b"), option("c", "目が冷えた", "vi-c")), "b"),
        ),
    )

    private fun pack(version: Int = 1, groups: List<ValidatedContrastGroup> = listOf(grammar, vocabulary), retired: List<RetiredGroup> = emptyList()) =
        ValidatedContrastPack("contrast-test", "Gói thử", "N3", version, "2026-11-17", "Giấy phép thử", false, groups, retired)

    private suspend fun install(p: ValidatedContrastPack = pack()) = content.replaceInstalledPack(p, schemaVersion = 1, now = t0)

    private fun attempt(requestId: String, session: String, position: Int, group: String = "cg-grammar", question: String = "gq-mc", outcome: AnswerOutcome = AnswerOutcome.CORRECT) =
        ContrastAttempt(requestId, session, position, group, 1, question, 1, ContrastQuestionType.MULTIPLE_CHOICE, "contrast-test", 1, outcome,
            if (outcome == AnswerOutcome.SKIPPED) null else "b", "b", "gi-b", if (outcome == AnswerOutcome.INCORRECT) "gi-a" else "gi-b",
            if (outcome == AnswerOutcome.SKIPPED) null else "Bになっている", "Bになっている", t0)

    private fun pendingReview(session: String, group: String = "cg-grammar", reviewCount: Int = 1) = PendingReview(
        ContrastReview(0, group, session, t0, 3, 3, 0, 0, ReviewRating.GOOD, null, 1, t0.plus(Duration.ofDays(3)), 1),
        ContrastProgress(group, 1, t0.plus(Duration.ofDays(3)), t0, reviewCount, 0, t0, t0),
    )

    private suspend fun session(id: String, items: Int = 3) = practice.createSession(
        ContrastSession(id, PracticeScope.Group("cg-grammar"), SessionStatus.ACTIVE, t0, t0, 1, (0 until items).map { SessionItem(it, "cg-grammar", "gq-mc", 1, 0) }),
    )

    // ---- DAO ----

    @Test
    fun contentRoundTrips_withTwoAndThreeItemGroups() = runTest {
        install()
        assertEquals(grammar.detail, content.getDetail("cg-grammar"))
        assertEquals(vocabulary.detail, content.getDetail("cg-vocab3"))
        assertEquals(3, content.getDetail("cg-vocab3")!!.items.size)
        assertEquals(grammar.questions, content.getQuestions("cg-grammar"))
        assertEquals(vocabulary.questions[1], content.getQuestion("vq-2"))
        val installed = content.installedPack()!!
        assertEquals(1 to setOf("cg-grammar", "cg-vocab3"), installed.contentVersion to installed.groupIds)
        // No foreign key from Contrast items to the existing content tables (D3): nothing there is needed.
        assertEquals(0, db.countRows("grammar"))
    }

    @Test
    fun filterAndAccentInsensitiveSearch() = runTest {
        install()
        assertEquals(listOf("cg-grammar", "cg-vocab3"), content.observeGroups().first().map { it.id })
        assertEquals(listOf("cg-vocab3"), content.observeGroups(ContrastFilter(contentType = ContrastContentType.VOCABULARY)).first().map { it.id })
        // "thói quen" (a grammar item's meaning) found without accents or case.
        assertEquals(listOf("cg-grammar"), content.observeGroups(ContrastFilter(query = "Thoi Quen")).first().map { it.id })
        assertEquals(listOf("cg-grammar"), content.observeGroups(ContrastFilter(query = "ことにしている")).first().map { it.id })
        assertEquals(listOf("cg-vocab3"), content.observeGroups(ContrastFilter(query = "覚める")).first().map { it.id })
        assertEquals(emptyList<String>(), content.observeGroups(ContrastFilter(query = "100%")).first().map { it.id })
        val row = content.observeGroups().first().single { it.id == "cg-vocab3" }
        assertEquals(listOf("冷める", "覚める", "冷える") to 2, row.itemLabels to row.questionCount)
    }

    @Test
    fun uniqueConstraints_andAFailedInstall_rollsBack() = runTest {
        install()
        // A pack whose two groups share an item id violates the primary key: nothing of it stays.
        val broken = pack(version = 2, groups = listOf(grammar, vocabulary.copy(detail = vocabulary.detail.copy(items = listOf(grammar.detail.items[0]) + vocabulary.detail.items.drop(1)))))
        try {
            install(broken)
            fail("expected a constraint failure")
        } catch (e: SQLiteConstraintException) {
            // expected
        }
        assertEquals(1, content.installedPack()!!.contentVersion)
        assertEquals(vocabulary.detail, content.getDetail("cg-vocab3"))
        session("s1")
        try {
            session("s1")
            fail("duplicate session id accepted")
        } catch (e: SQLiteConstraintException) {
            // expected
        }
    }

    @Test
    fun dueNewAndOverview() = runTest {
        install()
        assertEquals(listOf("cg-grammar", "cg-vocab3"), progress.newGroups(10))
        assertEquals(listOf("cg-vocab3"), progress.newGroups(10, ContrastFilter(contentType = ContrastContentType.VOCABULARY)))
        session("s1")
        assertEquals(RecordAnswerResult.RECORDED, practice.recordAnswer(attempt("r1", "s1", 0), pendingReview("s1")))
        assertEquals(listOf("cg-vocab3"), progress.newGroups(10))
        assertEquals(emptyList<String>(), progress.dueGroups(t0, 10))
        assertEquals(listOf("cg-grammar"), progress.dueGroups(t0.plus(Duration.ofDays(3)), 10))
        assertEquals(1, progress.newGroupsIntroducedBetween(t0.minusSeconds(1), t0.plusSeconds(1)))
        assertEquals(0, progress.newGroupsIntroducedBetween(t0.plusSeconds(1), t0.plus(Duration.ofDays(1))))
        val overview = progress.observeOverview(t0).first()
        assertEquals(Triple(0, 1, t0.plus(Duration.ofDays(3))), Triple(overview.dueCount, overview.scheduledCount, overview.nextDueAt))
    }

    @Test
    fun sessions_persistAcrossARestart_andOnlyOneIsActive() = runTest {
        install()
        session("s1")
        session("s2")
        assertEquals(SessionStatus.ABANDONED, practice.session("s1")!!.status)
        practice.appendItems("s2", listOf(SessionItem(3, "cg-grammar", "gq-fb", 1, null)), t0.plusSeconds(5))
        practice.recordAnswer(attempt("r1", "s2", 0, outcome = AnswerOutcome.SKIPPED), null)
        db.close()
        open()
        val s2 = practice.activeSession()!!
        assertEquals("s2", s2.id)
        assertEquals(4, s2.items.size)
        assertNull(s2.items[3].blockIndex)
        assertEquals(AnswerOutcome.SKIPPED, practice.attempts("s2").single().outcome)
        practice.finishSession("s2", SessionStatus.COMPLETED, t0.plusSeconds(9))
        assertNull(practice.activeSession())
        assertEquals(SessionStatus.COMPLETED, practice.session("s2")!!.status)
    }

    // ---- Transaction ----

    @Test
    fun recordAnswer_isIdempotent_andNeverSchedulesTwice() = runTest {
        install()
        session("s1")
        assertEquals(RecordAnswerResult.RECORDED, practice.recordAnswer(attempt("r1", "s1", 0), pendingReview("s1")))
        // The same request again: nothing written, review count unchanged.
        assertEquals(RecordAnswerResult.DUPLICATE, practice.recordAnswer(attempt("r1", "s1", 0), pendingReview("s1", reviewCount = 2)))
        // Another request for the same position: refused.
        assertEquals(RecordAnswerResult.ALREADY_ANSWERED, practice.recordAnswer(attempt("r2", "s1", 0, outcome = AnswerOutcome.INCORRECT), null))
        assertEquals(1, db.countRows("contrast_attempts"))
        assertEquals(1, db.countRows("contrast_reviews"))
        assertEquals(1, progress.progress("cg-grammar")!!.reviewCount)
        assertEquals(t0, practice.session("s1")!!.lastActivityAt)
    }

    @Test
    fun aFailureInsideTheTransaction_rollsBackTheAttemptToo() = runTest {
        install()
        session("s1")
        practice.recordAnswer(attempt("r1", "s1", 0), pendingReview("s1"))
        val before = progress.progress("cg-grammar")
        // A second scheduling of the same block (same session and group) breaks UNIQUE(session_id, group_id)
        // after the attempt row was inserted: the whole answer must be rolled back.
        try {
            practice.recordAnswer(attempt("r2", "s1", 1), pendingReview("s1", reviewCount = 2))
            fail("expected a constraint failure")
        } catch (e: SQLiteConstraintException) {
            // expected
        }
        assertNull(db.contrastDao().attempt("r2"))
        assertEquals(1, db.countRows("contrast_attempts"))
        assertEquals(1, db.countRows("contrast_reviews"))
        assertEquals(before, progress.progress("cg-grammar"))
        // A plain retry without the faulty review succeeds afterwards.
        assertEquals(RecordAnswerResult.RECORDED, practice.recordAnswer(attempt("r2", "s1", 1), null))
    }

    @Test
    fun learnerData_survivesARestart() = runTest {
        install()
        session("s1")
        practice.recordAnswer(attempt("r1", "s1", 0, outcome = AnswerOutcome.INCORRECT), pendingReview("s1"))
        val attempts = practice.attempts("s1")
        val p = progress.progress("cg-grammar")
        val reviews = progress.reviews("cg-grammar")
        db.close()
        open()
        assertEquals(attempts, practice.attempts("s1"))
        assertEquals(p, progress.progress("cg-grammar"))
        assertEquals(reviews, progress.reviews("cg-grammar"))
        assertEquals(mapOf("gq-mc" to t0), practice.lastAnsweredAt("cg-grammar"))
        assertEquals(attempts, practice.attemptsBetween(t0, t0.plusSeconds(1)))
        assertEquals(attempts, practice.attemptsOfGroup("cg-grammar"))
    }

    // ---- Content updates never lose history (D4) ----

    @Test
    fun aContentUpdate_keepsAttemptsProgressScheduleAndSessions_andRetiredGroupsKeepTheirHistory() = runTest {
        install()
        session("s1")
        practice.recordAnswer(attempt("r1", "s1", 0), pendingReview("s1"))
        practice.recordAnswer(attempt("r2", "s1", 1, group = "cg-vocab3", question = "vq-1", outcome = AnswerOutcome.INCORRECT), pendingReview("s1", group = "cg-vocab3"))
        val attemptsBefore = practice.attempts("s1")
        val grammarProgress = progress.progress("cg-grammar")
        val vocabProgress = progress.progress("cg-vocab3")

        // v2: the MC answer changes (new id, old question removed), group revision 2, the 3-item group is retired.
        val newMc = question("gq-mc2", "cg-grammar", ContrastQuestionType.MULTIPLE_CHOICE, "gi-a", listOf(option("a", "Aにしている", "gi-a"), option("b", "Bになっている", "gi-b")), "a")
        val grammar2 = grammar.copy(
            detail = grammar.detail.copy(group = grammar.detail.group.copy(revision = 2, questionCount = 4)),
            questions = listOf(newMc) + grammar.questions.drop(1),
        )
        install(pack(version = 2, groups = listOf(grammar2), retired = listOf(RetiredGroup("cg-vocab3", 2, "Gộp nhóm", null))))

        assertEquals(2, content.installedPack()!!.contentVersion)
        assertEquals(attemptsBefore, practice.attempts("s1")) // including the original grading and snapshots
        assertEquals("Bになっている", practice.attempts("s1").first().correctText)
        assertNull(content.getQuestion("gq-mc")) // gone from the content…
        assertEquals("gq-mc", practice.attemptsOfGroup("cg-grammar").single().questionId) // …its attempt stays
        assertEquals(grammarProgress, progress.progress("cg-grammar")) // not reset to NEW
        assertEquals(vocabProgress, progress.progress("cg-vocab3")) // retired group keeps its progress
        assertEquals(2, db.countRows("contrast_reviews"))
        assertNotNull(practice.session("s1"))
        assertEquals("Gộp nhóm", content.retiredGroup("cg-vocab3")!!.reasonVi)
        assertNull(content.getDetail("cg-vocab3"))
        assertEquals(1, practice.attemptsOfGroup("cg-vocab3").size)
        // A retired group is never due; the kept group still is.
        assertEquals(listOf("cg-grammar"), progress.dueGroups(t0.plus(Duration.ofDays(30)), 10))
        assertTrue(progress.newGroups(10).isEmpty())
    }

    // ---- The M1 use cases on Room ----

    @Test
    fun useCasesOnRoom_startAnswerSchedule() = runTest {
        install()
        var ids = 0
        val start = StartOrResumePractice(content, practice, progress, newId = { "room-${++ids}" })
        val submit = SubmitContrastAnswer(content, practice, progress)
        val s = (start(PracticeScope.Group("cg-grammar"), t0, zone) as StartPracticeResult.Started).session
        assertEquals(s, practice.activeSession())
        s.items.forEach { item ->
            val q = content.getQuestion(item.questionId)!!
            assertTrue(submit(s.id, item.position, AnswerInput.Chosen(q.correctOptionId), "req-${item.position}", t0, zone) is SubmitOutcome.Recorded)
        }
        assertEquals(SubmitOutcome.Duplicate, submit(s.id, 0, AnswerInput.Skipped, "req-0", t0, zone))
        assertEquals(1, progress.progress("cg-grammar")!!.stepIndex)
        assertEquals(SessionStatus.COMPLETED, practice.session(s.id)!!.status)
        assertEquals(s.items.size, db.countRows("contrast_attempts"))
        assertEquals(1, db.countRows("contrast_reviews"))
    }
}
