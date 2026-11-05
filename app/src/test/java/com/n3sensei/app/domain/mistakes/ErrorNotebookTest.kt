package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.exam.ReviewItem
import com.n3sensei.app.domain.exam.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorNotebookTest {

    private fun error(
        id: Long,
        content: String = "Câu $id",
        count: Int = 1,
        status: ErrorStatus = ErrorStatus.ACTIVE,
        reviews: Int = 0,
        reviewStatus: ReviewStatus = ReviewStatus.NEW,
        important: Boolean = false,
        level: String? = "N3",
        skill: ExamSkill? = ExamSkill.GRAMMAR,
        notes: String? = null,
        lastWrongAt: Long = id,
    ) = NotebookError(
        ErrorRecord(
            id, "q$id", "set", ErrorType.INCORRECT, count, 0, lastWrongAt, 0, status, notes,
            ExamQuestion("q$id", "s", QuestionType.GRAMMAR_FORM, AnswerFormat.CHOICE, content, null, listOf("ために", "のに"), 0, emptyList(), "Giải thích: mục đích", null, null, null, 0),
        ),
        ReviewItem(id, id, 1_000, 0, 0, reviews, 0, null, reviewStatus),
        important, null, level, skill,
    )

    @Test
    fun theState_followsReviewsAndStatus() {
        assertEquals(ErrorState.NEW, error(1).state)
        assertEquals(ErrorState.REVIEWING, error(2, reviews = 1, reviewStatus = ReviewStatus.LEARNING).state)
        assertEquals(ErrorState.MASTERED, error(3, reviews = 4, reviewStatus = ReviewStatus.MASTERED).state)
        assertEquals(ErrorState.MASTERED, error(4, status = ErrorStatus.MASTERED).state)
        assertEquals(ErrorState.ARCHIVED, error(5, status = ErrorStatus.ARCHIVED).state)
        // Priority: still open and marked or missed again; never a fixed one.
        assertTrue(error(6, important = true).isPriority)
        assertTrue(error(7, count = 2).isPriority)
        assertFalse(error(8, count = 3, status = ErrorStatus.MASTERED).isPriority)
        assertFalse(error(9).isPriority)
    }

    @Test
    fun theOverview_countsEveryError() {
        val all = listOf(
            error(1), error(2, count = 2), error(3, reviews = 2, reviewStatus = ReviewStatus.REVIEW),
            error(4, count = 3, reviews = 4, reviewStatus = ReviewStatus.MASTERED), error(5, status = ErrorStatus.ARCHIVED),
        )
        assertEquals(NotebookOverview(total = 5, open = 3, reviewing = 1, mastered = 1, recurring = 2), NotebookOverview.of(all))
    }

    @Test
    fun filters_byLevelSkillAndState_andArchivedOnlyWhenAskedFor() {
        val all = listOf(
            error(1, level = "N3", skill = ExamSkill.GRAMMAR),
            error(2, level = "N2", skill = ExamSkill.KANJI, count = 2),
            error(3, level = "N3", skill = ExamSkill.READING, reviews = 1, reviewStatus = ReviewStatus.LEARNING, important = true),
            error(4, level = "N3", skill = ExamSkill.GRAMMAR, reviews = 4, reviewStatus = ReviewStatus.MASTERED),
            error(5, level = null, skill = null, status = ErrorStatus.ARCHIVED),
        )
        fun ids(f: NotebookFilter) = all.filter(f::matches).map { it.id }
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(NotebookFilter()))
        assertEquals(listOf(1L, 3L, 4L), ids(NotebookFilter(level = "n3")))
        assertEquals(listOf(2L), ids(NotebookFilter(skill = ExamSkill.KANJI)))
        assertEquals(listOf(1L, 2L), ids(NotebookFilter(state = NotebookStateFilter.NEW)))
        assertEquals(listOf(2L), ids(NotebookFilter(state = NotebookStateFilter.RECURRING)))
        assertEquals(listOf(3L), ids(NotebookFilter(state = NotebookStateFilter.REVIEWING)))
        assertEquals(listOf(4L), ids(NotebookFilter(state = NotebookStateFilter.MASTERED)))
        assertEquals(listOf(3L), ids(NotebookFilter(state = NotebookStateFilter.IMPORTANT)))
        assertEquals(listOf(1L), ids(NotebookFilter(level = "N3", skill = ExamSkill.GRAMMAR, state = NotebookStateFilter.NEW)))
    }

    @Test
    fun search_looksInQuestionOptionsExplanationAndNotes_ignoringAccentsAndWidth() {
        val all = listOf(
            error(1, content = "雨が降った＿＿、試合は中止になった。"),
            error(2, content = "図書館はどこですか。", notes = "Nhầm với のに khi ôn tập"),
        )
        fun ids(q: String) = all.filter(NotebookFilter(query = q)::matches).map { it.id }
        assertEquals(listOf(1L), ids("試合"))
        assertEquals(listOf(1L), ids("降った"))
        assertEquals(listOf(2L), ids("on tap")) // the note, accents ignored
        assertEquals(listOf(2L), ids("ÔN TẬP"))
        assertEquals(listOf(1L, 2L), ids("ために")) // an option of both
        assertEquals(listOf(1L, 2L), ids("muc dich")) // the explanation
        assertEquals(listOf(2L), ids("図書館 のに")) // every word must match
        assertEquals(emptyList<Long>(), ids("không có"))
        assertEquals(listOf(1L, 2L), ids("   "))
        // Voiced kana keep their marks: が is not か.
        assertEquals(listOf(1L), ids("が降"))
        assertEquals(emptyList<Long>(), ids("か降"))
    }

    @Test
    fun theOrder_putsOpenPriorityErrorsFirst_thenRecent_fixedOnesLast() {
        val sorted = listOf(
            error(1, lastWrongAt = 50),
            error(2, important = true, lastWrongAt = 10),
            error(3, reviews = 4, reviewStatus = ReviewStatus.MASTERED, lastWrongAt = 99),
            error(4, lastWrongAt = 70),
        ).sortedWith(NotebookFilter.ORDER)
        assertEquals(listOf(2L, 4L, 1L, 3L), sorted.map { it.id })
    }
}
