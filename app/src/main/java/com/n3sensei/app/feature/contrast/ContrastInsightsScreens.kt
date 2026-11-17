package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.contrast.Accuracy
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.DirectionStatus
import com.n3sensei.app.domain.repository.OutcomeCounts
import kotlin.math.roundToInt

object InsightTags {
    const val ERRORS = "contrast_errors"
    const val LIST = "contrast_insights_list"
    const val REVIEW = "contrast_review"
    const val PROGRESS = "contrast_progress"
    const val OPEN_ERRORS = "contrast_open_errors"
    const val OPEN_REVIEW = "contrast_open_review"
    const val OPEN_PROGRESS = "contrast_open_progress"
    const val CONTENT_FILTER = "contrast_errors_filter_content"
    const val QTYPE_FILTER = "contrast_errors_filter_qtype"
    const val GROUP_FILTER = "contrast_errors_filter_group"
    const val START_REVIEW = "contrast_review_start"
    const val RESUME_REVIEW = "contrast_review_resume"
    const val PROGRESS_REVIEW = "contrast_progress_review"
    fun direction(groupId: String, expected: String, chosen: String) = "contrast_direction_${groupId}_${expected}_$chosen"
    fun reviewRow(groupId: String) = "contrast_review_row_$groupId"
    fun progressRow(groupId: String) = "contrast_progress_row_$groupId"
}

// ---- Shared pieces ----

@Composable
fun rateText(rate: RateView): String {
    val a = rate.accuracy
    return when {
        a.answered == 0 -> stringResource(R.string.contrast_rate_none)
        !rate.reliable -> stringResource(R.string.contrast_rate_not_enough, a.answered, rate.minAnswered)
        else -> stringResource(R.string.contrast_rate_value, percent(a), a.correct, a.answered)
    }
}

@Composable
private fun shortRate(rate: RateView): String {
    val a = rate.accuracy
    return if (a.answered == 0 || !rate.reliable) stringResource(R.string.contrast_rate_short_not_enough, a.answered) else stringResource(R.string.contrast_rate_short, percent(a), a.correct, a.answered)
}

@Composable
private fun shortRate(a: Accuracy?): String =
    if (a == null) stringResource(R.string.contrast_rate_short_none) else stringResource(R.string.contrast_rate_short, percent(a), a.correct, a.answered)

private fun percent(a: Accuracy) = ((a.value ?: 0.0) * 100).roundToInt()

@Composable
fun questionTypeName(type: ContrastQuestionType) = stringResource(
    when (type) {
        ContrastQuestionType.MULTIPLE_CHOICE -> R.string.contrast_qtype_mc
        ContrastQuestionType.ERROR_DETECTION -> R.string.contrast_qtype_ed
        ContrastQuestionType.FILL_IN_THE_BLANK -> R.string.contrast_qtype_fill
        ContrastQuestionType.CONTEXT_SELECTION -> R.string.contrast_qtype_context
    },
)

