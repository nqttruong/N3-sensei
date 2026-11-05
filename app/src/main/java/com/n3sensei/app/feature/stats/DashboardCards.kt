package com.n3sensei.app.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.BarValue
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.DailyBarChart
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.domain.forecast.Confidence
import com.n3sensei.app.domain.forecast.ForecastHorizon
import com.n3sensei.app.domain.forecast.ProgressEstimate
import com.n3sensei.app.domain.forecast.ProgressEstimator
import com.n3sensei.app.domain.forecast.TrendUnit
import com.n3sensei.app.domain.model.ItemType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import com.n3sensei.app.core.ui.state.DashboardUiState

// Cards that were on Trang chủ before its redesign, moved here unchanged (same data, same
// formulas, from the shared DashboardViewModel): forecast, trend, volume per type, progress
// estimate and recent review sessions.

/** Card with a heading and a "Thực tế" / "Ước tính" tag. */
@Composable
private fun DashboardCard(@StringRes titleRes: Int, estimate: Boolean, content: @Composable () -> Unit) {
    SectionCard(
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
private fun horizonLabel(horizon: ForecastHorizon): String =
    if (horizon == ForecastHorizon.TOMORROW) stringResource(R.string.dashboard_forecast_tomorrow)
    else stringResource(R.string.dashboard_forecast_days, horizon.days)

@Composable
internal fun DashboardForecastCard(ui: DashboardUiState) {
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
internal fun DashboardTrendCard(ui: DashboardUiState, onSelect: (TrendUnit) -> Unit, units: List<TrendUnit> = TrendUnit.entries) {
    DashboardCard(R.string.dashboard_trend, estimate = false) {
        val unitName = mapOf(
            TrendUnit.DAY to stringResource(R.string.dashboard_trend_day),
            TrendUnit.WEEK to stringResource(R.string.dashboard_trend_week),
            TrendUnit.MONTH to stringResource(R.string.dashboard_trend_month),
        )
        FilterChipRow(
            options = units.map { FilterOption(it, unitName.getValue(it)) },
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
internal fun DashboardVolumeCard(ui: DashboardUiState) {
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
internal fun DashboardProgressCard(progress: ProgressEstimate, newPerDay: Int) {
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
internal fun DashboardSessionsCard(ui: DashboardUiState) {
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

