package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaknessAnalyzerTest {

    private val k = ItemRef(ItemType.KANJI, "k")
    private val g = ItemRef(ItemType.GRAMMAR, "g")

    private fun occ(id: String, skill: ExamSkill, outcome: ItemOutcome, at: Long, related: List<ItemRef> = emptyList()) = GradedOccurrence(
        "a$at", "set", at, false, 0,
        ExamQuestion(id, "s", QuestionType.OTHER, AnswerFormat.CHOICE, "Câu $id", null, listOf("A", "B"), 0, emptyList(), null, null, null, null, 0, related),
        ExamSectionKind.VOCABULARY, skill, outcome, null, null, null,
    )

    @Test
    fun noHistory_isAnEmptyReport() {
        val r = WeaknessAnalyzer.analyze(emptyList())
        assertTrue(r.isEmpty)
        assertTrue(r.frequentKnowledge.isEmpty() && r.recurring.isEmpty() && r.unresolved.isEmpty() && r.improved.isEmpty())
    }

    @Test
    fun skillRates_needTenQuestions_beforeTheyCount_andReliableOnesComeFirst() {
        val history = (0 until 10).map { occ("r$it", ExamSkill.READING, if (it < 3) ItemOutcome.WRONG else ItemOutcome.CORRECT, it.toLong()) } +
            (0 until 4).map { occ("l$it", ExamSkill.LISTENING, ItemOutcome.WRONG, it.toLong()) }
        val skills = WeaknessAnalyzer.analyze(history).skills
        // Listening misses 100 % but on 4 questions only: listed after reading, not reliable.
        assertEquals(listOf(ExamSkill.READING, ExamSkill.LISTENING), skills.map { it.skill })
        assertTrue(skills[0].reliable)
        assertEquals(0.3, skills[0].missRate, 1e-9)
        assertFalse(skills[1].reliable)
        assertEquals(1.0, skills[1].missRate, 1e-9)
    }

    @Test
    fun anItemMissedInTwoQuestions_isFrequent_onceIsNot() {
        val history = listOf(
            occ("q1", ExamSkill.KANJI, ItemOutcome.WRONG, 1, listOf(k)),
            occ("q2", ExamSkill.KANJI, ItemOutcome.UNANSWERED, 2, listOf(k)),
            occ("q3", ExamSkill.GRAMMAR, ItemOutcome.WRONG, 3, listOf(g)),
        )
        val r = WeaknessAnalyzer.analyze(history)
        assertEquals(listOf(k), r.frequentKnowledge.map { it.ref })
        assertEquals(KnowledgeStat(k, seen = 2, missed = 2, lastMissed = true), r.frequentKnowledge.single())
    }

    @Test
    fun recurringUnresolvedAndImproved_followTheLatestAnswer() {
        val history = listOf(
            // q1 on k: missed twice, then right → recurring, improved.
            occ("q1", ExamSkill.KANJI, ItemOutcome.WRONG, 1, listOf(k)),
            occ("q1", ExamSkill.KANJI, ItemOutcome.WRONG, 2, listOf(k)),
            occ("q1", ExamSkill.KANJI, ItemOutcome.CORRECT, 3, listOf(k)),
            // q2 on g: right, then missed → unresolved.
            occ("q2", ExamSkill.GRAMMAR, ItemOutcome.CORRECT, 1, listOf(g)),
            occ("q2", ExamSkill.GRAMMAR, ItemOutcome.WRONG, 3, listOf(g)),
        )
        val r = WeaknessAnalyzer.analyze(history.shuffled(java.util.Random(7)))
        assertEquals(listOf("q1"), r.recurring.map { it.questionId })
        assertEquals(listOf(k), r.improved.map { it.ref })
        assertEquals(listOf("q1"), r.improvedQuestions.map { it.questionId })
        assertEquals(listOf(g), r.unresolved.map { it.ref })
        assertEquals(KnowledgeStat(k, seen = 3, missed = 2, lastMissed = false), r.frequentKnowledge.single())
    }
}
