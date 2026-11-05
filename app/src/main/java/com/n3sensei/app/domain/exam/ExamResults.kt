package com.n3sensei.app.domain.exam

/** How one question of a submitted attempt ended. Stored by name; never rename an entry. */
enum class ItemOutcome { CORRECT, WRONG, UNANSWERED }

/** One question of a submitted attempt, as it was when graded. */
data class ResultItem(
    /** 0-based place in the exam. */
    val position: Int,
    /** The question as answered (or as it was at the submit, when left blank). */
    val question: ExamQuestion,
    val section: ExamSectionKind,
    val skill: ExamSkill,
    val outcome: ItemOutcome,
    /** Null when left blank. */
    val answer: ExamAnswer?,
)

/** A submitted attempt with every question it had. */
data class AttemptRecord(
    val attempt: ExamAttempt,
    val items: List<ResultItem>,
    /** Reading texts and audio of the set, while it still exists (questions keep only the passage id). */
    val passages: Map<String, ExamPassage> = emptyMap(),
)

data class SkillScore(
    val skill: ExamSkill,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val unanswered: Int,
    /** Time spent on this skill's questions (answered ones; time on a question left blank is not known). */
    val timeSpentMs: Long,
) {
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

/**
 * The practice score of an attempt: one point per right answer. It is not a JLPT score: the
 * official exam scales each section and sets pass marks per section, which a practice set
 * cannot reproduce, so no pass or fail is derived from it.
 */
data class ExamScore(
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val unanswered: Int,
    /** Time used; null for an attempt not submitted. */
    val durationSeconds: Long?,
    val timeLimitSeconds: Int,
    /** Skills present in the attempt, in [ExamSkill] order. */
    val skills: List<SkillScore>,
) {
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

/** Something to work on, from the attempt's own results. */
sealed interface Improvement {
    /** A skill answered right less than [ExamScoring.WEAK_ACCURACY] of the time. */
    data class WeakSkill(val skill: ExamSkill, val correct: Int, val total: Int) : Improvement

    /** A kind of task missed (wrong or blank) at least twice. */
    data class FrequentMistake(val type: QuestionType, val missed: Int, val total: Int) : Improvement

    /** Questions left blank; [timeRanOut] when the attempt ended with the time. */
    data class Unanswered(val count: Int, val timeRanOut: Boolean) : Improvement
}

/** A submitted attempt with its score and what to improve. */
data class ExamReport(
    val record: AttemptRecord,
    val score: ExamScore,
    val improvements: List<Improvement>,
) {
    val attempt: ExamAttempt get() = record.attempt
    val items: List<ResultItem> get() = record.items
}

data class SkillChange(val skill: ExamSkill, val previous: SkillScore?, val current: SkillScore?) {
    /** Change in accuracy (−1 … 1); null when the skill is in only one of the attempts. */
    val accuracyDelta: Double? get() = if (previous != null && current != null) current.accuracy - previous.accuracy else null
}

/** [current] against an earlier attempt of the same set. */
data class ExamComparison(
    val previous: ExamAttempt,
    val previousScore: ExamScore,
    val currentScore: ExamScore,
    val skills: List<SkillChange>,
) {
    val correctDelta: Int get() = currentScore.correct - previousScore.correct
    val accuracyDelta: Double get() = currentScore.accuracy - previousScore.accuracy

    /** Seconds more (positive) or less (negative) than last time; null when either is unknown. */
    val durationDeltaSeconds: Long?
        get() = currentScore.durationSeconds?.let { now -> previousScore.durationSeconds?.let { now - it } }

    val improved: List<SkillChange> get() = skills.filter { (it.accuracyDelta ?: 0.0) > 0 }
    val declined: List<SkillChange> get() = skills.filter { (it.accuracyDelta ?: 0.0) < 0 }
}

object ExamScoring {

    /** Below this share of right answers a skill is listed as weak. */
    const val WEAK_ACCURACY = 0.6

    fun score(record: AttemptRecord): ExamScore {
        val items = record.items
        val skills = ExamSkill.entries.mapNotNull { skill ->
            val mine = items.filter { it.skill == skill }
            if (mine.isEmpty()) return@mapNotNull null
            SkillScore(
                skill, mine.size, mine.count { it.outcome == ItemOutcome.CORRECT }, mine.count { it.outcome == ItemOutcome.WRONG },
                mine.count { it.outcome == ItemOutcome.UNANSWERED }, mine.sumOf { it.answer?.timeSpentMs ?: 0 },
            )
        }
        return ExamScore(
            total = items.size,
            correct = items.count { it.outcome == ItemOutcome.CORRECT },
            wrong = items.count { it.outcome == ItemOutcome.WRONG },
            unanswered = items.count { it.outcome == ItemOutcome.UNANSWERED },
            durationSeconds = record.attempt.durationSeconds,
            timeLimitSeconds = record.attempt.timeLimitSeconds,
            skills = skills,
        )
    }

    /** Weakest skills first, then the tasks missed most, then blanks. */
    fun improvements(record: AttemptRecord, score: ExamScore = score(record)): List<Improvement> = buildList {
        score.skills.filter { it.accuracy < WEAK_ACCURACY }.sortedBy { it.accuracy }
            .forEach { add(Improvement.WeakSkill(it.skill, it.correct, it.total)) }
        record.items.filter { it.question.type != QuestionType.OTHER }.groupBy { it.question.type }
            .map { (type, items) -> Improvement.FrequentMistake(type, items.count { it.outcome != ItemOutcome.CORRECT }, items.size) }
            .filter { it.missed >= 2 }
            .sortedByDescending { it.missed }
            .take(3)
            .forEach(::add)
        if (score.unanswered > 0) add(Improvement.Unanswered(score.unanswered, record.attempt.status == AttemptStatus.EXPIRED))
    }

    fun report(record: AttemptRecord): ExamReport {
        val score = score(record)
        return ExamReport(record, score, improvements(record, score))
    }

    fun compare(previous: ExamReport, current: ExamReport): ExamComparison =
        ExamComparison(
            previous = previous.attempt,
            previousScore = previous.score,
            currentScore = current.score,
            skills = ExamSkill.entries.mapNotNull { skill ->
                val before = previous.score.skills.firstOrNull { it.skill == skill }
                val now = current.score.skills.firstOrNull { it.skill == skill }
                if (before == null && now == null) null else SkillChange(skill, before, now)
            },
        )
}
