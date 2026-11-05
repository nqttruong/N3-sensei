package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.course.ItemLocation
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.practice.PracticeItem

/** Why a question was missed. Decided by explicit rules (see [RuleBasedErrorAnalyzer]); never guessed. */
enum class ErrorCategory {
    VOCABULARY_GAP,
    KANJI_CONFUSION,
    GRAMMAR_CONFUSION,
    READING_MISUNDERSTANDING,
    LISTENING_MISUNDERSTANDING,
    /** Only with evidence the learner knew the answer (see [RuleBasedErrorAnalyzer.CARELESS_MIN_RIGHT_BEFORE]). */
    CARELESS_MISTAKE,
    UNKNOWN,
}

/** One graded question of a submitted attempt: the history every analysis is computed from. */
data class GradedOccurrence(
    val attemptId: String,
    val examSetId: String,
    val submittedAt: Long,
    /** The attempt ended because the time ran out. */
    val expired: Boolean,
    val position: Int,
    val question: ExamQuestion,
    val section: ExamSectionKind,
    val skill: ExamSkill,
    val outcome: ItemOutcome,
    val selectedIndex: Int?,
    val textAnswer: String?,
    val timeSpentMs: Long?,
) {
    val missed: Boolean get() = outcome != ItemOutcome.CORRECT
}

/** A study item a question tests, as found in the content now (null [item]: no longer in the app). */
data class Knowledge(
    val ref: ItemRef,
    val item: PracticeItem?,
    val location: ItemLocation,
)

/** What the analysis rests on: shown to the learner, so every conclusion can be checked. */
sealed interface Evidence {
    data class TaskType(val type: QuestionType) : Evidence
    data class LinkedKnowledge(val type: ItemType) : Evidence
    data class Section(val section: ExamSectionKind) : Evidence
    data object LeftBlank : Evidence

    /** Answered right [times] times before, wrong now. */
    data class RightBefore(val times: Int) : Evidence

    /** Nothing in the question tells what went wrong. */
    data object NotEnoughData : Evidence
}

/** How two missed questions are related: the same study item, or the same kind of task. */
sealed interface LinkBasis {
    data class SameKnowledge(val ref: ItemRef) : LinkBasis
    data class SameTask(val type: QuestionType) : LinkBasis
}

data class LinkedMistake(val questionId: String, val content: String, val basis: LinkBasis)

enum class ReviewPriority { NORMAL, RAISED, HIGH }

/** Repeats of the same mistake, and why its review priority is what it is. */
data class RepeatInfo(
    /** Times this question was missed, this time included. */
    val timesMissed: Int,
    /** Times it was answered at all (right or wrong). */
    val timesSeen: Int,
    val lastMissedAt: Long,
    /** Other missed questions on the same study item or task. */
    val linked: List<LinkedMistake>,
    val priority: ReviewPriority,
)

/** A step to fix the mistake, decided by rules from the category and the linked study items. */
sealed interface Recommendation {
    /** Two or more grammar points the question links: compare them side by side. */
    data class CompareGrammar(val points: List<GrammarPoint>) : Recommendation

    data class StudyItem(val knowledge: Knowledge) : Recommendation

    /** Practise these words as flashcards. */
    data class PracticeWords(val refs: List<ItemRef>) : Recommendation

    /** No study item is linked: the skill in general. */
    data class StudySkill(val skill: ExamSkill) : Recommendation

    data object ReadingKeywords : Recommendation

    /** [audioAvailable]: the question has audio (the app cannot play it yet). */
    data class ListenAgain(val audioAvailable: Boolean) : Recommendation

    data object ReadCarefully : Recommendation

    data class ManageTime(val timeRanOut: Boolean) : Recommendation

    data object ReadExplanation : Recommendation
}

/** The analysis of one missed question. */
data class MistakeAnalysis(
    val occurrence: GradedOccurrence,
    /** The learner's answer as text: the option or what was typed; null when left blank. */
    val givenAnswer: String?,
    val correctAnswer: String,
    /** Null when the question has none: shown as "no explanation", never written for it. */
    val explanation: String?,
    val category: ErrorCategory,
    val evidence: List<Evidence>,
    val knowledge: List<Knowledge>,
    val recommendations: List<Recommendation>,
    /** Lessons of Học → Bài học where the linked items are studied (numbers, ascending). */
    val lessons: List<Int>,
    val repeat: RepeatInfo,
) {
    val question: ExamQuestion get() = occurrence.question
}
