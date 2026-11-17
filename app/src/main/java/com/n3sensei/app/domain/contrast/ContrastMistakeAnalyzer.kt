package com.n3sensei.app.domain.contrast

import java.time.Instant

/** A directed confusion inside one group, with how often and when it last happened. */
data class ConfusionCount(
    val groupId: String,
    val expectedItemId: String,
    val chosenItemId: String,
    val count: Int,
    /** In how many different sessions: ≥ 2 makes it a repeated error. */
    val sessions: Int,
    val lastAt: Instant,
)

data class GroupErrorSummary(val groupId: String, val accuracy: Accuracy, val incorrect: Int, val skipped: Int, val lastWrongAt: Instant?)

data class TypeErrorSummary(val type: ContrastQuestionType, val accuracy: Accuracy, val incorrect: Int)

/** A question answered wrong in ≥ 2 different sessions. */
data class RepeatedQuestionError(val groupId: String, val questionId: String, val sessions: Int, val lastAt: Instant)

/** Error analysis (ARCHITECTURE §9). Item ids are the stable identifiers; labels are never used as keys. */
data class MistakeReport(
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    /** correct / (correct + incorrect); skipped answers are not counted. */
    val accuracy: Accuracy,
    /** Most incorrect answers first. */
    val groups: List<GroupErrorSummary>,
    /** A → B and B → A are separate entries; most frequent first. */
    val confusions: List<ConfusionCount>,
    val byQuestionType: List<TypeErrorSummary>,
    val repeatedConfusions: List<ConfusionCount>,
    val repeatedQuestions: List<RepeatedQuestionError>,
    val lastWrongAt: Instant?,
    /** All delayed answers (≥ ContrastConfig.delayedGap after the previous session of the group). */
    val delayedAccuracy: Accuracy,
    /** The main measure: delayed answers on questions never answered before. */
    val freshDelayedAccuracy: Accuracy,
    /** Answers on distractors (not one of the group's items): wrong, but not a confusion between items. */
    val otherIncorrect: Int,
)

/** Turns attempts into a [MistakeReport]. Pending positions have no attempt, so they never count. */
class ContrastMistakeAnalyzer(private val config: ContrastConfig = ContrastConfig()) {

    /**
     * [attempts] in the analysed range; [groupHistory] gives each group's full history, needed to
     * tell delayed answers (the range may start after the previous session).
     */
    fun analyze(attempts: List<ContrastAttempt>, groupHistory: (String) -> List<ContrastAttempt> = { g -> attempts.filter { it.groupId == g } }): MistakeReport {
        val incorrect = attempts.filter { it.outcome == AnswerOutcome.INCORRECT }
        val confusions = incorrect.mapNotNull { a -> a.confusion?.let { it to a } }
            .groupBy({ (c, a) -> Triple(a.groupId, c.expectedItemId, c.chosenItemId) }, { it.second })
            .map { (key, list) -> ConfusionCount(key.first, key.second, key.third, list.size, list.map { it.sessionId }.toSet().size, list.maxOf { it.answeredAt }) }
            .sortedWith(compareByDescending<ConfusionCount> { it.count }.thenByDescending { it.lastAt }.thenBy { it.groupId }.thenBy { it.expectedItemId }.thenBy { it.chosenItemId })
        val groups = attempts.groupBy { it.groupId }.map { (groupId, list) ->
            GroupErrorSummary(
                groupId = groupId,
                accuracy = Accuracy.of(list),
                incorrect = list.count { it.outcome == AnswerOutcome.INCORRECT },
                skipped = list.count { it.outcome == AnswerOutcome.SKIPPED },
                lastWrongAt = list.filter { it.outcome == AnswerOutcome.INCORRECT }.maxOfOrNull { it.answeredAt },
            )
        }.sortedWith(compareByDescending<GroupErrorSummary> { it.incorrect }.thenBy { it.groupId })
        val byType = attempts.groupBy { it.questionType }.map { (type, list) ->
            TypeErrorSummary(type, Accuracy.of(list), list.count { it.outcome == AnswerOutcome.INCORRECT })
        }.sortedWith(compareByDescending<TypeErrorSummary> { it.incorrect }.thenBy { it.type })
        val repeatedQuestions = incorrect.groupBy { it.groupId to it.questionId }
            .mapNotNull { (key, list) ->
                val sessions = list.map { it.sessionId }.toSet().size
                if (sessions >= 2) RepeatedQuestionError(key.first, key.second, sessions, list.maxOf { it.answeredAt }) else null
            }
            .sortedWith(compareByDescending<RepeatedQuestionError> { it.sessions }.thenBy { it.questionId })
        val inRange = attempts.map { it.requestId }.toSet()
        val delayed = mutableSetOf<String>()
        val fresh = mutableSetOf<String>()
        attempts.map { it.groupId }.distinct().forEach { groupId ->
            val classified = DelayedAnswers.classify(groupHistory(groupId), config.delayedGap)
            delayed += classified.delayed.intersect(inRange)
            fresh += classified.fresh.intersect(inRange)
        }
        return MistakeReport(
            total = attempts.size,
            correct = attempts.count { it.outcome == AnswerOutcome.CORRECT },
            incorrect = incorrect.size,
            skipped = attempts.count { it.outcome == AnswerOutcome.SKIPPED },
            accuracy = Accuracy.of(attempts),
            groups = groups,
            confusions = confusions,
            byQuestionType = byType,
            repeatedConfusions = confusions.filter { it.sessions >= 2 },
            repeatedQuestions = repeatedQuestions,
            lastWrongAt = incorrect.maxOfOrNull { it.answeredAt },
            delayedAccuracy = Accuracy.of(attempts.filter { it.requestId in delayed }),
            freshDelayedAccuracy = Accuracy.of(attempts.filter { it.requestId in fresh }),
            otherIncorrect = incorrect.count { it.confusion == null },
        )
    }
}
