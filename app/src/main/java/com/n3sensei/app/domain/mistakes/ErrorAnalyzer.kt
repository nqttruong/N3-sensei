package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.practice.PracticeItem

/** Everything known about one missed question: the occurrence, the learner's history and the study items it tests. */
data class MistakeContext(
    val occurrence: GradedOccurrence,
    /** Graded questions of all submitted attempts (any order); only those up to [occurrence] are used. */
    val history: List<GradedOccurrence>,
    /** The question's study items, as found in the content now. */
    val knowledge: List<Knowledge>,
    /** The question has audio of its own or through its passage. */
    val hasAudio: Boolean = occurrence.question.audioPath != null,
)

/**
 * Analyses a missed question. [RuleBasedErrorAnalyzer] is the only implementation for now; another
 * (an AI one, later) can take its place, as long as it states its evidence and does not claim
 * more than the data shows.
 */
fun interface ErrorAnalyzer {
    fun analyze(context: MistakeContext): MistakeAnalysis
}

/**
 * Deterministic analysis, from the question's task, its linked study items, its section and the
 * learner's own history. Each conclusion lists its evidence. When the data does not tell, the
 * category is UNKNOWN. A wrong answer alone is never called careless.
 */
class RuleBasedErrorAnalyzer : ErrorAnalyzer {

    override fun analyze(context: MistakeContext): MistakeAnalysis {
        val now = context.occurrence
        val q = now.question
        val past = context.history.filter { it.submittedAt <= now.submittedAt }
        val earlierOfThisQuestion = past.filter { it.question.id == q.id && it.attemptId != now.attemptId }
        val (category, evidence) = categorize(now, earlierOfThisQuestion.count { it.outcome == ItemOutcome.CORRECT })
        return MistakeAnalysis(
            occurrence = now,
            givenAnswer = when (q.format) {
                AnswerFormat.CHOICE -> now.selectedIndex?.let { q.options.getOrNull(it) }
                AnswerFormat.TEXT -> now.textAnswer
            },
            correctAnswer = when (q.format) {
                AnswerFormat.CHOICE -> q.answerIndex?.let { q.options.getOrNull(it) }.orEmpty()
                AnswerFormat.TEXT -> q.acceptedAnswers.joinToString(" / ")
            },
            explanation = q.explanation?.takeIf { it.isNotBlank() },
            category = category,
            evidence = evidence,
            knowledge = context.knowledge,
            recommendations = recommend(category, now, context.knowledge, context.hasAudio),
            lessons = context.knowledge.mapNotNull { it.location.courseLesson }.distinct().sorted(),
            repeat = repeatInfo(now, past),
        )
    }

    /**
     * The category of a miss and its evidence alone, from the occurrence and how many times the
     * same question was answered right in earlier attempts (for statistics over many misses).
     */
    fun categorize(now: GradedOccurrence, rightBefore: Int): Pair<ErrorCategory, List<Evidence>> {
        if (now.outcome == ItemOutcome.UNANSWERED) return ErrorCategory.UNKNOWN to listOf(Evidence.LeftBlank)
        if (rightBefore >= CARELESS_MIN_RIGHT_BEFORE) return ErrorCategory.CARELESS_MISTAKE to listOf(Evidence.RightBefore(rightBefore))
        val type = now.question.type
        byTask(type)?.let { return it to listOf(Evidence.TaskType(type)) }
        // An untyped question: the kind of study item it is linked to, if only one kind…
        val kinds = now.question.relatedItems.map { it.type }.distinct()
        if (kinds.size == 1) {
            val kind = kinds.single()
            val category = when (kind) {
                ItemType.VOCABULARY -> ErrorCategory.VOCABULARY_GAP
                ItemType.KANJI -> ErrorCategory.KANJI_CONFUSION
                ItemType.GRAMMAR -> ErrorCategory.GRAMMAR_CONFUSION
            }
            return category to listOf(Evidence.LinkedKnowledge(kind))
        }
        // …else its section, except "Từ vựng", which holds both vocabulary and kanji tasks.
        return when (now.section) {
            ExamSectionKind.GRAMMAR -> ErrorCategory.GRAMMAR_CONFUSION to listOf(Evidence.Section(now.section))
            ExamSectionKind.READING -> ErrorCategory.READING_MISUNDERSTANDING to listOf(Evidence.Section(now.section))
            ExamSectionKind.LISTENING -> ErrorCategory.LISTENING_MISUNDERSTANDING to listOf(Evidence.Section(now.section))
            ExamSectionKind.VOCABULARY -> ErrorCategory.UNKNOWN to listOf(Evidence.NotEnoughData)
        }
    }

