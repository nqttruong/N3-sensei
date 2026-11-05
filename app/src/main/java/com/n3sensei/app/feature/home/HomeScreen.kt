package com.n3sensei.app.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.CardTone
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.core.ui.state.DashboardUiState
import com.n3sensei.app.core.ui.state.DashboardViewModel
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.forecast.GoalPart
import com.n3sensei.app.domain.model.ItemRef
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Trang chủ: the proverb of the day, today's goal and what is due. Every other figure of the
 * former dashboard is in Thống kê (same view model, same formulas).
 */
@Composable
fun HomeScreen(
    viewModel: DashboardViewModel,
    proverbViewModel: ProverbViewModel,
    onOpenReview: () -> Unit,
    onStartSession: () -> Unit,
    onOpenLessons: () -> Unit,
    onOpenItem: (ItemRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val proverb by proverbViewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Independent of the study data: shows even while the dashboard loads or fails.
        ProverbCard(proverb, onNext = proverbViewModel::next, onRetry = proverbViewModel::load)
        when (val s = state) {
            is UiState.Success -> {
                val ui = s.data
                if (!ui.hasContent) {
                    InfoBanner(stringResource(R.string.common_no_content))
                    OutlinedButton(onClick = onOpenLessons) { Text(stringResource(R.string.dashboard_open_lessons)) }
                }
                GoalCard(ui, onStartSession, onOpenReview)
                DueCard(ui, onOpenReview, onOpenItem)
            }
            else -> UiStateContent(state = s, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) {}
        }
    }
}

/**
 * The visual accent of the page: the proverb in large type with furigana, its translation and a
 * short note. Kept compact; "Câu khác" shows another one.
 */
@Composable
private fun ProverbCard(state: UiState<ProverbUiState>, onNext: () -> Unit, onRetry: () -> Unit) {
    N3Card(tone = CardTone.Emphasis, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_proverb_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state is UiState.Success) {
                TextButton(onClick = onNext) { Text(stringResource(R.string.home_proverb_next)) }
            }
        }
        when (state) {
            is UiState.Success -> {
                val p = state.data.proverb
                FuriganaText(p.furigana, style = MaterialTheme.typography.headlineSmall)
                Text(p.translationVi, style = MaterialTheme.typography.titleMedium)
                p.noteVi?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    state.data.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            UiState.Loading -> Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
            is UiState.Error -> {
                Text(stringResource(R.string.home_proverb_error), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            }
            UiState.Empty, UiState.NotFound -> Text(stringResource(R.string.home_proverb_empty), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Card with a heading and a "Thực tế" / "Ước tính" tag. */
@Composable
private fun DashboardCard(@StringRes titleRes: Int, estimate: Boolean, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    SectionCard(
        onClick = onClick,
        title = stringResource(titleRes),
        tag = {
            Tag(
                stringResource(if (estimate) R.string.badge_estimate else R.string.badge_real),
                tone = if (estimate) TagTone.Info else TagTone.Neutral,
            )
        },
    ) { content() }
}

@Composable
private fun Note(text: String) = Caption(text)

@Composable
private fun GoalBar(label: String, part: GoalPart) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        N3ProgressBar(part.fraction)
    }
}

@Composable
private fun GoalCard(ui: DashboardUiState, onStartSession: () -> Unit, onOpenReview: () -> Unit) {
    DashboardCard(R.string.dashboard_goal, estimate = false) {
        GoalBar(stringResource(R.string.dashboard_goal_new, ui.goal.newItems.done, ui.goal.newItems.target), ui.goal.newItems)
        GoalBar(stringResource(R.string.dashboard_goal_reviews, ui.goal.reviews.done, ui.goal.reviews.target), ui.goal.reviews)
        if (ui.goal.reached) Text(stringResource(R.string.dashboard_goal_reached), color = MaterialTheme.colorScheme.tertiary)
        // One tap from the home screen into the session when there is something to study.
        val size = ui.goal.sessionSize
        if (size > 0) {
            Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_start, size)) }
        } else {
            OutlinedButton(onClick = onOpenReview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_start_review)) }
        }
        Note(stringResource(R.string.dashboard_goal_rule))
    }
}

private val dueFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")

@Composable
private fun DueCard(ui: DashboardUiState, onOpenReview: () -> Unit, onOpenItem: (ItemRef) -> Unit) {
    DashboardCard(R.string.dashboard_due, estimate = false, onClick = onOpenReview) {
        Row(Modifier.fillMaxWidth()) {
            StatItem(ui.due.due.toString(), stringResource(R.string.stats_due), Modifier.weight(1f))
            StatItem(ui.due.overdue.toString(), stringResource(R.string.stats_overdue), Modifier.weight(1f))
        }
        Text(stringResource(R.string.dashboard_priority), style = MaterialTheme.typography.titleSmall)
        if (ui.priority.isEmpty()) Note(stringResource(R.string.dashboard_priority_empty))
        val zone = ZoneId.systemDefault()
        ui.priority.forEach { item ->
            ListItem(
                headlineContent = { Text(japaneseText(item.label), style = MaterialTheme.typography.titleMedium.japanese()) },
                supportingContent = {
                    val due = stringResource(R.string.dashboard_priority_due, item.dueAt.atZone(zone).format(dueFormat))
                    Text(if (item.lapses > 0) "$due · ${stringResource(R.string.dashboard_priority_lapses, item.lapses)}" else due)
                },
                modifier = Modifier.clickable(role = Role.Button) { onOpenItem(item.ref) },
            )
        }
    }
}

