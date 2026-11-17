package com.n3sensei.app.domain.contrast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ContrastAnswerCheckerTest {

    private val pack = ContrastFixtures.samplePack()
    private fun q(id: String) = pack.groups.flatMap { it.questions }.single { it.id == id }

    @Test
    fun multipleChoice_correctAndWrong() {
        val question = q("cq-g-koto-mc1") // correct b (natteiru); a = shiteiru; c = distractor
        val right = ContrastAnswerChecker.grade(question, AnswerInput.Chosen("b"))
        assertEquals(AnswerOutcome.CORRECT, right.outcome)
        val wrong = ContrastAnswerChecker.grade(question, AnswerInput.Chosen("a"))
        assertEquals(AnswerOutcome.INCORRECT, wrong.outcome)
        assertEquals("ci-g-koto-ni-natteiru", wrong.expectedItemId)
        assertEquals("ci-g-koto-ni-shiteiru", wrong.chosenItemId)
        val distractor = ContrastAnswerChecker.grade(question, AnswerInput.Chosen("c"))
        assertEquals(AnswerOutcome.INCORRECT, distractor.outcome)
        assertNull(distractor.chosenItemId)
        assertEquals("この寮では、十時に寝ることになっている。", distractor.correctText)
    }

    @Test
    fun fillInTheBlank_bothDirections() {
        val toNatteiru = ContrastAnswerChecker.grade(q("cq-g-koto-fb1"), AnswerInput.Chosen("a"))
        val toShiteiru = ContrastAnswerChecker.grade(q("cq-g-koto-fb2"), AnswerInput.Chosen("b"))
        assertEquals(AnswerOutcome.INCORRECT, toNatteiru.outcome)
        assertEquals("ci-g-koto-ni-natteiru" to "ci-g-koto-ni-shiteiru", toNatteiru.expectedItemId to toNatteiru.chosenItemId)
        assertEquals("ci-g-koto-ni-shiteiru" to "ci-g-koto-ni-natteiru", toShiteiru.expectedItemId to toShiteiru.chosenItemId)
        assertEquals(AnswerOutcome.CORRECT, ContrastAnswerChecker.grade(q("cq-g-koto-fb2"), AnswerInput.Chosen("a")).outcome)
    }

    @Test
    fun errorDetection_rightSegment_orMissedError() {
        val question = q("cq-g-koto-ed1") // wrong segment d uses natteiru; correction = shiteiru (target)
        val found = ContrastAnswerChecker.grade(question, AnswerInput.Chosen("d"))
        assertEquals(AnswerOutcome.CORRECT, found.outcome)
        assertEquals("ことになっている。 → ことにしている。", found.correctText)
        val missed = ContrastAnswerChecker.grade(question, AnswerInput.Chosen("b"))
        assertEquals(AnswerOutcome.INCORRECT, missed.outcome)
        // The learner accepted natteiru where shiteiru was needed: shiteiru → natteiru.
        assertEquals("ci-g-koto-ni-shiteiru", missed.expectedItemId)
        assertEquals("ci-g-koto-ni-natteiru", missed.chosenItemId)
        assertEquals("健康のために、", missed.chosenText)
    }

    @Test
    fun contextSelection() {
        val question = q("cq-g-koto-cs1")
        assertEquals(AnswerOutcome.CORRECT, ContrastAnswerChecker.grade(question, AnswerInput.Chosen("a")).outcome)
        assertEquals("ci-g-koto-ni-shiteiru", ContrastAnswerChecker.grade(question, AnswerInput.Chosen("b")).chosenItemId)
    }

    @Test
    fun skipped_isNeitherCorrectNorIncorrect() {
        val skipped = ContrastAnswerChecker.grade(q("cq-g-koto-mc1"), AnswerInput.Skipped)
        assertEquals(AnswerOutcome.SKIPPED, skipped.outcome)
        assertNull(skipped.chosenOptionId)
        assertNull(skipped.chosenItemId)
    }

    @Test
    fun anOptionOfAnotherQuestion_isRefused() {
        assertThrows(IllegalArgumentException::class.java) { ContrastAnswerChecker.grade(q("cq-g-koto-fb1"), AnswerInput.Chosen("c")) }
    }

    @Test
    fun aGroupOfThreeItems_keepsEveryDirectionApart() {
        val question = ContrastQuestion(
            id = "q3", groupId = "g3", revision = 1, type = ContrastQuestionType.MULTIPLE_CHOICE, difficulty = 1, targetItemId = "A",
            instructionVi = "?", prompt = null, context = null,
            options = listOf(ContrastOption("a", "あ", null, "A", null), ContrastOption("b", "い", null, "B", null), ContrastOption("c", "う", null, "C", null)),
            correctOptionId = "a", correction = null, explanationVi = "…",
        )
        fun attempt(option: String, n: Int): ContrastAttempt {
            val g = ContrastAnswerChecker.grade(question, AnswerInput.Chosen(option))
            return ContrastAttempt("r$n", "s", n, "g3", 1, "q3", 1, question.type, "p", 1, g.outcome, g.chosenOptionId, "a", g.expectedItemId, g.chosenItemId, g.chosenText, g.correctText, Instant.EPOCH)
        }
        assertEquals(Confusion("A", "B"), attempt("b", 0).confusion)
        assertEquals(Confusion("A", "C"), attempt("c", 1).confusion)
        assertNull(attempt("a", 2).confusion)
    }
}
