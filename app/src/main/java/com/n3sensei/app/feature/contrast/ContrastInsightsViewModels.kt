package com.n3sensei.app.feature.contrast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.Accuracy
import com.n3sensei.app.domain.contrast.ContrastAnalyticsPolicy
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastProgressReport
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.DirectionStatus
import com.n3sensei.app.domain.contrast.GetContrastErrorAnalysis
import com.n3sensei.app.domain.contrast.GetContrastProgressReport
import com.n3sensei.app.domain.contrast.GetContrastReviewQueue
import com.n3sensei.app.domain.contrast.ReviewQueue
import com.n3sensei.app.domain.repository.AttemptFilter
import com.n3sensei.app.domain.repository.ContrastAnalyticsRepository
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.repository.MistakeSnapshot
import com.n3sensei.app.domain.repository.OutcomeCounts
import com.n3sensei.app.domain.repository.SessionOutcomes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A rate as the screens show it: [accuracy] is null when nothing was answered; [reliable] is false
 * when fewer than the policy's minimum answers (then no percentage is shown).
 */
data class RateView(val accuracy: Accuracy, val minAnswered: Int) {
    val reliable: Boolean get() = accuracy.answered >= minAnswered
}

private fun ContrastAnalyticsPolicy.rate(counts: OutcomeCounts) = RateView(counts.accuracy, minAnsweredForRate)

/** Questions left in the resumable ACTIVE session (shared by Home and Review); null when none. */
internal suspend fun activeRemaining(practice: ContrastPracticeRepository, content: ContrastContentRepository, config: ContrastConfig, now: Instant): Int? {
    val session = practice.activeSession()?.takeIf { Duration.between(it.lastActivityAt, now) <= config.resumeWindow } ?: return null
    val answered = practice.attempts(session.id).map { it.position }.toSet()
    return session.items.count { it.position !in answered && content.getQuestion(it.questionId) != null }.takeIf { it > 0 }
}

/** Title of a group: the published one, or a "retired" label (its history is kept, never moved to another id). */
internal fun titleOf(groupId: String, published: Map<String, ContrastGroup>, retiredLabel: (String) -> String): String =
    published[groupId]?.title ?: retiredLabel(groupId)

// ---- Errors ----

data class DirectionRow(
    val groupId: String,
    val groupTitle: String,
    /** False for a retired / removed group: no link to Compare. */
    val published: Boolean,
    val expectedLabel: String,
    val chosenLabel: String,
    val count: Int,
    val sessions: Int,
    val sessionsSince: Int,
    val status: DirectionStatus,
    val example: MistakeSnapshot?,
    /** The example's question revision differs from the current one (or the question is gone). */
    val exampleOutdated: Boolean,
)

data class ErrorGroupRow(val groupId: String, val title: String, val published: Boolean, val incorrect: Int, val rate: RateView)

data class ErrorTypeRow(val type: ContrastQuestionType, val incorrect: Int, val rate: RateView)

data class RecentMistakeRow(val groupTitle: String, val snapshot: MistakeSnapshot)

data class ContrastErrorsUiState(
    val totals: OutcomeCounts,
    val rate: RateView,
    val directions: List<DirectionRow>,
    val groups: List<ErrorGroupRow>,
    val types: List<ErrorTypeRow>,
    val recent: List<RecentMistakeRow>,
    val otherIncorrect: Int,
    val sessions: List<SessionOutcomes>,
    val recentRate: Accuracy?,
    val earlierRate: Accuracy?,
    val recentSessionCount: Int,
    val contentType: ContrastContentType?,
    val questionType: ContrastQuestionType?,
    val groupId: String?,
    /** Published groups for the group filter. */
    val groupOptions: List<ContrastGroup>,
)