@Composable
private fun Stat(label: String, value: Int) {
    N3Card(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnswerCounts(counts: OutcomeCounts) {
    Text(stringResource(R.string.contrast_answers_counts, counts.total, counts.correct, counts.incorrect, counts.skipped), style = MaterialTheme.typography.bodyLarge)
}

private val listPadding = PaddingValues(horizontal = N3Spacing.screen, vertical = N3Spacing.s)

// ---- Errors ----

@Composable
fun ContrastErrorsScreen(viewModel: ContrastErrorsViewModel, onOpenGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastErrorsContent(state, onOpenGroup, viewModel::selectContentType, viewModel::selectQuestionType, viewModel::selectGroup, viewModel::retry, modifier)
}

@Composable
fun ContrastErrorsContent(
    state: UiState<ContrastErrorsUiState>,
    onOpenGroup: (String) -> Unit,
    onSelectContentType: (ContrastContentType?) -> Unit,
    onSelectQuestionType: (ContrastQuestionType?) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier.testTag(InsightTags.ERRORS), onRetry = onRetry) { data ->
        val all = stringResource(R.string.common_all)
        LazyColumn(Modifier.fillMaxSize().testTag(InsightTags.LIST), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(N3Spacing.m)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                    val contentDescription = stringResource(R.string.contrast_filter_type)
                    FilterChipRow(
                        options = listOf(FilterOption<ContrastContentType?>(null, all)) + ContrastContentType.entries.map { FilterOption(it, typeLabel(it)) },
                        selected = data.contentType,
                        onSelect = onSelectContentType,
                        modifier = Modifier.testTag(InsightTags.CONTENT_FILTER).semantics { this.contentDescription = contentDescription },
                    )
                    val qDescription = stringResource(R.string.contrast_filter_qtype)
                    FilterChipRow(
                        options = listOf(FilterOption<ContrastQuestionType?>(null, all)) + ContrastQuestionType.entries.map { FilterOption(it, questionTypeName(it)) },
                        selected = data.questionType,
                        onSelect = onSelectQuestionType,
                        modifier = Modifier.testTag(InsightTags.QTYPE_FILTER).semantics { this.contentDescription = qDescription },
                    )
                    val gDescription = stringResource(R.string.contrast_filter_group)
                    FilterChipRow(
                        options = listOf(FilterOption<String?>(null, stringResource(R.string.contrast_filter_all_groups))) + data.groupOptions.map { FilterOption(it.id, it.title) },
                        selected = data.groupId,
                        onSelect = onSelectGroup,
                        modifier = Modifier.testTag(InsightTags.GROUP_FILTER).semantics { this.contentDescription = gDescription },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    Text(stringResource(R.string.contrast_errors_total, data.totals.incorrect), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    AnswerCounts(data.totals)
                    Text(rateText(data.rate), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (data.totals.incorrect == 0) {
                item { Text(stringResource(R.string.contrast_errors_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                if (data.directions.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.contrast_errors_directions)) }
                    items(data.directions, key = { "d_${it.groupId}_${it.expectedLabel}_${it.chosenLabel}" }) { row -> DirectionCard(row, onOpenGroup) }
                }
                if (data.otherIncorrect > 0) {
                    item { Text(stringResource(R.string.contrast_errors_other, data.otherIncorrect), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (data.groups.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.contrast_errors_groups)) }
                    items(data.groups, key = { "g_${it.groupId}" }) { row ->
                        N3Card(onClick = if (row.published) ({ onOpenGroup(row.groupId) }) else null) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.contrast_errors_group_row, row.incorrect, shortRate(row.rate)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (data.types.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                            SectionHeader(stringResource(R.string.contrast_errors_types))
                            data.types.forEach { t ->
                                Text(stringResource(R.string.contrast_errors_type_row, questionTypeName(t.type), t.incorrect, shortRate(t.rate)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (data.recent.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.contrast_errors_recent)) }
                    items(data.recent, key = { "r_${it.snapshot.questionId}_${it.snapshot.answeredAt}" }) { r ->
                        N3Card {
                            Text(r.groupTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.contrast_errors_recent_row, questionTypeName(r.snapshot.questionType), r.snapshot.chosenText.orEmpty(), r.snapshot.correctText),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(formatDate(r.snapshot.answeredAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (data.sessions.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                        SectionHeader(stringResource(R.string.contrast_errors_trend))
                        Text(stringResource(R.string.contrast_errors_trend_compare, data.recentSessionCount, shortRate(data.recentRate), shortRate(data.earlierRate)), style = MaterialTheme.typography.bodyMedium)
                        data.sessions.forEach { s ->
                            Text(stringResource(R.string.contrast_errors_session_row, formatDate(s.firstAnsweredAt), s.counts.correct, s.counts.incorrect, s.counts.skipped), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionCard(row: DirectionRow, onOpenGroup: (String) -> Unit) {
    N3Card(modifier = Modifier.testTag(InsightTags.direction(row.groupId, row.expectedLabel, row.chosenLabel))) {
        Text(row.groupTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.contrast_errors_direction, row.expectedLabel, row.chosenLabel), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.contrast_errors_direction_count, row.count, row.sessions), style = MaterialTheme.typography.bodyMedium)
        when (row.status) {
            DirectionStatus.TO_REINFORCE -> Tag(stringResource(R.string.contrast_errors_to_reinforce), tone = TagTone.Accent)
            DirectionStatus.SETTLING -> Tag(stringResource(R.string.contrast_errors_settling, row.sessionsSince))
            DirectionStatus.SINGLE -> Unit
        }
        row.example?.let { e ->
            Text(stringResource(R.string.contrast_errors_example, e.chosenText.orEmpty(), e.correctText), style = MaterialTheme.typography.bodyMedium)
            if (row.exampleOutdated) Text(stringResource(R.string.contrast_errors_example_old), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.published) {
            OutlinedButton(onClick = { onOpenGroup(row.groupId) }) { Text(stringResource(R.string.contrast_errors_open_group)) }
        }
    }
}

// ---- Review ----

@Composable
fun ContrastReviewScreen(viewModel: ContrastReviewViewModel, onStartReview: () -> Unit, onResume: () -> Unit, onOpenGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastReviewContent(state, onStartReview, onResume, onOpenGroup, viewModel::retry, modifier)
}

@Composable
fun ContrastReviewContent(
    state: UiState<ContrastReviewUiState>,
    onStartReview: () -> Unit,
    onResume: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier.testTag(InsightTags.REVIEW), emptyMessage = stringResource(R.string.contrast_empty), onRetry = onRetry) { data ->
        LazyColumn(Modifier.fillMaxSize().testTag(InsightTags.LIST), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(N3Spacing.m)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                    Text(stringResource(R.string.contrast_review_due, data.due.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.contrast_review_upcoming_count, data.upcoming.size), style = MaterialTheme.typography.bodyLarge)
                    data.nextDueAt?.let { Text(stringResource(R.string.contrast_review_next, formatDate(it)), style = MaterialTheme.typography.bodyMedium) }
                    data.activeRemaining?.let { remaining ->
                        Button(onClick = onResume, modifier = Modifier.fillMaxWidth().testTag(InsightTags.RESUME_REVIEW)) { Text(stringResource(R.string.contrast_continue_session, remaining)) }
                    }
                    if (data.due.isNotEmpty()) {
                        Button(onClick = onStartReview, modifier = Modifier.fillMaxWidth().testTag(InsightTags.START_REVIEW)) { Text(stringResource(R.string.contrast_review_start)) }
                    } else {
                        Text(
                            stringResource(if (data.upcoming.isEmpty()) R.string.contrast_review_nothing_scheduled else R.string.contrast_review_none_due),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.contrast_review_new_note, data.newAvailableToday), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (data.due.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.contrast_review_due_section)) }
                items(data.due, key = { "due_${it.groupId}" }) { ReviewCard(it, onOpenGroup) }
            }
            if (data.toReinforce.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.contrast_review_reinforce_section)) }
                items(data.toReinforce, key = { "re_${it.groupId}" }) { ReviewCard(it, onOpenGroup) }
            }
            if (data.upcoming.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.contrast_review_upcoming_section)) }
                items(data.upcoming, key = { "up_${it.groupId}" }) { ReviewCard(it, onOpenGroup) }
            }
            if (data.unreviewable.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.contrast_review_unavailable_section)) }
                items(data.unreviewable, key = { "un_$it" }) { Text(stringResource(R.string.contrast_review_unavailable_row, it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(row: ReviewRow, onOpenGroup: (String) -> Unit) {
    N3Card(onClick = { onOpenGroup(row.groupId) }, modifier = Modifier.testTag(InsightTags.reviewRow(row.groupId))) {
        Text(row.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
            Tag(typeLabel(row.type))
            Tag(masteryLabel(row.mastery))
            if (row.toReinforce) Tag(stringResource(R.string.contrast_errors_to_reinforce), tone = TagTone.Accent)
        }
        Text(stringResource(R.string.contrast_review_due_at, formatDate(row.dueAt)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.contrast_review_step, row.step, row.stepCount, row.intervalDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Progress ----

@Composable
fun ContrastProgressScreen(viewModel: ContrastProgressViewModel, onOpenReview: () -> Unit, onOpenGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastProgressContent(state, onOpenReview, onOpenGroup, viewModel::retry, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContrastProgressContent(
    state: UiState<ContrastProgressUiState>,
    onOpenReview: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier.testTag(InsightTags.PROGRESS), emptyMessage = stringResource(R.string.contrast_empty), onRetry = onRetry) { data ->
        val r = data.report
        LazyColumn(Modifier.fillMaxSize().testTag(InsightTags.LIST), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(N3Spacing.m)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                    SectionHeader(stringResource(R.string.contrast_progress_overview))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                        Stat(stringResource(R.string.contrast_progress_published), r.published)
                        Stat(stringResource(R.string.contrast_progress_not_started), r.notStarted)
                        Stat(stringResource(R.string.contrast_progress_learning), r.learning)
                        Stat(stringResource(R.string.contrast_progress_due), r.due)
                        Stat(stringResource(R.string.contrast_progress_mastered), r.mastered)
                    }
                    Text(stringResource(R.string.contrast_progress_due_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.contrast_progress_mastery_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    SectionHeader(stringResource(R.string.contrast_progress_answers))
                    AnswerCounts(r.totals)
                    Text(rateText(data.rate), style = MaterialTheme.typography.bodyLarge)
                    if (r.historical.total > 0) Text(stringResource(R.string.contrast_progress_historical, r.historical.total), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                    SectionHeader(stringResource(R.string.contrast_progress_categories))
                    r.categories.forEach { c ->
                        N3Card {
                            Text(typeLabel(c.type), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.contrast_progress_category_row, c.started, c.total, c.mastered, c.due), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (data.types.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                        SectionHeader(stringResource(R.string.contrast_progress_by_type))
                        data.types.forEach { t -> Text("${questionTypeName(t.type)}: ${shortRate(t.rate)}", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    r.nextDueAt?.let { Text(stringResource(R.string.contrast_review_next, formatDate(it)), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = onOpenReview, modifier = Modifier.fillMaxWidth().testTag(InsightTags.PROGRESS_REVIEW)) { Text(stringResource(R.string.contrast_progress_open_review)) }
                }
            }
            item { SectionHeader(stringResource(R.string.contrast_progress_groups)) }
            items(data.groups, key = { "p_${it.groupId}" }) { g ->
                N3Card(onClick = { onOpenGroup(g.groupId) }, modifier = Modifier.testTag(InsightTags.progressRow(g.groupId))) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                        Tag(typeLabel(g.type))
                        Tag(masteryLabel(g.mastery))
                        if (g.isDue) Tag(stringResource(R.string.contrast_progress_due), tone = TagTone.Accent)
                    }
                    Text(if (g.counts.total == 0) stringResource(R.string.contrast_progress_group_none) else shortRate(g.rate), style = MaterialTheme.typography.bodyMedium)
                    g.dueAt?.let { Text(stringResource(R.string.contrast_review_due_at, formatDate(it)), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
