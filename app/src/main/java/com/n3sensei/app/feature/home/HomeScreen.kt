package com.n3sensei.app.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.BarValue
import com.n3sensei.app.core.designsystem.component.DailyBarChart
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.forecast.Confidence
import com.n3sensei.app.domain.forecast.ForecastHorizon
import com.n3sensei.app.domain.forecast.GoalPart
import com.n3sensei.app.domain.forecast.ProgressEstimate
import com.n3sensei.app.domain.forecast.ProgressEstimator
import com.n3sensei.app.domain.forecast.TrendUnit
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Dashboard: today's goal, due work, forecasts, trend, progress estimate, sessions. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenReview: () -> Unit,
    onOpenLessons: () -> Unit,
    onOpenItem: (ItemRef) -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state = state, modifier = modifier) { ui ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!ui.hasContent) {
                InfoBanner(stringResource(R.string.common_no_content))
                OutlinedButton(onClick = onOpenLessons) { Text(stringResource(R.string.dashboard_open_lessons)) }
            }
            GoalCard(ui, onOpenReview)
            DueCard(ui, onOpenItem)
            ForecastCard(ui)
            TrendCard(ui, viewModel::selectTrendUnit)
            VolumeCard(ui)
            ProgressCard(ui.progress, ui.assumptions.newPerDay)
            SessionsCard(ui)
            TextButton(onClick = onOpenStats) { Text(stringResource(R.string.dashboard_open_stats)) }
        }
    }
}

/** Card with a heading and a "Thực tế" / "Ước tính" badge. */
@Composable
private fun DashboardCard(@StringRes titleRes: Int, estimate: Boolean, content: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Badge(estimate)
            }
            content()
        }
    }
}