/** Contrast error analysis. Read-only: filters live in [SavedStateHandle] (kept on Back / process death). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContrastErrorsViewModel(
    private val content: ContrastContentRepository,
    private val analytics: ContrastAnalyticsRepository,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val policy: ContrastAnalyticsPolicy = ContrastAnalyticsPolicy(),
    private val retiredLabel: (String) -> String = { it },
) : ViewModel() {

    private val analyze = GetContrastErrorAnalysis(analytics, policy)
    private val contentType = MutableStateFlow(saved.get<String>(KEY_CONTENT)?.let { c -> ContrastContentType.entries.firstOrNull { it.name == c } })
    private val questionType = MutableStateFlow(saved.get<String>(KEY_QTYPE)?.let { c -> ContrastQuestionType.entries.firstOrNull { it.name == c } })
    private val groupId = MutableStateFlow(saved.get<String>(KEY_GROUP))
    private val reload = MutableStateFlow(0)

    val state: StateFlow<UiState<ContrastErrorsUiState>> = combine(contentType, questionType, groupId, reload) { c, q, g, _ -> AttemptFilter(c, q, g) }
        .flatMapLatest { filter -> combine(content.observeGroups(), analytics.observeChanges()) { groups, _ -> filter to groups } }
        .mapLatest<Pair<AttemptFilter, List<ContrastGroup>>, UiState<ContrastErrorsUiState>> { (filter, groups) -> UiState.Success(build(filter, groups)) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun selectContentType(value: ContrastContentType?) {
        contentType.value = value
        saved[KEY_CONTENT] = value?.name
    }

    fun selectQuestionType(value: ContrastQuestionType?) {
        questionType.value = value
        saved[KEY_QTYPE] = value?.name
    }

    fun selectGroup(value: String?) {
        groupId.value = value
        saved[KEY_GROUP] = value
    }

    fun retry() {
        reload.value++
    }

    private suspend fun build(filter: AttemptFilter, groups: List<ContrastGroup>): ContrastErrorsUiState {
        val published = groups.associateBy { it.id }
        val report = analyze(filter)
        val labels = mutableMapOf<String, Map<String, String>>()
        suspend fun label(groupId: String, itemId: String) =
            labels.getOrPut(groupId) { content.getDetail(groupId)?.items.orEmpty().associate { it.id to it.label } }[itemId] ?: itemId
        val directions = report.directions.take(MAX_DIRECTIONS).map { d ->
            val c = d.count
            val example = analytics.latestExample(c.groupId, c.expectedItemId, c.chosenItemId)
            val current = example?.let { content.getQuestion(it.questionId) }
            DirectionRow(
                groupId = c.groupId,
                groupTitle = titleOf(c.groupId, published, retiredLabel),
                published = c.groupId in published,
                expectedLabel = label(c.groupId, c.expectedItemId),
                chosenLabel = label(c.groupId, c.chosenItemId),
                count = c.count,
                sessions = c.sessions,
                sessionsSince = c.sessionsSince,
                status = d.status,
                example = example,
                exampleOutdated = example != null && (current == null || current.revision != example.questionRevision),
            )
        }
        return ContrastErrorsUiState(
            totals = report.totals,
            rate = policy.rate(report.totals),
            directions = directions,
            groups = report.groups.filter { it.counts.incorrect > 0 }.take(MAX_GROUPS).map {
                ErrorGroupRow(it.groupId, titleOf(it.groupId, published, retiredLabel), it.groupId in published, it.counts.incorrect, policy.rate(it.counts))
            },
            types = report.byQuestionType.map { ErrorTypeRow(it.type, it.counts.incorrect, policy.rate(it.counts)) },
            recent = analytics.recentMistakes(filter, MAX_RECENT).map { RecentMistakeRow(titleOf(it.groupId, published, retiredLabel), it) },
            otherIncorrect = report.otherIncorrect,
            sessions = report.trend.sessions,
            recentRate = report.trend.recent,
            earlierRate = report.trend.earlier,
            recentSessionCount = report.trend.sessions.size,
            contentType = filter.contentType,
            questionType = filter.questionType,
            groupId = filter.groupId,
            groupOptions = groups,
        )
    }

    companion object {
        const val KEY_CONTENT = "contrast_errors_content"
        const val KEY_QTYPE = "contrast_errors_qtype"
        const val KEY_GROUP = "contrast_errors_group"
        const val MAX_DIRECTIONS = 30
        const val MAX_GROUPS = 20
        const val MAX_RECENT = 10
    }
}

// ---- Review ----

data class ReviewRow(
    val groupId: String,
    val title: String,
    val type: ContrastContentType,
    val mastery: ContrastMastery,
    val dueAt: Instant,
    /** 1-based step on Contrast's ladder, of [stepCount]. */
    val step: Int,
    val stepCount: Int,
    val intervalDays: Int,
    val toReinforce: Boolean,
)

