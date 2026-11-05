package com.n3sensei.app.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.BarValue
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.DailyBarChart
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.component.StackSegment
import com.n3sensei.app.core.designsystem.component.StackedBar
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.core.ui.content.StudyColors
import com.n3sensei.app.core.ui.content.labelRes
import com.n3sensei.app.core.ui.state.DashboardViewModel
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.analytics.DayActivity
import com.n3sensei.app.domain.analytics.StatsPeriod
import com.n3sensei.app.domain.analytics.StatusCounts
import com.n3sensei.app.domain.forecast.TrendUnit
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.ReviewRating
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    /** The figures that were on Trang chủ: forecast, trend, volume, progress estimate, sessions. */
    dashboardViewModel: DashboardViewModel,
    onOpenReview: () -> Unit,
    onOpenList: (ItemType, LearningStatus?) -> Unit,
    onOpenItem: (ItemRef) -> Unit,
    modifier: Modifier = Modifier,
    onOpenJlpt: () -> Unit = {},
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard by dashboardViewModel.state.collectAsStateWithLifecycle()
    // Day by day is the daily chart below: the trend here starts by week.
    LaunchedEffect(Unit) { if (dashboardViewModel.trendUnit.value == TrendUnit.DAY) dashboardViewModel.selectTrendUnit(TrendUnit.WEEK) }
    Column(modifier = modifier.fillMaxSize()) {
        FilterChipRow(
            options = listOf(
                FilterOption(StatsPeriod.TODAY, stringResource(R.string.stats_period_today)),
                FilterOption(StatsPeriod.LAST_7_DAYS, stringResource(R.string.stats_period_7)),
                FilterOption(StatsPeriod.LAST_30_DAYS, stringResource(R.string.stats_period_30)),
                FilterOption(StatsPeriod.ALL_TIME, stringResource(R.string.stats_period_all)),
            ),
            selected = period,
            onSelect = viewModel::selectPeriod,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        UiStateContent(state = state) { ui ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(
                    stringResource(R.string.jlpt_dashboard_entry),
                    onClick = onOpenJlpt,
                ) { Caption(stringResource(R.string.jlpt_dashboard_entry_description)) }
                if (!ui.hasContent) InfoBanner(stringResource(R.string.common_no_content))
                ActivityCard(ui)
                DueCard(ui, onOpenReview)
                RecallCard(ui)
                DailyCard(ui.daily)
                (dashboard as? UiState.Success)?.data?.let { d -> DashboardTrendCard(d, dashboardViewModel::selectTrendUnit, units = listOf(TrendUnit.WEEK, TrendUnit.MONTH)) }
                ForecastCard(ui.forecast)
                (dashboard as? UiState.Success)?.data?.let { d -> DashboardForecastCard(d) }
                StreakCard(ui)
                ProgressCard(ui, onOpenList)
                TroubleCard(ui, onOpenItem)
                (dashboard as? UiState.Success)?.data?.let { d ->
                    DashboardVolumeCard(d)
                    DashboardProgressCard(d.progress, d.assumptions.newPerDay)
                    DashboardSessionsCard(d)
                }
                Text(
                    stringResource(R.string.stats_excluded_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(@StringRes titleRes: Int, title: String = stringResource(titleRes), content: @Composable () -> Unit) {
    SectionCard(title) { content() }
}

@Composable
private fun Note(text: String) = Caption(text)

@Composable
private fun ActivityCard(ui: StatsUiState) {
    val a = ui.activity
    StatsCard(R.string.stats_activity) {
        Row(Modifier.fillMaxWidth()) {
            StatItem(a.distinctItems.toString(), stringResource(R.string.stats_items_studied), Modifier.weight(1f))
            StatItem(a.newItems.toString(), stringResource(R.string.stats_new_items), Modifier.weight(1f))
            StatItem(a.reviewAnswers.toString(), stringResource(R.string.stats_review_answers), Modifier.weight(1f))
            StatItem(a.distinctReviewedItems.toString(), stringResource(R.string.stats_items_reviewed), Modifier.weight(1f))
        }
        if (a.answers == 0) {
            Text(stringResource(R.string.stats_no_activity), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.stats_total_answers, a.answers), style = MaterialTheme.typography.bodyMedium)
        }
        Note(stringResource(R.string.stats_activity_rule))
    }
}

@Composable
private fun DueCard(ui: StatsUiState, onOpenReview: () -> Unit) {
    StatsCard(R.string.stats_due_now) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatItem(ui.due.toString(), stringResource(R.string.stats_due), Modifier.weight(1f))
            StatItem(ui.overdue.toString(), stringResource(R.string.stats_overdue), Modifier.weight(1f))
            FilledTonalButton(onClick = onOpenReview, enabled = ui.due > 0) { Text(stringResource(R.string.stats_open_review)) }
        }
    }
}

@Composable
private fun RecallCard(ui: StatsUiState) {
    val ratings = ui.activity.reviewRatings
    val total = ratings.values.sum()
    StatsCard(R.string.stats_recall) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                ui.recallRate?.let { stringResource(R.string.stats_recall_value, (it * 100).roundToInt()) }
                    ?: stringResource(R.string.stats_recall_none),
                style = MaterialTheme.typography.displaySmall,
            )
            if (total > 0) {
                Text(
                    stringResource(R.string.stats_recall_detail, total - (ratings[ReviewRating.AGAIN] ?: 0), total),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val line = stringResource(
            R.string.stats_rating_line,
            ratings[ReviewRating.AGAIN] ?: 0,
            ratings[ReviewRating.HARD] ?: 0,
            ratings[ReviewRating.GOOD] ?: 0,
            ratings[ReviewRating.EASY] ?: 0,
        )
        StackedBar(
            segments = ReviewRating.entries.map { StackSegment(ratings[it] ?: 0, StudyColors.ratingAccent(it)) },
            description = line,
        )
        Text(line, style = MaterialTheme.typography.bodySmall)
        Note(stringResource(R.string.stats_recall_rule))
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("dd/MM")

@Composable
private fun DailyCard(daily: List<DayActivity>) {
    StatsCard(R.string.stats_daily, stringResource(R.string.stats_daily, daily.size)) {
        val total = daily.sumOf { it.answers }
        val max = daily.maxOfOrNull { it.answers } ?: 0
        if (total == 0) {
            Text(stringResource(R.string.stats_chart_empty, daily.size), style = MaterialTheme.typography.bodyMedium)
        } else {
            DailyBarChart(
                bars = daily.map { BarValue(it.date.format(dayFormat), it.answers, it.newItems) },
                description = stringResource(R.string.stats_daily_description, daily.size, total, max),
                maxLabel = stringResource(R.string.stats_chart_max, max),
            )
            Note(stringResource(R.string.stats_daily_legend))
        }
    }
}

@Composable
private fun ForecastCard(forecast: List<DayActivity>) {
    StatsCard(R.string.stats_forecast) {
        val total = forecast.sumOf { it.answers }
        val max = forecast.maxOfOrNull { it.answers } ?: 0
        if (total == 0) {
            Text(stringResource(R.string.stats_forecast_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            DailyBarChart(
                bars = forecast.map { BarValue(it.date.format(dayFormat), it.answers) },
                description = stringResource(R.string.stats_forecast_description, total, max),
                maxLabel = stringResource(R.string.stats_chart_max, max),
                barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        }
        Note(stringResource(R.string.stats_forecast_rule))
    }
}

@Composable
private fun StreakCard(ui: StatsUiState) {
    val streak = ui.streak
    StatsCard(R.string.stats_streak) {
        Text(stringResource(R.string.stats_streak_current, streak.current), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.stats_streak_longest, streak.longest), style = MaterialTheme.typography.bodyMedium)
        if (!streak.studiedToday && streak.current > 0) Note(stringResource(R.string.stats_streak_today_pending))
        Note(stringResource(R.string.stats_streak_rule))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressCard(ui: StatsUiState, onOpenList: (ItemType, LearningStatus?) -> Unit) {
    val statusColors: List<Pair<LearningStatus, Color>> = LearningStatus.entries.map { it to StudyColors.status(it) }
    StatsCard(R.string.stats_progress) {
        listOf(
            ItemType.VOCABULARY to R.string.totals_vocabulary,
            ItemType.KANJI to R.string.totals_kanji,
            ItemType.GRAMMAR to R.string.totals_grammar,
        ).forEach { (type, nameRes) ->
            val counts = ui.statusByType[type] ?: StatusCounts.EMPTY
            val name = stringResource(nameRes)
            Text(
                stringResource(R.string.stats_type_total, name, counts.total),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.clickable(role = Role.Button) { onOpenList(type, null) }.padding(vertical = 4.dp),
            )
            StackedBar(
                segments = statusColors.map { (status, color) -> StackSegment(counts.of(status), color) },
                description = stringResource(
                    R.string.stats_progress_description, name, counts.new, counts.learning, counts.review, counts.mastered,
                ),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                statusColors.forEach { (status, color) ->
                    AssistChip(
                        onClick = { onOpenList(type, status) },
                        label = { Text(stringResource(R.string.stats_status_count, stringResource(status.labelRes()), counts.of(status))) },
                        // Legend: the dot has the colour of this status in the bar above.
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) },
                    )
                }
            }
            HorizontalDivider()
        }
        Note(stringResource(R.string.stats_progress_rule))
    }
}

private fun StatusCounts.of(status: LearningStatus): Int = when (status) {
    LearningStatus.NEW -> new
    LearningStatus.LEARNING -> learning
    LearningStatus.REVIEW -> review
    LearningStatus.MASTERED -> mastered
}

@Composable
private fun TroubleCard(ui: StatsUiState, onOpenItem: (ItemRef) -> Unit) {
    StatsCard(R.string.stats_trouble) {
        if (ui.troubleItems.isEmpty()) {
            Text(stringResource(R.string.stats_trouble_empty), style = MaterialTheme.typography.bodyMedium)
        }
        ui.troubleItems.forEach { item ->
            ListItem(
                headlineContent = { Text(japaneseText(item.label), style = MaterialTheme.typography.titleMedium.japanese()) },
                supportingContent = { Text(stringResource(R.string.stats_trouble_line, item.again, item.hard)) },
                modifier = Modifier.clickable(role = Role.Button) { onOpenItem(item.ref) },
            )
        }
        Note(stringResource(R.string.stats_trouble_rule))
    }
}
