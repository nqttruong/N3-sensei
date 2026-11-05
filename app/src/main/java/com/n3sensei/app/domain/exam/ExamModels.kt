package com.n3sensei.app.domain.exam

import com.n3sensei.app.domain.model.ItemRef
import java.text.Normalizer

/** Names of these enums are stored in Room; never rename an entry. Unknown names read as the fallback. */
enum class ExamType { FULL, SECTION, GENERATED }

/** The parts of a JLPT N3 exam. */
enum class ExamSectionKind { VOCABULARY, GRAMMAR, READING, LISTENING }

/** The JLPT task a question belongs to; [OTHER] for anything else. */
enum class QuestionType {
    KANJI_READING, ORTHOGRAPHY, CONTEXT, PARAPHRASE, USAGE,
    GRAMMAR_FORM, SENTENCE_ORDER, TEXT_GRAMMAR,
    READING_SHORT, READING_MID, READING_LONG, INFORMATION_SEARCH,
    LISTENING_TASK, LISTENING_POINT, LISTENING_SUMMARY, LISTENING_EXPRESSION, LISTENING_RESPONSE,
    OTHER,
}

/** How a question is answered: pick one option, or type the answer. Listening questions are CHOICE with audio. */
enum class AnswerFormat { CHOICE, TEXT }

/**
 * Stored state of an attempt. [SUBMITTED] and [EXPIRED] are both submitted (scored, mistakes
 * recorded, read-only): [EXPIRED] means the time ran out and the attempt was submitted for the
 * learner at its deadline. [ABANDONED]: left without a score.
 */
enum class AttemptStatus {
    IN_PROGRESS, SUBMITTED, EXPIRED, ABANDONED;

    val isSubmitted: Boolean get() = this == SUBMITTED || this == EXPIRED
}

/** Where an exam session stands, including before its attempt exists. */
enum class ExamSessionStatus {
    NOT_STARTED, IN_PROGRESS, SUBMITTED, EXPIRED, ABANDONED;

    companion object {
        fun of(attempt: ExamAttempt?): ExamSessionStatus = when (attempt?.status) {
            null -> NOT_STARTED
            AttemptStatus.IN_PROGRESS -> IN_PROGRESS
            AttemptStatus.SUBMITTED -> SUBMITTED
            AttemptStatus.EXPIRED -> EXPIRED
            AttemptStatus.ABANDONED -> ABANDONED
        }
    }
}

/** The skill a question trains, as the exam list filters them. */
enum class ExamSkill {
    VOCABULARY, KANJI, GRAMMAR, READING, LISTENING;

    companion object {
        /** From the question's task; a question of type OTHER takes its section's skill. */
        fun of(type: QuestionType, section: ExamSectionKind): ExamSkill = when (type) {
            QuestionType.KANJI_READING, QuestionType.ORTHOGRAPHY -> KANJI
            QuestionType.CONTEXT, QuestionType.PARAPHRASE, QuestionType.USAGE -> VOCABULARY
            QuestionType.GRAMMAR_FORM, QuestionType.SENTENCE_ORDER, QuestionType.TEXT_GRAMMAR -> GRAMMAR
            QuestionType.READING_SHORT, QuestionType.READING_MID, QuestionType.READING_LONG, QuestionType.INFORMATION_SEARCH -> READING
            QuestionType.LISTENING_TASK, QuestionType.LISTENING_POINT, QuestionType.LISTENING_SUMMARY,
            QuestionType.LISTENING_EXPRESSION, QuestionType.LISTENING_RESPONSE -> LISTENING
            QuestionType.OTHER -> when (section) {
                ExamSectionKind.VOCABULARY -> VOCABULARY
                ExamSectionKind.GRAMMAR -> GRAMMAR
                ExamSectionKind.READING -> READING
                ExamSectionKind.LISTENING -> LISTENING
            }
        }
    }
}

enum class Confidence { LOW, MEDIUM, HIGH }

/** Main cause of a mistake. The data layer sets the first two; the error analysis refines the rest. */
enum class ErrorType { INCORRECT, UNANSWERED, TIME_PRESSURE, CHANGED_TO_WRONG, DISTRACTOR, ITEM_NOT_LEARNED, UNKNOWN }

enum class ErrorStatus { ACTIVE, MASTERED, ARCHIVED }

enum class ReviewStatus { NEW, LEARNING, REVIEW, MASTERED }

data class ExamSection(val id: String, val kind: ExamSectionKind, val durationSeconds: Int?, val order: Int)

/** A reading text or listening audio shared by several questions. */
data class ExamPassage(val id: String, val title: String?, val text: String?, val furigana: String?, val audioPath: String?)

data class ExamQuestion(
    val id: String,
    val sectionId: String,
    val type: QuestionType,
    val format: AnswerFormat,
    val content: String,
    val contentFurigana: String?,
    val options: List<String>,
    val answerIndex: Int?,
    val acceptedAnswers: List<String>,
    val explanation: String?,
    val difficulty: Int?,
    val passageId: String?,
    val audioPath: String?,
    val order: Int,
    /** Vocabulary, kanji and grammar points the question tests (any number, any kind). */
    val relatedItems: List<ItemRef> = emptyList(),
)

