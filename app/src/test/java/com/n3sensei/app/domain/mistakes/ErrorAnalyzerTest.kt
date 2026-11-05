package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.course.ItemLocation
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.practice.PracticeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorAnalyzerTest {

    private val analyzer = RuleBasedErrorAnalyzer()

    private fun question(
        id: String = "q",
        type: QuestionType = QuestionType.OTHER,
        related: List<ItemRef> = emptyList(),
        explanation: String? = "Vì …",
        audio: String? = null,
        format: AnswerFormat = AnswerFormat.CHOICE,
    ) = ExamQuestion(
        id, "s", type, format, "Câu $id", null,
        if (format == AnswerFormat.CHOICE) listOf("A", "B", "C", "D") else emptyList(),
        if (format == AnswerFormat.CHOICE) 0 else null,
        if (format == AnswerFormat.TEXT) listOf("ため", "ために") else emptyList(),
        explanation, null, null, audio, 0, related,
    )

    private fun occ(
        q: ExamQuestion,
        attempt: String = "now",
        at: Long = 100,
        outcome: ItemOutcome = ItemOutcome.WRONG,
        section: ExamSectionKind = ExamSectionKind.VOCABULARY,
        picked: Int? = if (outcome == ItemOutcome.UNANSWERED) null else if (outcome == ItemOutcome.CORRECT) 0 else 2,
        expired: Boolean = false,
    ) = GradedOccurrence(attempt, "set", at, expired, 0, q, section, ExamSkill.VOCABULARY, outcome, picked, null, 1_000)

    private fun analyze(now: GradedOccurrence, history: List<GradedOccurrence> = listOf(now), knowledge: List<Knowledge> = emptyList()) =
        analyzer.analyze(MistakeContext(now, history, knowledge))

    private fun grammar(id: String) = Knowledge(
        ItemRef(ItemType.GRAMMAR, id), PracticeItem.GrammarItem(GrammarPoint(id, "l", "〜$id", "V + $id", "nghĩa $id", "", null)), ItemLocation.NONE,
    )

    private fun word(id: String, lesson: Int? = null) = Knowledge(
        ItemRef(ItemType.VOCABULARY, id), PracticeItem.VocabularyItem(Vocabulary(id, "l", id, id, null, "nghĩa", null, "N3", null)),
        ItemLocation(ItemLocation.Kind.VOCABULARY, courseLesson = lesson),
    )

    @Test
    fun eachTaskType_givesItsCategory_withTheTaskAsEvidence() {
        val expected = mapOf(
            QuestionType.KANJI_READING to ErrorCategory.KANJI_CONFUSION,
            QuestionType.ORTHOGRAPHY to ErrorCategory.KANJI_CONFUSION,
            QuestionType.CONTEXT to ErrorCategory.VOCABULARY_GAP,
            QuestionType.PARAPHRASE to ErrorCategory.VOCABULARY_GAP,
            QuestionType.USAGE to ErrorCategory.VOCABULARY_GAP,
            QuestionType.GRAMMAR_FORM to ErrorCategory.GRAMMAR_CONFUSION,
            QuestionType.SENTENCE_ORDER to ErrorCategory.GRAMMAR_CONFUSION,
            QuestionType.TEXT_GRAMMAR to ErrorCategory.GRAMMAR_CONFUSION,
            QuestionType.READING_SHORT to ErrorCategory.READING_MISUNDERSTANDING,
            QuestionType.INFORMATION_SEARCH to ErrorCategory.READING_MISUNDERSTANDING,
            QuestionType.LISTENING_POINT to ErrorCategory.LISTENING_MISUNDERSTANDING,
            QuestionType.LISTENING_RESPONSE to ErrorCategory.LISTENING_MISUNDERSTANDING,
        )
        expected.forEach { (type, category) ->
            val a = analyze(occ(question(type = type)))
            assertEquals(type.name, category, a.category)
            assertEquals(listOf(Evidence.TaskType(type)), a.evidence)
        }
    }

    @Test
    fun anUntypedQuestion_usesItsOnlyKindOfLinkedItem_elseItsSection_elseUnknown() {
        val kanji = analyze(occ(question(related = listOf(ItemRef(ItemType.KANJI, "k")))))
        assertEquals(ErrorCategory.KANJI_CONFUSION, kanji.category)
        assertEquals(listOf(Evidence.LinkedKnowledge(ItemType.KANJI)), kanji.evidence)

        val mixed = question(related = listOf(ItemRef(ItemType.KANJI, "k"), ItemRef(ItemType.VOCABULARY, "v")))
        assertEquals(ErrorCategory.GRAMMAR_CONFUSION, analyze(occ(mixed, section = ExamSectionKind.GRAMMAR)).category)
        assertEquals(ErrorCategory.READING_MISUNDERSTANDING, analyze(occ(question(), section = ExamSectionKind.READING)).category)
        assertEquals(ErrorCategory.LISTENING_MISUNDERSTANDING, analyze(occ(question(), section = ExamSectionKind.LISTENING)).category)
        // "Từ vựng" holds kanji and vocabulary tasks: without more, the cause is not known.
        val unknown = analyze(occ(mixed, section = ExamSectionKind.VOCABULARY))
        assertEquals(ErrorCategory.UNKNOWN, unknown.category)
        assertEquals(listOf(Evidence.NotEnoughData), unknown.evidence)
    }

    @Test
    fun aBlank_isUnknown_andPointsToTheTimeWhenItRanOut() {
        val q = question(type = QuestionType.GRAMMAR_FORM)
        val blank = analyze(occ(q, outcome = ItemOutcome.UNANSWERED, expired = true))
        assertEquals(ErrorCategory.UNKNOWN, blank.category)
        assertEquals(listOf(Evidence.LeftBlank), blank.evidence)
        assertNull(blank.givenAnswer)
        assertEquals(Recommendation.ManageTime(timeRanOut = true), blank.recommendations.first())
    }

    @Test
    fun aWrongAnswerAlone_isNeverCareless_onlyAfterTwoRightAnswersToTheSameQuestion() {
        val q = question(type = QuestionType.KANJI_READING)
        // Wrong now, right once before: not enough.
        val once = listOf(occ(q, "a1", 10, ItemOutcome.CORRECT), occ(q, "now", 100))
        assertEquals(ErrorCategory.KANJI_CONFUSION, analyze(once.last(), once).category)
        // Right twice before: careless, and it says why.
        val twice = listOf(occ(q, "a1", 10, ItemOutcome.CORRECT), occ(q, "a2", 20, ItemOutcome.CORRECT), occ(q, "now", 100))
        val careless = analyze(twice.last(), twice)
        assertEquals(ErrorCategory.CARELESS_MISTAKE, careless.category)
        assertEquals(listOf(Evidence.RightBefore(2)), careless.evidence)
        assertEquals(listOf(Recommendation.ReadCarefully), careless.recommendations)
        // Later attempts do not count as "before".
        val later = listOf(occ(q, "now", 100), occ(q, "a3", 200, ItemOutcome.CORRECT), occ(q, "a4", 300, ItemOutcome.CORRECT))
        assertEquals(ErrorCategory.KANJI_CONFUSION, analyze(later.first(), later).category)
        // A blank is never careless either.
        val blank = twice.dropLast(1) + occ(q, "now", 100, ItemOutcome.UNANSWERED)
        assertEquals(ErrorCategory.UNKNOWN, analyze(blank.last(), blank).category)
    }

    @Test
    fun theAnalysis_showsTheGivenAndRightAnswers_andSaysWhenThereIsNoExplanation() {
        val a = analyze(occ(question(type = QuestionType.CONTEXT, explanation = null), picked = 2))
        assertEquals("C", a.givenAnswer)
        assertEquals("A", a.correctAnswer)
        assertNull(a.explanation)
        assertNull(analyze(occ(question(explanation = "   "))).explanation)

        val typed = question(type = QuestionType.GRAMMAR_FORM, format = AnswerFormat.TEXT)
        val t = analyze(occ(typed, picked = null).copy(textAnswer = "のに"))
        assertEquals("のに", t.givenAnswer)
        assertEquals("ため / ために", t.correctAnswer)
    }

    @Test
    fun aRepeatedMistake_isCounted_andRaisesThePriority() {
        val q = question(type = QuestionType.PARAPHRASE)
        val first = analyze(occ(q, "a1", 10))
        assertEquals(1, first.repeat.timesMissed)
        assertEquals(ReviewPriority.NORMAL, first.repeat.priority)

        val two = listOf(occ(q, "a1", 10), occ(q, "a2", 20, ItemOutcome.CORRECT), occ(q, "now", 100))
        val again = analyze(two.last(), two)
        assertEquals(2, again.repeat.timesMissed)
        assertEquals(3, again.repeat.timesSeen)
        assertEquals(100L, again.repeat.lastMissedAt)
        assertEquals(ReviewPriority.RAISED, again.repeat.priority)

        val three = listOf(occ(q, "a1", 10), occ(q, "a2", 20), occ(q, "now", 100))
        assertEquals(ReviewPriority.HIGH, analyze(three.last(), three).repeat.priority)
    }

    @Test
    fun mistakesAreLinkedByASharedItemOrTask_neverByTheSameWrongOption() {
        val tame = ItemRef(ItemType.GRAMMAR, "tame")
        val now = question("now", QuestionType.GRAMMAR_FORM, listOf(tame))
        val sameItem = question("other1", QuestionType.TEXT_GRAMMAR, listOf(tame))
        val sameTask = question("other2", QuestionType.GRAMMAR_FORM)
        // Same wrong option (2), different item and task: not related.
        val unrelated = question("other3", QuestionType.KANJI_READING, listOf(ItemRef(ItemType.KANJI, "k")))
        val history = listOf(
            occ(sameItem, "a1", 10), occ(sameTask, "a1", 10), occ(unrelated, "a1", 10, picked = 2),
            occ(now, "now", 100, picked = 2),
        )
        val links = analyze(history.last(), history).repeat.linked
        assertEquals(listOf("other1", "other2"), links.map { it.questionId })
        assertEquals(LinkBasis.SameKnowledge(tame), links[0].basis)
        assertEquals(LinkBasis.SameTask(QuestionType.GRAMMAR_FORM), links[1].basis)
        assertEquals(ReviewPriority.RAISED, analyze(history.last(), history).repeat.priority)
        // An untyped question with no items links to nothing.
        val bare = listOf(occ(question("x"), "a1", 10), occ(question("y"), "now", 100))
        assertTrue(analyze(bare.last(), bare).repeat.linked.isEmpty())
    }

    @Test
    fun recommendations_followTheCategoryAndTheLinkedItems() {
        val g = analyze(
            occ(question(type = QuestionType.GRAMMAR_FORM, related = listOf(ItemRef(ItemType.GRAMMAR, "a"), ItemRef(ItemType.GRAMMAR, "b")))),
            knowledge = listOf(grammar("a"), grammar("b")),
        )
        assertEquals(listOf("a", "b"), (g.recommendations.single() as Recommendation.CompareGrammar).points.map { it.id })

        val one = analyze(occ(question(type = QuestionType.GRAMMAR_FORM)), knowledge = listOf(grammar("a")))
        assertEquals(listOf(Recommendation.StudyItem(grammar("a"))), one.recommendations)
        assertEquals(
            listOf(Recommendation.StudySkill(ExamSkill.GRAMMAR)),
            analyze(occ(question(type = QuestionType.GRAMMAR_FORM))).recommendations,
        )

        val v = analyze(occ(question(type = QuestionType.CONTEXT)), knowledge = listOf(word("w1", lesson = 3), word("w2", lesson = 1)))
        assertEquals(Recommendation.PracticeWords(listOf(ItemRef(ItemType.VOCABULARY, "w1"), ItemRef(ItemType.VOCABULARY, "w2"))), v.recommendations.first())
        assertEquals(listOf(1, 3), v.lessons)

        // A study item no longer in the app is listed, but not recommended.
        val gone = Knowledge(ItemRef(ItemType.KANJI, "gone"), null, ItemLocation.NONE)
        assertEquals(
            listOf(Recommendation.StudySkill(ExamSkill.KANJI)),
            analyze(occ(question(type = QuestionType.KANJI_READING)), knowledge = listOf(gone)).recommendations,
        )

        assertEquals(Recommendation.ReadingKeywords, analyze(occ(question(type = QuestionType.READING_LONG))).recommendations.first())
        assertEquals(
            Recommendation.ListenAgain(audioAvailable = true),
            analyze(occ(question(type = QuestionType.LISTENING_TASK, audio = "a.mp3"))).recommendations.first(),
        )
        assertEquals(
            Recommendation.ListenAgain(audioAvailable = false),
            analyze(occ(question(type = QuestionType.LISTENING_TASK))).recommendations.first(),
        )
    }
}
