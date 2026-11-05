package com.n3sensei.app.domain.exam

import com.n3sensei.app.data.repository.OfflineExamRepository
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamGradingTest {

    private val choice = ExamQuestion(
        id = "q1", sectionId = "s", type = QuestionType.KANJI_READING, format = AnswerFormat.CHOICE, content = "「経験」",
        contentFurigana = null, options = listOf("けいけん", "きょうけん", "けいげん"), answerIndex = 0, acceptedAnswers = emptyList(),
        explanation = null, difficulty = 2, passageId = null, audioPath = null, order = 1,
        relatedItems = listOf(ItemRef(ItemType.VOCABULARY, "v1")),
    )
    private val text = choice.copy(id = "q2", format = AnswerFormat.TEXT, options = emptyList(), answerIndex = null, acceptedAnswers = listOf("ため", "ために"))

    @Test
    fun choice_isRightOnlyForTheAnswerIndex() {
        assertTrue(ExamGrading.isCorrect(choice, AnswerInput.Choice(0)))
        assertFalse(ExamGrading.isCorrect(choice, AnswerInput.Choice(1)))
        assertFalse(ExamGrading.isCorrect(choice, null))
        assertFalse(ExamGrading.isCorrect(choice, AnswerInput.Text("けいけん")))
    }

    @Test
    fun typedAnswer_ignoresWidthAndSpaces_butNotOtherWords() {
        assertTrue(ExamGrading.isCorrect(text, AnswerInput.Text("ため")))
        assertTrue(ExamGrading.isCorrect(text, AnswerInput.Text(" ため に　")))
        assertTrue(ExamGrading.isCorrect(text.copy(acceptedAnswers = listOf("N3")), AnswerInput.Text("Ｎ３")))
        assertFalse(ExamGrading.isCorrect(text, AnswerInput.Text("のに")))
        assertFalse(ExamGrading.isCorrect(text, AnswerInput.Text("  ")))
        assertFalse(ExamGrading.isCorrect(text, AnswerInput.Choice(0)))
    }

    @Test
    fun problems_findQuestionsThatCannotBeGraded() {
        assertEquals(emptyList<String>(), ExamGrading.problems(choice))
        assertEquals(emptyList<String>(), ExamGrading.problems(text))
        assertEquals(listOf("answerIndex"), ExamGrading.problems(choice.copy(answerIndex = 3)))
        assertEquals(listOf("options", "answerIndex"), ExamGrading.problems(choice.copy(options = listOf("a"), answerIndex = 1)))
        assertEquals(listOf("acceptedAnswers"), ExamGrading.problems(text.copy(acceptedAnswers = listOf(" "))))
        assertEquals(listOf("content", "difficulty"), ExamGrading.problems(choice.copy(content = " ", difficulty = 7)))
    }

    @Test
    fun theSnapshotOfAQuestion_readsBackTheSame() {
        listOf(choice, text.copy(passageId = "p", audioPath = "a.mp3", contentFurigana = "{経験|けいけん}")).forEach {
            assertEquals(it, OfflineExamRepository.fromSnapshot(OfflineExamRepository.snapshot(it)))
        }
        assertEquals(null, OfflineExamRepository.fromSnapshot("not json"))
    }
}
