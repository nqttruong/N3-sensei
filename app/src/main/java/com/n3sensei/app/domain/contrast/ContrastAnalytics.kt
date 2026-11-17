package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.repository.AttemptFilter
import com.n3sensei.app.domain.repository.ContrastAnalyticsRepository
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.repository.DirectionCount
import com.n3sensei.app.domain.repository.GroupOutcomes
import com.n3sensei.app.domain.repository.OutcomeCounts
import com.n3sensei.app.domain.repository.SessionOutcomes
import com.n3sensei.app.domain.repository.TypeOutcomes
import com.n3sensei.app.domain.srs.ReviewInterval
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * EXPERIMENTAL thresholds of the M7 analysis (ARCHITECTURE §9). Kept apart from [ContrastConfig]
 * (whose settled values M7 must not change); the UI never hard-codes any of them.
 */
data class ContrastAnalyticsPolicy(
    /** A direction seen in at least this many different sessions is a repeated mistake. */
    val repeatedMinSessions: Int = 2,
    /**
     * A repeated mistake stays "to reinforce" until this many later sessions of its group had
     * answers without it: one later correct answer is never enough.
     */
    val settledAfterSessions: Int = 2,
    /** Sessions counted as "recent" for the recent accuracy and the per-session trend. */
    val recentSessions: Int = 5,
    /** Below this many answered (non-skipped) answers a rate is "not enough data", never a %. */
    val minAnsweredForRate: Int = 5,
) {
    init {
        require(repeatedMinSessions >= 2) { "A repeated mistake needs ≥ 2 sessions" }
        require(settledAfterSessions >= 1 && recentSessions >= 1 && minAnsweredForRate >= 1) { "Minimums must be ≥ 1" }
    }

    /** The accuracy when it rests on enough answers; null = not enough data to show a rate. */
    fun reliable(accuracy: Accuracy): Accuracy? = accuracy.takeIf { it.answered >= minAnsweredForRate }
}

enum class DirectionStatus {
    /** Seen in one session only. */
    SINGLE,

    /** Repeated, and fewer than settledAfterSessions later sessions without it. */
    TO_REINFORCE,

    /** Repeated, then settledAfterSessions later sessions without it (not "mastered": mastery is separate). */
    SETTLING,
}

data class DirectionAnalysis(val count: DirectionCount, val status: DirectionStatus) {
    val repeated: Boolean get() = status != DirectionStatus.SINGLE
}

/** Recent sessions against everything before them; [recent]/[earlier] are null when too few answers. */
data class AccuracyTrend(val sessions: List<SessionOutcomes>, val recent: Accuracy?, val earlier: Accuracy?)

data class ErrorAnalysis(
    val totals: OutcomeCounts,
    val groups: List<GroupOutcomes>,
    val directions: List<DirectionAnalysis>,
    val byQuestionType: List<TypeOutcomes>,
    /** Wrong answers that chose a distractor, not another item of the group (not a confusion). */
    val otherIncorrect: Int,
    val trend: AccuracyTrend,
    val lastWrongAt: Instant?,
) {
    val repeated: List<DirectionAnalysis> get() = directions.filter { it.repeated }
    val toReinforce: List<DirectionAnalysis> get() = directions.filter { it.status == DirectionStatus.TO_REINFORCE }
}

