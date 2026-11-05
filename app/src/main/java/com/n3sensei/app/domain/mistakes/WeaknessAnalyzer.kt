package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.model.ItemRef

/** Missed share of one skill over all submitted attempts. */
data class SkillWeakness(val skill: ExamSkill, val answered: Int, val missed: Int) {
    val missRate: Double get() = if (answered == 0) 0.0 else missed.toDouble() / answered

    /** Enough questions for the rate to mean something ([WeaknessAnalyzer.MIN_SKILL_QUESTIONS]). */
    val reliable: Boolean get() = answered >= WeaknessAnalyzer.MIN_SKILL_QUESTIONS
}

/** How one study item fared in every question linked to it. */
data class KnowledgeStat(
    val ref: ItemRef,
    val seen: Int,
    val missed: Int,
    /** The latest question on it was missed. */
    val lastMissed: Boolean,
)

/** A question missed more than once. */
data class RecurringQuestion(val questionId: String, val content: String, val seen: Int, val missed: Int, val lastMissed: Boolean)

data class WeaknessReport(
    /** Reliable skills first, by missed share; then those with too few questions to judge. */
    val skills: List<SkillWeakness>,
    /** Items missed at least twice, most missed first. */
    val frequentKnowledge: List<KnowledgeStat>,
    /** Questions missed at least twice. */
    val recurring: List<RecurringQuestion>,
    /** Items whose latest question was missed: not fixed yet. */
    val unresolved: List<KnowledgeStat>,
    /** Items missed before whose latest question was right. */
    val improved: List<KnowledgeStat>,
    /** Questions missed before and answered right the latest time. */
    val improvedQuestions: List<RecurringQuestion>,
) {
    val isEmpty: Boolean get() = skills.isEmpty()
}

/**
 * Weak points over every submitted attempt, by counting: no rate is called weak without
 * [MIN_SKILL_QUESTIONS] questions, and an item counts as a frequent mistake only when missed in
 * at least two questions.
 */
object WeaknessAnalyzer {

    const val MIN_SKILL_QUESTIONS = 10
    const val MIN_MISSES = 2

    fun analyze(history: List<GradedOccurrence>): WeaknessReport {
        val ordered = history.sortedWith(compareBy({ it.submittedAt }, { it.position }))

        val skills = ExamSkill.entries.mapNotNull { skill ->
            val mine = ordered.filter { it.skill == skill }
            if (mine.isEmpty()) null else SkillWeakness(skill, mine.size, mine.count { it.missed })
        }.sortedWith(compareBy<SkillWeakness>({ !it.reliable }, { -it.missRate }))

        val byRef = ordered.flatMap { o -> o.question.relatedItems.distinct().map { it to o } }.groupBy({ it.first }, { it.second })
        val stats = byRef.map { (ref, list) -> KnowledgeStat(ref, list.size, list.count { it.missed }, list.last().missed) }

        val byQuestion = ordered.groupBy { it.question.id }.map { (id, list) ->
            RecurringQuestion(id, list.last().question.content, list.size, list.count { it.missed }, list.last().missed)
        }

        return WeaknessReport(
            skills = skills,
            frequentKnowledge = stats.filter { it.missed >= MIN_MISSES }.sortedByDescending { it.missed },
            recurring = byQuestion.filter { it.missed >= MIN_MISSES }.sortedByDescending { it.missed },
            unresolved = stats.filter { it.lastMissed }.sortedByDescending { it.missed },
            improved = stats.filter { it.missed > 0 && !it.lastMissed },
            improvedQuestions = byQuestion.filter { it.missed > 0 && !it.lastMissed },
        )
    }
}