/** A whole exam set with its content, in order (sections, then questions within each). */
data class ExamSet(
    val id: String,
    val title: String,
    val description: String?,
    val jlptLevel: String,
    val type: ExamType,
    val durationSeconds: Int,
    val source: String?,
    val createdAt: Long,
    val sections: List<ExamSection>,
    val passages: List<ExamPassage>,
    val questions: List<ExamQuestion>,
) {
    val totalQuestions: Int get() = questions.size

    fun skillOf(question: ExamQuestion): ExamSkill =
        ExamSkill.of(question.type, sections.firstOrNull { it.id == question.sectionId }?.kind ?: ExamSectionKind.VOCABULARY)
}

/** An exam set in a list: counts come from the questions and attempts, never stored apart. */
data class ExamSetSummary(
    val id: String,
    val title: String,
    val jlptLevel: String,
    val type: ExamType,
    val durationSeconds: Int,
    val totalQuestions: Int,
    val submittedAttempts: Int,
    /** Correct answers of the last submitted attempt; null before the first one. */
    val lastCorrect: Int?,
    /** Skills its questions train (one for a section exam, several for a full one). */
    val skills: Set<ExamSkill> = emptySet(),
    /** Attempts started and not submitted yet (0 or 1 in practice). */
    val inProgressAttempts: Int = 0,
)

/** Exam list filter; null means any. A set matches a skill when any of its questions trains it. */
data class ExamSetFilter(val jlptLevel: String? = null, val skill: ExamSkill? = null, val type: ExamType? = null) {
    fun matches(set: ExamSetSummary): Boolean =
        (jlptLevel == null || set.jlptLevel.equals(jlptLevel, ignoreCase = true)) &&
            (skill == null || skill in set.skills) &&
            (type == null || set.type == type)
}

data class ExamAttempt(
    val id: String,
    val examSetId: String,
    val examTitle: String,
    val startedAt: Long,
    val submittedAt: Long?,
    val timeLimitSeconds: Int,
    val durationSeconds: Long?,
    val correctCount: Int?,
    val questionCount: Int,
    val status: AttemptStatus,
    /** The question the learner was on; null before they moved (the first question). */
    val currentQuestionId: String? = null,
)

/** What the learner gave: an option for CHOICE questions, a text for TEXT questions. */
sealed interface AnswerInput {
    data class Choice(val index: Int) : AnswerInput
    data class Text(val value: String) : AnswerInput
}

data class ExamAnswer(
    val attemptId: String,
    val questionId: String,
    val selectedIndex: Int?,
    val textAnswer: String?,
    val isCorrect: Boolean,
    val timeSpentMs: Long,
    val confidence: Confidence?,
    val changeCount: Int,
    val answeredAt: Long,
)

data class ErrorRecord(
    val id: Long,
    val questionId: String,
    val examSetId: String,
    val errorType: ErrorType,
    val errorCount: Int,
    val firstWrongAt: Long,
    val lastWrongAt: Long,
    val masteryLevel: Int,
    val status: ErrorStatus,
    val notes: String?,
    /** The question as it was when last answered wrong (kept if the set changes or goes away). */
    val question: ExamQuestion?,
)

data class ReviewItem(
    val id: Long,
    val errorRecordId: Long,
    val nextReviewAt: Long,
    val intervalMinutes: Long,
    val stepIndex: Int,
    val reviewCount: Int,
    val successCount: Int,
    val lastReviewedAt: Long?,
    val status: ReviewStatus,
)

/** An error due for review, with its review state. */
data class ErrorReview(val error: ErrorRecord, val review: ReviewItem)

/** Result of a submitted attempt. */
data class AttemptResult(val attempt: ExamAttempt, val answers: List<ExamAnswer>)

object ExamGrading {
    /**
     * Whether [input] answers [question]. A choice must be the answer index; a typed answer
     * must equal an accepted answer after NFKC (full-width ⇄ half-width), trimming and
     * removing spaces, so "けいけん", "けい けん" and "ｹｲｹﾝ"-style width differences compare
     * as typed by the author. A wrong kind of input is wrong.
     */
    fun isCorrect(question: ExamQuestion, input: AnswerInput?): Boolean = when {
        input == null -> false
        question.format == AnswerFormat.CHOICE && input is AnswerInput.Choice -> input.index == question.answerIndex
        question.format == AnswerFormat.TEXT && input is AnswerInput.Text -> {
            val given = normalize(input.value)
            given.isNotEmpty() && question.acceptedAnswers.any { normalize(it) == given }
        }
        else -> false
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("[\\s　]+"), "").lowercase()

    /** Problems that would make a question impossible to answer or grade; empty when valid. */
    fun problems(question: ExamQuestion): List<String> = buildList {
        if (question.content.isBlank()) add("content")
        when (question.format) {
            AnswerFormat.CHOICE -> {
                if (question.options.size < 2) add("options")
                if (question.answerIndex == null || question.answerIndex !in question.options.indices) add("answerIndex")
            }
            AnswerFormat.TEXT -> if (question.acceptedAnswers.none { normalize(it).isNotEmpty() }) add("acceptedAnswers")
        }
        if (question.difficulty != null && question.difficulty !in 1..5) add("difficulty")
    }
}