/** Error analysis from the stored answers (aggregated in the database). Writes nothing. */
class GetContrastErrorAnalysis(
    private val analytics: ContrastAnalyticsRepository,
    private val policy: ContrastAnalyticsPolicy = ContrastAnalyticsPolicy(),
) {
    suspend operator fun invoke(filter: AttemptFilter = AttemptFilter()): ErrorAnalysis {
        val totals = analytics.totals(filter)
        val groups = analytics.byGroup(filter)
        val directions = analytics.directions(filter).map { DirectionAnalysis(it, statusOf(it)) }
        val sessions = analytics.recentSessions(filter, policy.recentSessions)
        val recent = sessions.fold(OutcomeCounts.NONE) { acc, s -> OutcomeCounts(acc.correct + s.counts.correct, acc.incorrect + s.counts.incorrect, acc.skipped + s.counts.skipped) }
        return ErrorAnalysis(
            totals = totals,
            groups = groups,
            directions = directions,
            byQuestionType = analytics.byQuestionType(filter),
            otherIncorrect = totals.incorrect - directions.sumOf { it.count.count },
            trend = AccuracyTrend(sessions, policy.reliable(recent.accuracy), policy.reliable((totals - recent).accuracy)),
            lastWrongAt = groups.mapNotNull { it.lastWrongAt }.maxOrNull(),
        )
    }

    fun statusOf(direction: DirectionCount): DirectionStatus = when {
        direction.sessions < policy.repeatedMinSessions -> DirectionStatus.SINGLE
        direction.sessionsSince >= policy.settledAfterSessions -> DirectionStatus.SETTLING
        else -> DirectionStatus.TO_REINFORCE
    }
}

// ---- Smart review ----

/** One scheduled group of the review queue. [step] is the index on Contrast's own ladder. */
data class ReviewQueueEntry(
    val group: ContrastGroup,
    val mastery: ContrastMastery,
    val dueAt: Instant,
    val step: Int,
    val interval: ReviewInterval,
    val reviewCount: Int,
)

/** A scheduled group that can no longer be reviewed (retired or removed): its history is kept. */
data class UnreviewableGroup(val groupId: String, val reasonVi: String?, val dueAt: Instant)

data class ReviewQueue(
    /** dueAt ≤ now, most overdue first. */
    val due: List<ReviewQueueEntry>,
    /** Scheduled later, soonest first. */
    val upcoming: List<ReviewQueueEntry>,
    /** Published groups with a repeated mistake still to reinforce (may also be due). */
    val toReinforce: List<String>,
    val unreviewable: List<UnreviewableGroup>,
    /** New groups still allowed today (they are not part of a review session). */
    val newAvailableToday: Int,
    val nextDueAt: Instant?,
)

/**
 * The review queue from Contrast's own schedule (contrast_progress, never learning_progress).
 * Reading it writes nothing: no session, no schedule.
 */
class GetContrastReviewQueue(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val analytics: ContrastAnalyticsRepository,
    private val config: ContrastConfig = ContrastConfig(),
    private val policy: ContrastAnalyticsPolicy = ContrastAnalyticsPolicy(),
) {
    private val getProgress = GetContrastProgress(content, practice, progress, config)
    private val scheduler = ContrastScheduler(config)

    suspend operator fun invoke(now: Instant, zone: ZoneId): ReviewQueue {
        val ordered = content.observeGroups().first()
        val published = ordered.associateBy { it.id }
        val order = ordered.withIndex().associate { (i, g) -> g.id to i }
        val rows = progress.allProgress()
        val scheduledPublished = rows.filter { it.groupId in published }
        val entries = scheduledPublished.associate { p ->
            p.groupId to ReviewQueueEntry(published.getValue(p.groupId), getProgress.ofGroup(p.groupId, zone).mastery, p.dueAt, config.ladder.clampStep(p.stepIndex), config.ladder[p.stepIndex], p.reviewCount)
        }
        val byDue = compareBy<ReviewQueueEntry> { it.dueAt }.thenBy { order.getValue(it.group.id) }
        val due = scheduledPublished.filter { scheduler.isDue(it, now) }.map { entries.getValue(it.groupId) }.sortedWith(byDue)
        val upcoming = scheduledPublished.filterNot { scheduler.isDue(it, now) }.map { entries.getValue(it.groupId) }.sortedWith(byDue)
        val directions = GetContrastErrorAnalysis(analytics, policy)
        val toReinforce = analytics.directions().filter { it.groupId in published && directions.statusOf(it) == DirectionStatus.TO_REINFORCE }
            .map { it.groupId }.distinct()
        val unreviewable = rows.filter { it.groupId !in published }
            .map { UnreviewableGroup(it.groupId, content.retiredGroup(it.groupId)?.reasonVi, it.dueAt) }
            .sortedBy { it.groupId }
        return ReviewQueue(
            due = due,
            upcoming = upcoming,
            toReinforce = toReinforce,
            unreviewable = unreviewable,
            newAvailableToday = GetDueContrastReviews(progress, config)(now, zone).newAvailableToday,
            nextDueAt = upcoming.minOfOrNull { it.dueAt },
        )
    }
}