data class ContrastReviewUiState(
    val due: List<ReviewRow>,
    val upcoming: List<ReviewRow>,
    val toReinforce: List<ReviewRow>,
    val unreviewable: List<String>,
    val nextDueAt: Instant?,
    val newAvailableToday: Int,
    val activeRemaining: Int?,
)

/** Contrast review queue. Opening it writes nothing; "Ôn tập ngay" goes to the M6 practice. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContrastReviewViewModel(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val analytics: ContrastAnalyticsRepository,
    private val clock: Clock,
    private val config: ContrastConfig = ContrastConfig(),
    policy: ContrastAnalyticsPolicy = ContrastAnalyticsPolicy(),
    private val retiredLabel: (String) -> String = { it },
) : ViewModel() {

    private val queue = GetContrastReviewQueue(content, practice, progress, analytics, config, policy)
    private val reload = MutableStateFlow(0)

    val state: StateFlow<UiState<ContrastReviewUiState>> = reload
        .flatMapLatest { combine(content.observeGroups(), analytics.observeChanges()) { groups, _ -> groups } }
        .mapLatest<List<ContrastGroup>, UiState<ContrastReviewUiState>> { groups -> if (groups.isEmpty()) UiState.Empty else UiState.Success(build()) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun retry() {
        reload.value++
    }

    private suspend fun build(): ContrastReviewUiState {
        val now = clock.instant()
        val q: ReviewQueue = queue(now, clock.zone)
        val reinforce = q.toReinforce.toSet()
        val rows = (q.due + q.upcoming).associate { e ->
            e.group.id to ReviewRow(
                e.group.id, e.group.title, e.group.contentType, e.mastery, e.dueAt, e.step + 1, config.ladder.size,
                e.interval.amount, e.group.id in reinforce,
            )
        }
        return ContrastReviewUiState(
            due = q.due.map { rows.getValue(it.group.id) },
            upcoming = q.upcoming.map { rows.getValue(it.group.id) },
            toReinforce = q.toReinforce.mapNotNull(rows::get),
            unreviewable = q.unreviewable.map { retiredLabel(it.groupId) },
            nextDueAt = q.nextDueAt,
            newAvailableToday = q.newAvailableToday,
            activeRemaining = activeRemaining(practice, content, config, now),
        )
    }
}

// ---- Progress ----

data class ProgressGroupRow(val groupId: String, val title: String, val type: ContrastContentType, val mastery: ContrastMastery, val dueAt: Instant?, val isDue: Boolean, val counts: OutcomeCounts, val rate: RateView)

data class ProgressTypeRow(val type: ContrastQuestionType, val rate: RateView)

data class ContrastProgressUiState(
    val report: ContrastProgressReport,
    val rate: RateView,
    val groups: List<ProgressGroupRow>,
    val types: List<ProgressTypeRow>,
)

/** Contrast progress from real data (never the app's Statistics tab). Read-only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContrastProgressViewModel(
    private val content: ContrastContentRepository,
    practice: ContrastPracticeRepository,
    progress: ContrastProgressRepository,
    private val analytics: ContrastAnalyticsRepository,
    private val clock: Clock,
    config: ContrastConfig = ContrastConfig(),
    private val policy: ContrastAnalyticsPolicy = ContrastAnalyticsPolicy(),
) : ViewModel() {

    private val getReport = GetContrastProgressReport(content, practice, progress, analytics, config)
    private val reload = MutableStateFlow(0)

    val state: StateFlow<UiState<ContrastProgressUiState>> = reload
        .flatMapLatest { combine(content.observeGroups(), analytics.observeChanges()) { groups, _ -> groups } }
        .mapLatest<List<ContrastGroup>, UiState<ContrastProgressUiState>> { groups -> if (groups.isEmpty()) UiState.Empty else UiState.Success(build()) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun retry() {
        reload.value++
    }

    private suspend fun build(): ContrastProgressUiState {
        val now = clock.instant()
        val report = getReport(now, clock.zone)
        return ContrastProgressUiState(
            report = report,
            rate = policy.rate(report.totals),
            groups = report.groups.map {
                ProgressGroupRow(it.group.id, it.group.title, it.group.contentType, it.mastery, it.dueAt, it.dueAt?.let { d -> !d.isAfter(now) } ?: false, it.counts, policy.rate(it.counts))
            },
            types = report.byQuestionType.map { ProgressTypeRow(it.type, policy.rate(it.counts)) },
        )
    }
}
