package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SearchField
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.mistakes.ErrorState
import com.n3sensei.app.domain.mistakes.NotebookError
import com.n3sensei.app.domain.mistakes.NotebookOverview
import com.n3sensei.app.domain.mistakes.NotebookStateFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ErrorNotebookTags {
    const val LEVELS = "errors_filter_levels"
    const val SKILLS = "errors_filter_skills"
    const val STATES = "errors_filter_states"
    fun row(id: Long) = "error_row_$id"
}

@Composable
fun ErrorNotebookScreen(
    viewModel: ErrorNotebookViewModel,
    onOpenError: (Long) -> Unit,
    onOpenWeakness: () -> Unit,
    onReviewDue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, emptyMessage = stringResource(R.string.mistakes_empty)) { data ->
        ErrorNotebookContent(
            data.copy(filter = data.filter.copy(query = query)), viewModel::search, viewModel::selectLevel, viewModel::selectSkill, viewModel::selectState, onOpenError, onOpenWeakness,
            onReviewDue = onReviewDue,
        )
    }
}

@Composable
fun ErrorNotebookContent(
    state: ErrorNotebookUiState,
    onSearch: (String) -> Unit,
    onSelectLevel: (String?) -> Unit,
    onSelectSkill: (ExamSkill?) -> Unit,
    onSelectState: (NotebookStateFilter) -> Unit,
    onOpenError: (Long) -> Unit,
    onOpenWeakness: () -> Unit,
    modifier: Modifier = Modifier,
    onReviewDue: () -> Unit = {},
) {
    val all = stringResource(R.string.common_all)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = N3Spacing.l),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.s),
    ) {
        item { OverviewCard(state.overview, Modifier.padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s)) }
        if (state.dueNow > 0) {
            item {
                Button(onClick = onReviewDue, modifier = Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen)) {
                    Text(stringResource(R.string.errors_review_due, state.dueNow))
                }
            }
        }
        item {
            SearchField(
                query = state.filter.query,
                onQueryChange = onSearch,
                placeholder = stringResource(R.string.errors_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen),
            )
        }
        item {
            FilterChipRow(
                options = listOf(FilterOption<String?>(null, all)) + ExamListViewModel.LEVELS.map { FilterOption(it, it) },
                selected = state.filter.level,
                onSelect = onSelectLevel,
                modifier = Modifier.testTag(ErrorNotebookTags.LEVELS),
            )
        }
        item {
            FilterChipRow(
                options = listOf(FilterOption<ExamSkill?>(null, all)) + ExamSkill.entries.map { FilterOption(it, stringResource(it.labelRes())) },
                selected = state.filter.skill,
                onSelect = onSelectSkill,
                modifier = Modifier.testTag(ErrorNotebookTags.SKILLS),
            )
        }
        item {
            FilterChipRow(
                options = NotebookStateFilter.entries.map { FilterOption(it, stateFilterLabel(it, all)) },
                selected = state.filter.state,
                onSelect = onSelectState,
                modifier = Modifier.testTag(ErrorNotebookTags.STATES),
            )
        }
        item {
            Row(Modifier.padding(horizontal = N3Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                Caption(stringResource(R.string.errors_count_shown, state.errors.size), Modifier.weight(1f))
                TextButton(onClick = onOpenWeakness) { Text(stringResource(R.string.errors_open_weakness)) }
            }
        }
        if (state.errors.isEmpty()) {
            item { MessageContent(stringResource(R.string.errors_none_match)) }
        }
        items(state.errors, key = { it.id }) { error ->
            ErrorRow(error, onClick = { onOpenError(error.id) }, Modifier.padding(horizontal = N3Spacing.screen))
        }
    }
}

@Composable
private fun stateFilterLabel(filter: NotebookStateFilter, all: String): String = when (filter) {
    NotebookStateFilter.ALL -> all
    NotebookStateFilter.NEW -> stringResource(R.string.errors_filter_new)
    NotebookStateFilter.RECURRING -> stringResource(R.string.errors_filter_recurring)
    NotebookStateFilter.REVIEWING -> stringResource(R.string.errors_filter_reviewing)
    NotebookStateFilter.MASTERED -> stringResource(R.string.errors_filter_mastered)
    NotebookStateFilter.IMPORTANT -> stringResource(R.string.errors_filter_important)
}

/** Five counts in one row; the numbers carry the card, no colours. */
@Composable
private fun OverviewCard(overview: NotebookOverview, modifier: Modifier = Modifier) {
    N3Card(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Count(overview.total, R.string.errors_overview_total)
            Count(overview.open, R.string.errors_overview_open)
            Count(overview.reviewing, R.string.errors_overview_reviewing)
            Count(overview.mastered, R.string.errors_overview_mastered)
            Count(overview.recurring, R.string.errors_overview_recurring)
        }
    }
}

@Composable
private fun Count(value: Int, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text("$value", style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@StringRes
internal fun ErrorState.labelRes(): Int = when (this) {
    ErrorState.NEW -> R.string.error_state_new
    ErrorState.REVIEWING -> R.string.error_state_reviewing
    ErrorState.MASTERED -> R.string.error_state_mastered
    ErrorState.ARCHIVED -> R.string.error_state_archived
}

internal val ERROR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())

internal fun formatDate(millis: Long): String = ERROR_DATE.format(Instant.ofEpochMilli(millis))

/**
 * One error: priority ones carry an accent "Ưu tiên" tag (the only colour), fixed ones are
 * dimmed; the state is always written, so colour is never the only cue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorRow(error: NotebookError, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val q = error.record.question
    val done = !error.isOpen
    N3Card(
        modifier = modifier.testTag(ErrorNotebookTags.row(error.id)).alpha(if (done) 0.7f else 1f),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                q?.content ?: error.record.questionId,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (error.isImportant) {
                Icon(
                    N3Icons.Flagged,
                    contentDescription = stringResource(R.string.error_important),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
            if (error.isPriority) Tag(stringResource(R.string.error_priority), tone = TagTone.Accent)
            Tag(stringResource(error.state.labelRes()))
            error.skill?.let { Tag(stringResource(it.labelRes())) }
            error.jlptLevel?.let { Tag(it) }
        }
        Caption(stringResource(R.string.error_row_meta, error.record.errorCount, formatDate(error.record.lastWrongAt)))
        if (error.isOpen) error.review?.let { Caption(stringResource(R.string.error_next_review, formatDate(it.nextReviewAt))) }
    }
}