// ---- Progress ----

data class CategoryProgress(val type: ContrastContentType, val total: Int, val started: Int, val mastered: Int, val due: Int)

data class GroupProgressRow(val group: ContrastGroup, val mastery: ContrastMastery, val dueAt: Instant?, val counts: OutcomeCounts)

data class ContrastProgressReport(
    /** Published (reviewed, installed) groups: the denominator of every group count. */
    val published: Int,
    val notStarted: Int,
    /** LEARNING + REVIEWING. */
    val learning: Int,
    /** "Cần củng cố" as in M5: scheduled and due now (overlaps learning / mastered). */
    val due: Int,
    val mastered: Int,
    /** Every stored answer, including those of groups no longer published (history is never dropped). */
    val totals: OutcomeCounts,
    /** The part of [totals] in groups that are no longer published. */
    val historical: OutcomeCounts,
    val categories: List<CategoryProgress>,
    val groups: List<GroupProgressRow>,
    val byQuestionType: List<TypeOutcomes>,
    val nextDueAt: Instant?,
)

/**
 * Progress of Contrast Lab from real data. Mastery is the M1 evaluator's ([GetContrastProgress]):
 * a group without a schedule is NEW by that rule, so only scheduled groups are evaluated.
 */
class GetContrastProgressReport(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val analytics: ContrastAnalyticsRepository,
    private val config: ContrastConfig = ContrastConfig(),
) {
    private val getProgress = GetContrastProgress(content, practice, progress, config)

    suspend operator fun invoke(now: Instant, zone: ZoneId): ContrastProgressReport {
        val groups = content.observeGroups().first()
        val scheduled = progress.allProgress().associateBy { it.groupId }
        val counts = analytics.byGroup().associateBy { it.groupId }
        val rows = groups.map { g ->
            val p = scheduled[g.id]
            val mastery = if (p == null) ContrastMastery.NEW else getProgress.ofGroup(g.id, zone).mastery
            GroupProgressRow(g, mastery, p?.dueAt, counts[g.id]?.counts ?: OutcomeCounts.NONE)
        }
        val totals = analytics.totals()
        val publishedIds = groups.map { it.id }.toSet()
        val historical = counts.values.filter { it.groupId !in publishedIds }
            .fold(OutcomeCounts.NONE) { acc, g -> OutcomeCounts(acc.correct + g.counts.correct, acc.incorrect + g.counts.incorrect, acc.skipped + g.counts.skipped) }
        fun isDue(row: GroupProgressRow) = row.dueAt != null && !row.dueAt.isAfter(now)
        return ContrastProgressReport(
            published = rows.size,
            notStarted = rows.count { it.mastery == ContrastMastery.NEW },
            learning = rows.count { it.mastery == ContrastMastery.LEARNING || it.mastery == ContrastMastery.REVIEWING },
            due = rows.count(::isDue),
            mastered = rows.count { it.mastery == ContrastMastery.MASTERED },
            totals = totals,
            historical = historical,
            categories = ContrastContentType.entries.mapNotNull { type ->
                val ofType = rows.filter { it.group.contentType == type }
                if (ofType.isEmpty()) null else CategoryProgress(type, ofType.size, ofType.count { it.mastery != ContrastMastery.NEW }, ofType.count { it.mastery == ContrastMastery.MASTERED }, ofType.count(::isDue))
            },
            groups = rows,
            byQuestionType = analytics.byQuestionType(),
            nextDueAt = rows.mapNotNull { it.dueAt }.filter { it.isAfter(now) }.minOrNull(),
        )
    }
}
