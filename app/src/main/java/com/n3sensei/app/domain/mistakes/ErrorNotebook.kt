package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ReviewItem
import com.n3sensei.app.domain.exam.ReviewStatus
import java.text.Normalizer

/** Where an error stands in the notebook. */
enum class ErrorState {
    /** Not reviewed yet since it was (last) made. */
    NEW,

    /** Reviewed at least once, not mastered. */
    REVIEWING,

    /** Passed every spaced review, at least once on another question ([ErrorReviewSchedule]). */
    MASTERED,

    /** Put away by the learner. */
    ARCHIVED,
}

/** An error notebook entry: the record, its review state and the learner's flags. */
data class NotebookError(
    val record: ErrorRecord,
    val review: ReviewItem?,
    val isImportant: Boolean,
    val understoodAt: Long?,
    val jlptLevel: String?,
    /** Null when unknown (an error from before the skill was stored and with no task type). */
    val skill: ExamSkill?,
) {
    val id: Long get() = record.id

    val state: ErrorState
        get() = when {
            record.status == ErrorStatus.ARCHIVED -> ErrorState.ARCHIVED
            record.status == ErrorStatus.MASTERED || review?.status == ReviewStatus.MASTERED -> ErrorState.MASTERED
            (review?.reviewCount ?: 0) > 0 -> ErrorState.REVIEWING
            else -> ErrorState.NEW
        }

    /** Missed in more than one exam. */
    val isRecurring: Boolean get() = record.errorCount >= RECURRING_COUNT

    /** Still to fix: not mastered nor archived. */
    val isOpen: Boolean get() = state == ErrorState.NEW || state == ErrorState.REVIEWING

    /** Shown first: open and either marked by the learner or missed again. */
    val isPriority: Boolean get() = isOpen && (isImportant || isRecurring)

    companion object {
        const val RECURRING_COUNT = 2
    }
}

/** Counts on top of the notebook. */
data class NotebookOverview(
    val total: Int,
    val open: Int,
    val reviewing: Int,
    val mastered: Int,
    val recurring: Int,
) {
    companion object {
        fun of(errors: List<NotebookError>) = NotebookOverview(
            total = errors.size,
            open = errors.count { it.isOpen },
            reviewing = errors.count { it.state == ErrorState.REVIEWING },
            mastered = errors.count { it.state == ErrorState.MASTERED },
            recurring = errors.count { it.isRecurring },
        )
    }
}

/** The "Trạng thái" filter of the notebook. */
enum class NotebookStateFilter { ALL, NEW, RECURRING, REVIEWING, MASTERED, IMPORTANT }

data class NotebookFilter(
    val level: String? = null,
    val skill: ExamSkill? = null,
    val state: NotebookStateFilter = NotebookStateFilter.ALL,
    val query: String = "",
) {
    fun matches(error: NotebookError): Boolean =
        (level == null || error.jlptLevel.equals(level, ignoreCase = true)) &&
            (skill == null || error.skill == skill) &&
            when (state) {
                NotebookStateFilter.ALL -> error.state != ErrorState.ARCHIVED
                NotebookStateFilter.NEW -> error.state == ErrorState.NEW
                NotebookStateFilter.RECURRING -> error.isRecurring && error.state != ErrorState.ARCHIVED
                NotebookStateFilter.REVIEWING -> error.state == ErrorState.REVIEWING
                NotebookStateFilter.MASTERED -> error.state == ErrorState.MASTERED
                NotebookStateFilter.IMPORTANT -> error.isImportant && error.state != ErrorState.ARCHIVED
            } &&
            matchesQuery(error)

    /**
     * The query in the question, its options or accepted answers, the explanation or the
     * learner's note; width, case and accents of Vietnamese are ignored ("on tap" finds "ôn tập").
     */
    private fun matchesQuery(error: NotebookError): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val q = error.record.question
        val haystack = fold(
            listOfNotNull(
                q?.content, q?.contentFurigana, q?.explanation, error.record.notes,
                q?.options?.joinToString(" "), q?.acceptedAnswers?.joinToString(" "),
            ).joinToString(" "),
        )
        return words.all { it in haystack }
    }

    companion object {
        /** Open errors that matter most first, then the most recent; mastered and archived last. */
        val ORDER: Comparator<NotebookError> =
            compareBy<NotebookError> { if (it.isPriority) 0 else if (it.isOpen) 1 else 2 }
                .thenByDescending { it.record.lastWrongAt }
                .thenByDescending { it.id }

        /** NFKC, lower case, accents of Latin letters (Vietnamese) and đ removed, whitespace collapsed. Kana keep their marks. */
        fun fold(text: String): String {
            val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
            val stripped = Normalizer.normalize(nfkc, Normalizer.Form.NFD).replace(LATIN_MARKS, "")
            return Normalizer.normalize(stripped.replace('đ', 'd'), Normalizer.Form.NFC).replace(Regex("\\s+"), " ").trim()
        }

        private val LATIN_MARKS = Regex("(?<=\\p{IsLatin})\\p{Mn}+")
    }
}

/** One review of an error. */
data class ErrorReviewEntry(
    val reviewedAt: Long,
    val correct: Boolean,
    /** False for a review before it was due: recorded as practice, mastery unchanged. */
    val counted: Boolean,
    val stepBefore: Int,
    val stepAfter: Int,
    /** The question asked; null for reviews recorded before the question was stored (the original). */
    val questionId: String? = null,
    val kind: ReviewQuestionKind = ReviewQuestionKind.ORIGINAL,
)