    private fun byTask(type: QuestionType): ErrorCategory? = when (type) {
        QuestionType.KANJI_READING, QuestionType.ORTHOGRAPHY -> ErrorCategory.KANJI_CONFUSION
        QuestionType.CONTEXT, QuestionType.PARAPHRASE, QuestionType.USAGE -> ErrorCategory.VOCABULARY_GAP
        QuestionType.GRAMMAR_FORM, QuestionType.SENTENCE_ORDER, QuestionType.TEXT_GRAMMAR -> ErrorCategory.GRAMMAR_CONFUSION
        QuestionType.READING_SHORT, QuestionType.READING_MID, QuestionType.READING_LONG, QuestionType.INFORMATION_SEARCH ->
            ErrorCategory.READING_MISUNDERSTANDING
        QuestionType.LISTENING_TASK, QuestionType.LISTENING_POINT, QuestionType.LISTENING_SUMMARY,
        QuestionType.LISTENING_EXPRESSION, QuestionType.LISTENING_RESPONSE -> ErrorCategory.LISTENING_MISUNDERSTANDING
        QuestionType.OTHER -> null
    }

    private fun recommend(category: ErrorCategory, now: GradedOccurrence, knowledge: List<Knowledge>, hasAudio: Boolean): List<Recommendation> {
        fun of(type: ItemType) = knowledge.filter { it.ref.type == type }
        val found = knowledge.filter { it.item != null }
        return when (category) {
            ErrorCategory.GRAMMAR_CONFUSION -> {
                val points = found.mapNotNull { (it.item as? PracticeItem.GrammarItem)?.grammar }
                when {
                    points.size >= 2 -> listOf(Recommendation.CompareGrammar(points))
                    points.size == 1 -> found.filter { it.ref.type == ItemType.GRAMMAR }.map(Recommendation::StudyItem)
                    else -> listOf(Recommendation.StudySkill(ExamSkill.GRAMMAR))
                }
            }
            ErrorCategory.VOCABULARY_GAP -> {
                val words = of(ItemType.VOCABULARY).filter { it.item != null }
                if (words.isEmpty()) {
                    listOf(Recommendation.StudySkill(ExamSkill.VOCABULARY))
                } else {
                    listOf(Recommendation.PracticeWords(words.map { it.ref })) + words.map(Recommendation::StudyItem)
                }
            }
            ErrorCategory.KANJI_CONFUSION -> {
                val items = (of(ItemType.KANJI) + of(ItemType.VOCABULARY)).filter { it.item != null }
                if (items.isEmpty()) listOf(Recommendation.StudySkill(ExamSkill.KANJI)) else items.map(Recommendation::StudyItem)
            }
            ErrorCategory.READING_MISUNDERSTANDING -> listOf(Recommendation.ReadingKeywords, Recommendation.ReadExplanation)
            ErrorCategory.LISTENING_MISUNDERSTANDING -> listOf(
                Recommendation.ListenAgain(audioAvailable = hasAudio),
                Recommendation.ReadExplanation,
            )
            ErrorCategory.CARELESS_MISTAKE -> listOf(Recommendation.ReadCarefully)
            ErrorCategory.UNKNOWN ->
                if (now.outcome == ItemOutcome.UNANSWERED) {
                    listOf(Recommendation.ManageTime(now.expired)) + found.map(Recommendation::StudyItem)
                } else {
                    listOf(Recommendation.ReadExplanation) + found.map(Recommendation::StudyItem)
                }
        }
    }

    private fun repeatInfo(now: GradedOccurrence, past: List<GradedOccurrence>): RepeatInfo {
        val q = now.question
        val sameQuestion = past.filter { it.question.id == q.id }
        val refs = q.relatedItems.toSet()
        // Other questions missed so far, linked by a study item they share, or else by the same
        // task. Never by the answer that was picked.
        val linked = past.filter { it.missed && it.question.id != q.id }
            .distinctBy { it.question.id }
            .mapNotNull { other ->
                val shared = other.question.relatedItems.firstOrNull { it in refs }
                val basis = when {
                    shared != null -> LinkBasis.SameKnowledge(shared)
                    q.type != QuestionType.OTHER && other.question.type == q.type -> LinkBasis.SameTask(q.type)
                    else -> null
                }
                basis?.let { LinkedMistake(other.question.id, other.question.content, it) }
            }
            .sortedBy { if (it.basis is LinkBasis.SameKnowledge) 0 else 1 }
        val timesMissed = sameQuestion.count { it.missed }
        val sameKnowledge = linked.count { it.basis is LinkBasis.SameKnowledge }
        val priority = when {
            timesMissed >= 3 || sameKnowledge >= 2 -> ReviewPriority.HIGH
            timesMissed >= 2 || sameKnowledge >= 1 -> ReviewPriority.RAISED
            else -> ReviewPriority.NORMAL
        }
        return RepeatInfo(timesMissed, sameQuestion.size, now.submittedAt, linked, priority)
    }

    companion object {
        /** A wrong answer is called careless only after at least this many right answers to the same question. */
        const val CARELESS_MIN_RIGHT_BEFORE = 2
    }
}