@Composable
private fun Badge(estimate: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (estimate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (estimate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            stringResource(if (estimate) R.string.badge_estimate else R.string.badge_real),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GoalBar(label: String, part: GoalPart) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(
            progress = { part.fraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun GoalCard(ui: HomeUiState, onOpenReview: () -> Unit) {
    DashboardCard(R.string.dashboard_goal, estimate = false) {
        GoalBar(stringResource(R.string.dashboard_goal_new, ui.goal.newItems.done, ui.goal.newItems.target), ui.goal.newItems)
        GoalBar(stringResource(R.string.dashboard_goal_reviews, ui.goal.reviews.done, ui.goal.reviews.target), ui.goal.reviews)
        if (ui.goal.reached) Text(stringResource(R.string.dashboard_goal_reached), color = MaterialTheme.colorScheme.tertiary)
        Button(onClick = onOpenReview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_start_review)) }
        Note(stringResource(R.string.dashboard_goal_rule))
    }
}

private val dueFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")

@Composable
private fun DueCard(ui: HomeUiState, onOpenItem: (ItemRef) -> Unit) {
    DashboardCard(R.string.dashboard_due, estimate = false) {
        Row(Modifier.fillMaxWidth()) {
            StatItem(ui.due.due.toString(), stringResource(R.string.stats_due), Modifier.weight(1f))
            StatItem(ui.due.overdue.toString(), stringResource(R.string.stats_overdue), Modifier.weight(1f))
        }
        Text(stringResource(R.string.dashboard_priority), style = MaterialTheme.typography.titleSmall)
        if (ui.priority.isEmpty()) Note(stringResource(R.string.dashboard_priority_empty))
        val zone = ZoneId.systemDefault()
        ui.priority.forEach { item ->
            ListItem(
                headlineContent = { Text(item.label, style = MaterialTheme.typography.titleMedium.japanese()) },
                supportingContent = {
                    val due = stringResource(R.string.dashboard_priority_due, item.dueAt.atZone(zone).format(dueFormat))
                    Text(if (item.lapses > 0) "$due · ${stringResource(R.string.dashboard_priority_lapses, item.lapses)}" else due)
                },
                modifier = Modifier.clickable(role = Role.Button) { onOpenItem(item.ref) },
            )
        }
    }
}

@Composable
private fun horizonLabel(horizon: ForecastHorizon): String =
    if (horizon == ForecastHorizon.TOMORROW) stringResource(R.string.dashboard_forecast_tomorrow)
    else stringResource(R.string.dashboard_forecast_days, horizon.days)

@Composable
private fun ForecastCard(ui: HomeUiState) {
    DashboardCard(R.string.dashboard_forecast, estimate = true) {
        val header = MaterialTheme.typography.labelMedium
        val columns = Arrangement.spacedBy(8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = columns) {
            Text("", modifier = Modifier.weight(1.2f))
            Text(stringResource(R.string.dashboard_forecast_scheduled), style = header, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.dashboard_forecast_reviews), style = header, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.dashboard_forecast_new), style = header, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        ui.forecasts.forEach { f ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = columns) {
                Text(horizonLabel(f.horizon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
                Text(f.scheduledItems.toString(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("≈ ${f.estimatedReviews}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("≈ ${f.estimatedNewItems}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        Note(stringResource(R.string.dashboard_forecast_rule_scheduled, ui.due.due))
        Note(stringResource(R.string.dashboard_forecast_rule_estimate, ui.assumptions.newPerDay, ui.assumptions.newAvailable))
    }
}

private val dayLabel = DateTimeFormatter.ofPattern("dd/MM")
private val monthLabel = DateTimeFormatter.ofPattern("MM/yyyy")

@Composable
private fun TrendCard(ui: HomeUiState, onSelect: (TrendUnit) -> Unit) {
    DashboardCard(R.string.dashboard_trend, estimate = false) {
        val unitName = mapOf(
            TrendUnit.DAY to stringResource(R.string.dashboard_trend_day),
            TrendUnit.WEEK to stringResource(R.string.dashboard_trend_week),
            TrendUnit.MONTH to stringResource(R.string.dashboard_trend_month),
        )
        FilterChipRow(
            options = TrendUnit.entries.map { FilterOption(it, unitName.getValue(it)) },
            selected = ui.trendUnit,
            onSelect = onSelect,
        )
        val total = ui.trend.sumOf { it.answers }
        val max = ui.trend.maxOfOrNull { it.answers } ?: 0
        if (total == 0) {
            Note(stringResource(R.string.dashboard_trend_empty))
        } else {
            val format = if (ui.trendUnit == TrendUnit.MONTH) monthLabel else dayLabel
            DailyBarChart(
                bars = ui.trend.map { BarValue(it.start.format(format), it.answers, it.newItems) },
                description = stringResource(R.string.dashboard_trend_description, unitName.getValue(ui.trendUnit).lowercase(Locale.ROOT), total, max),
                maxLabel = stringResource(R.string.stats_chart_max, max),
            )
            Note(stringResource(R.string.stats_daily_legend))
        }
    }
}

@Composable
private fun VolumeCard(ui: HomeUiState) {
    DashboardCard(R.string.dashboard_volume, estimate = false) {
        Row(Modifier.fillMaxWidth()) {
            StatItem((ui.answersByType7Days[ItemType.VOCABULARY] ?: 0).toString(), stringResource(R.string.totals_vocabulary), Modifier.weight(1f))
            StatItem((ui.answersByType7Days[ItemType.KANJI] ?: 0).toString(), stringResource(R.string.totals_kanji), Modifier.weight(1f))
            StatItem((ui.answersByType7Days[ItemType.GRAMMAR] ?: 0).toString(), stringResource(R.string.totals_grammar), Modifier.weight(1f))
        }
        Note(stringResource(R.string.dashboard_volume_rule))
    }
}

@Composable
private fun ProgressCard(progress: ProgressEstimate, newPerDay: Int) {
    DashboardCard(R.string.dashboard_progress, estimate = true) {
        when (progress) {
            ProgressEstimate.AllStarted -> Text(stringResource(R.string.dashboard_progress_all_started))
            is ProgressEstimate.NotEnoughData -> {
                Text(
                    stringResource(
                        R.string.dashboard_progress_not_enough,
                        ProgressEstimator.MIN_STUDY_DAYS,
                        ProgressEstimator.MIN_NEW_ITEMS,
                        progress.studyDays,
                        progress.newItems,
                    ),
                )
                SettingsLine(progress.settingsDays, progress.remaining, newPerDay)
            }
            is ProgressEstimate.Estimate -> {
                Text(
                    stringResource(
                        R.string.dashboard_progress_estimate,
                        String.format(Locale.ROOT, "%.1f", progress.itemsPerDay),
                        progress.remaining,
                        progress.expectedDays,
                        progress.fastestDays,
                        progress.slowestDays?.toString() ?: stringResource(R.string.dashboard_progress_unbounded),
                    ),
                )
                Text(
                    stringResource(
                        R.string.dashboard_progress_confidence,
                        stringResource(
                            when (progress.confidence) {
                                Confidence.LOW -> R.string.confidence_low
                                Confidence.MEDIUM -> R.string.confidence_medium
                                Confidence.HIGH -> R.string.confidence_high
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                SettingsLine(progress.settingsDays, progress.remaining, newPerDay)
            }
        }
        Note(stringResource(R.string.dashboard_progress_rule))
    }
}

@Composable
private fun SettingsLine(settingsDays: Int?, remaining: Int, newPerDay: Int) {
    if (settingsDays == null) return
    Note(stringResource(R.string.dashboard_progress_settings, newPerDay, settingsDays, remaining))
}

private val sessionFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")

@Composable
private fun SessionsCard(ui: HomeUiState) {
    DashboardCard(R.string.dashboard_sessions, estimate = false) {
        if (ui.sessions.isEmpty()) Note(stringResource(R.string.dashboard_sessions_empty))
        val zone = ZoneId.systemDefault()
        ui.sessions.forEach { s ->
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            R.string.dashboard_session_line,
                            s.startedAt.atZone(zone).format(sessionFormat),
                            s.answered,
                            s.planned,
                            (s.completion * 100).roundToInt(),
                        ),
                    )
                },
                supportingContent = {
                    Text(stringResource(if (s.finished) R.string.dashboard_session_finished else R.string.dashboard_session_unfinished))
                },
            )
        }
    }
}
