package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ContrastModelsTest {

    private fun attempt(outcome: AnswerOutcome, chosen: String?) = ContrastAttempt(
        "r", "s", 0, "g", 1, "q", 1, ContrastQuestionType.MULTIPLE_CHOICE, "p", 1, outcome, chosen, "a", "A", "B", chosen, "x", Instant.EPOCH,
    )

    @Test
    fun onlyASkippedAnswerHasNoChosenOption() {
        attempt(AnswerOutcome.SKIPPED, null)
        attempt(AnswerOutcome.INCORRECT, "b")
        assertThrows(IllegalArgumentException::class.java) { attempt(AnswerOutcome.SKIPPED, "b") }
        assertThrows(IllegalArgumentException::class.java) { attempt(AnswerOutcome.INCORRECT, null) }
    }

    @Test
    fun aConfusionNeedsAWrongAnswerOnAnotherItem() {
        assertEquals(Confusion("A", "B"), attempt(AnswerOutcome.INCORRECT, "b").confusion)
        assertNull(attempt(AnswerOutcome.CORRECT, "a").confusion)
        assertNull(attempt(AnswerOutcome.SKIPPED, null).confusion)
    }

    @Test
    fun contrastTypesAreNotItemTypes_andParseOnlyKnownCodes() {
        // Contrast never extends ItemType (D4): the existing enum keeps its three entries.
        assertEquals(listOf("VOCABULARY", "KANJI", "GRAMMAR"), ItemType.entries.map { it.name })
        assertEquals(ContrastQuestionType.ERROR_DETECTION, ContrastQuestionType.fromCode("ERROR_DETECTION"))
        assertNull(ContrastQuestionType.fromCode("FILL_BLANK"))
        assertNull(ContrastContentType.fromCode(null))
        assertEquals(ContrastContentStatus.PUBLISHED, ContrastContentStatus.fromCode("PUBLISHED"))
    }

    @Test
    fun progressVersionIsSeparateFromContentVersion() {
        assertEquals(1, CONTRAST_PROGRESS_VERSION)
    }
}
