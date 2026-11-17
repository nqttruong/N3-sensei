package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/** Error analysis over [from, to) (null = unbounded), optionally for one content type. */
class AnalyzeContrastMistakes(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val config: ContrastConfig = ContrastConfig(),
) {
    private val analyzer = ContrastMistakeAnalyzer(config)

    suspend operator fun invoke(from: Instant? = null, to: Instant? = null, contentType: ContrastContentType? = null): MistakeReport {
        var attempts = practice.attemptsBetween(from, to)
        if (contentType != null) {
            // Retired or removed groups have no type any more, so they only appear unfiltered.
            val ofType = content.observeGroups(ContrastFilter(contentType = contentType)).first().map { it.id }.toSet()
            attempts = attempts.filter { it.groupId in ofType }
        }
        val histories = attempts.map { it.groupId }.distinct().associateWith { practice.attemptsOfGroup(it) }
        return analyzer.analyze(attempts) { histories.getValue(it) }
    }
}

/** Mastery overview: how many published groups are in each state. */
data class ContrastProgressOverview(val byMastery: Map<ContrastMastery, Int>, val groups: List<GroupMastery>)

/** Progress of one group, or the overview of every published group. */
class GetContrastProgress(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val config: ContrastConfig = ContrastConfig(),
) {
    private val evaluator = ContrastMasteryEvaluator(config)

    suspend fun ofGroup(groupId: String, zone: ZoneId): GroupMastery =
        evaluator.evaluate(groupId, progress.progress(groupId), practice.attemptsOfGroup(groupId), progress.reviews(groupId), zone)

    suspend fun overview(zone: ZoneId): ContrastProgressOverview {
        val groups = content.observeGroups().first().map { ofGroup(it.id, zone) }
        return ContrastProgressOverview(groups.groupingBy { it.mastery }.eachCount(), groups)
    }
}

data class DueSummary(val dueCount: Int, val newAvailableToday: Int, val nextDueAt: Instant?)

/** What the Contrast Lab home shows: due groups and new groups still allowed today (no streak, no XP: rule R7). */
class GetDueContrastReviews(
    private val progress: ContrastProgressRepository,
    private val config: ContrastConfig = ContrastConfig(),
) {
    suspend operator fun invoke(now: Instant, zone: ZoneId): DueSummary {
        val overview = progress.observeOverview(now).first()
        val introducedToday = progress.newGroupsIntroducedBetween(ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone))
        val allowed = (config.newGroupsPerDay - introducedToday).coerceAtLeast(0)
        val available = if (allowed == 0) 0 else progress.newGroups(allowed).size
        return DueSummary(overview.dueCount, available, overview.nextDueAt)
    }
}
