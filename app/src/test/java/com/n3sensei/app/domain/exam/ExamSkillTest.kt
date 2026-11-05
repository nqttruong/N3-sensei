package com.n3sensei.app.domain.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamSkillTest {

    @Test
    fun everyQuestionType_trainsOneSkill_andOtherTakesItsSection() {
        assertEquals(ExamSkill.KANJI, ExamSkill.of(QuestionType.KANJI_READING, ExamSectionKind.VOCABULARY))
        assertEquals(ExamSkill.KANJI, ExamSkill.of(QuestionType.ORTHOGRAPHY, ExamSectionKind.VOCABULARY))
        assertEquals(ExamSkill.VOCABULARY, ExamSkill.of(QuestionType.PARAPHRASE, ExamSectionKind.VOCABULARY))
        assertEquals(ExamSkill.GRAMMAR, ExamSkill.of(QuestionType.SENTENCE_ORDER, ExamSectionKind.GRAMMAR))
        assertEquals(ExamSkill.READING, ExamSkill.of(QuestionType.INFORMATION_SEARCH, ExamSectionKind.READING))
        assertEquals(ExamSkill.LISTENING, ExamSkill.of(QuestionType.LISTENING_RESPONSE, ExamSectionKind.LISTENING))
        ExamSectionKind.entries.forEach { section ->
            assertEquals(section.name, ExamSkill.of(QuestionType.OTHER, section).name)
        }
    }

    @Test
    fun theFilter_matchesLevelSkillAndType_andNullMeansAny() {
        val full = ExamSetSummary("a", "Đề", "N3", ExamType.FULL, 6_000, 60, 0, null, setOf(ExamSkill.KANJI, ExamSkill.LISTENING))
        val section = full.copy(id = "b", type = ExamType.SECTION, skills = setOf(ExamSkill.LISTENING))
        assertTrue(ExamSetFilter().matches(full))
        assertTrue(ExamSetFilter(jlptLevel = "n3").matches(full))
        assertFalse(ExamSetFilter(jlptLevel = "N2").matches(full))
        assertTrue(ExamSetFilter(skill = ExamSkill.LISTENING).matches(section))
        assertFalse(ExamSetFilter(skill = ExamSkill.KANJI).matches(section))
        assertFalse(ExamSetFilter(type = ExamType.SECTION).matches(full))
        assertTrue(ExamSetFilter("N3", ExamSkill.KANJI, ExamType.FULL).matches(full))
    }

    @Test
    fun sessionStatus_followsTheAttempt() {
        assertEquals(ExamSessionStatus.NOT_STARTED, ExamSessionStatus.of(null))
        val attempt = ExamAttempt("a", "s", "Đề", 0, null, 60, null, null, 1, AttemptStatus.EXPIRED)
        assertEquals(ExamSessionStatus.EXPIRED, ExamSessionStatus.of(attempt))
        assertTrue(AttemptStatus.EXPIRED.isSubmitted)
        assertTrue(AttemptStatus.SUBMITTED.isSubmitted)
        assertFalse(AttemptStatus.ABANDONED.isSubmitted)
        assertFalse(AttemptStatus.IN_PROGRESS.isSubmitted)
    }
}
