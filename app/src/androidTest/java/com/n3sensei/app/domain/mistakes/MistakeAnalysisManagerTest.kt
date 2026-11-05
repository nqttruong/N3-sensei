package com.n3sensei.app.domain.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamPassage
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.SaveExamResult
import com.n3sensei.app.testing.MutableTestClock
import com.n3sensei.app.testing.SeedData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mistake analysis on a real database with the SEED TEST DATA content pack, and a TEST DATA ONLY
 * exam set whose questions link to the seed items (and to one item that is not in the app).
 */
@RunWith(AndroidJUnit4::class)
class MistakeAnalysisManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "mistake-analysis-test.db"
    private val clock = MutableTestClock(1_000_000)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var progress: OfflineProgressRepository
    private lateinit var manager: MistakeAnalysisManager

    private val kanji = ItemRef(ItemType.KANJI, SeedData.K_KEI)
    private val keiken = ItemRef(ItemType.VOCABULARY, SeedData.V_KEIKEN)
    private val fueru = ItemRef(ItemType.VOCABULARY, SeedData.V_FUERU)
    private val tabakari = ItemRef(ItemType.GRAMMAR, SeedData.G_TABAKARI)
    private val gone = ItemRef(ItemType.GRAMMAR, "not-in-the-app")

    private fun q(id: String, section: String, type: QuestionType, related: List<ItemRef> = emptyList(), explanation: String? = "Lời giải $id", passage: String? = null) =
        ExamQuestion(id, section, type, AnswerFormat.CHOICE, "Câu $id", null, listOf("A", "B", "C"), 0, emptyList(), explanation, null, passage, null, 1, related)

    private val set = ExamSet(
        "mistake-set", "Đề phân tích", null, "N3", ExamType.FULL, 1_800, null, 0,
        sections = listOf(
            ExamSection("m-s1", ExamSectionKind.VOCABULARY, null, 1), ExamSection("m-s2", ExamSectionKind.GRAMMAR, null, 2),
            ExamSection("m-s3", ExamSectionKind.READING, null, 3), ExamSection("m-s4", ExamSectionKind.LISTENING, null, 4),
        ),
        passages = listOf(ExamPassage("m-audio", null, null, null, "exam/m/1.mp3")),
        questions = listOf(
            q("qk", "m-s1", QuestionType.KANJI_READING, listOf(kanji, keiken)),
            q("qv", "m-s1", QuestionType.CONTEXT, listOf(fueru, keiken)).copy(order = 2),
            q("qg", "m-s2", QuestionType.GRAMMAR_FORM, listOf(tabakari, gone)),
            q("qr", "m-s3", QuestionType.READING_SHORT, explanation = null),
            q("ql", "m-s4", QuestionType.LISTENING_POINT, passage = "m-audio"),
        ),
    )

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        progress = OfflineProgressRepository(db.progressDao(), clock)
        manager = MistakeAnalysisManager(exams, OfflineContentRepository(db.contentDao(), db.contentPackDao()), progress)
        runBlocking {
            db.contentPackDao().replacePack(SeedData.bundle())
            assertEquals(SaveExamResult.Saved, exams.saveExamSet(set))
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    /** One attempt: question id → option picked (missing: left blank). */
    private suspend fun attempt(picks: Map<String, Int>): String {
        val a = exams.startAttempt(set.id)!!
        picks.forEach { (id, index) -> assertNotNull(exams.answer(a.id, id, AnswerInput.Choice(index), 3_000)) }
        clock.advance(600_000)
        exams.submit(a.id)
        clock.advance(86_400_000)
        return a.id
    }

    @Test
    fun eachMissedQuestion_getsItsAnswersCategoryKnowledgeAndFix() = runBlocking {
        val id = attempt(mapOf("qk" to 1, "qv" to 2, "qg" to 1, "qr" to 2))
        val result = manager.analyzeAttempt(id)!!
        val byId = result.analyses.associateBy { it.question.id }
        assertEquals(listOf("qk", "qv", "qg", "qr", "ql"), result.analyses.map { it.question.id })

        val k = byId.getValue("qk")
        assertEquals(ErrorCategory.KANJI_CONFUSION, k.category)
        assertEquals("B", k.givenAnswer)
        assertEquals("A", k.correctAnswer)
        assertEquals("Lời giải qk", k.explanation)
        assertEquals(setOf(kanji, keiken), k.knowledge.map { it.ref }.toSet())
        assertTrue(k.knowledge.all { it.item != null })
        assertEquals(k.knowledge.map { Recommendation.StudyItem(it) }, k.recommendations)

        // Linked items come back in no particular order.
        assertEquals(setOf(fueru, keiken), (byId.getValue("qv").recommendations.first() as Recommendation.PracticeWords).refs.toSet())

        // A linked item that is not in the app is listed as missing; the one found is studied.
        val g = byId.getValue("qg")
        assertEquals(ErrorCategory.GRAMMAR_CONFUSION, g.category)
        assertNull(g.knowledge.single { it.ref == gone }.item)
        assertEquals(listOf(tabakari), g.recommendations.filterIsInstance<Recommendation.StudyItem>().map { it.knowledge.ref })

        val r = byId.getValue("qr")
        assertEquals(ErrorCategory.READING_MISUNDERSTANDING, r.category)
        assertNull(r.explanation) // no explanation in the set: none is made up

        val l = byId.getValue("ql")
        assertEquals(ErrorCategory.UNKNOWN, l.category) // left blank
        assertEquals(listOf(Evidence.LeftBlank), l.evidence)
        assertNull(l.givenAnswer)

        // Linked through the word both questions test, not through the option picked.
        assertEquals(LinkBasis.SameKnowledge(keiken), k.repeat.linked.single { it.questionId == "qv" }.basis)
    }

    @Test
    fun theSameMistakeAgain_isCountedRaisedAndKeptInTheHistory() = runBlocking {
        attempt(mapOf("qk" to 1))
        val second = attempt(mapOf("qk" to 2, "qv" to 0, "qg" to 0, "qr" to 0, "ql" to 0))
        val k = manager.analyzeAttempt(second)!!.analyses.single()
        assertEquals("qk", k.question.id)
        assertEquals(2, k.repeat.timesMissed)
        assertEquals(2, k.repeat.timesSeen)
        assertTrue(k.repeat.priority >= ReviewPriority.RAISED)
        assertEquals(2, exams.getErrorForQuestion("qk")!!.errorCount)
        assertEquals(10, exams.getGradedHistory().size)
    }

    @Test
    fun aMissAfterTwoRightAnswers_isCareless_withTheEvidence() = runBlocking {
        attempt(mapOf("qk" to 0))
        attempt(mapOf("qk" to 0))
        val third = attempt(mapOf("qk" to 1))
        val k = manager.analyzeAttempt(third)!!.analyses.first { it.question.id == "qk" }
        assertEquals(ErrorCategory.CARELESS_MISTAKE, k.category)
        assertEquals(listOf(Evidence.RightBefore(2)), k.evidence)
    }

    @Test
    fun weakPoints_andTheLatestMissOfAQuestion_andMarkingWordsMarksThem() = runBlocking {
        attempt(mapOf("qk" to 1, "qv" to 0, "qg" to 0, "qr" to 0, "ql" to 0))
        attempt(mapOf("qk" to 1, "qv" to 0, "qg" to 0, "qr" to 0, "ql" to 0))
        attempt(mapOf("qk" to 1, "qv" to 0, "qg" to 1, "qr" to 0, "ql" to 0))
        val k = manager.questionMistakes("qk")
        assertEquals(3, k.history.size)
        assertEquals(ReviewPriority.HIGH, k.analysis!!.repeat.priority)
        // A question never missed has its history and no analysis.
        val v = manager.questionMistakes("qv")
        assertEquals(3, v.history.size)
        assertNull(v.analysis)
        val notebook = manager.weakness()
        assertEquals(listOf("qk"), notebook.weakness.recurring.map { it.questionId })
        // Missed in all three attempts through qk, so unresolved; listed with its title.
        assertTrue(kanji in notebook.weakness.unresolved.map { it.ref })
        assertNotNull(notebook.knowledge.getValue(kanji).item)
        // 15 questions over five skills: no skill has the 10 needed to be judged.
        assertTrue(notebook.weakness.skills.none { it.reliable })

        manager.markForPractice(listOf(fueru))
        assertTrue(progress.observeItemState(fueru).first().isMarked)
    }
}
