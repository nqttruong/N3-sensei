package com.n3sensei.app.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.BarValue
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.DailyBarChart
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.dashboard.ExamStatistics
import com.n3sensei.app.domain.dashboard.SkillDetail
import com.n3sensei.app.domain.dashboard.Tally
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.model.ItemRef
import java.time.format.DateTimeFormatter

@Composable
fun SkillDetailScreen(
    viewModel: SkillDetailViewModel,
    onOpenItem: (ItemRef) -> Unit,
    onOpenErrors: (ExamSkill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, onRetry = viewModel::load) { SkillDetailContent(it, onOpenItem, onOpenErrors) }
}

@Composable
fun SkillDetailContent(detail: SkillDetail, onOpenItem: (ItemRef) -> Unit, onOpenErrors: (ExamSkill) -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(detail.skill.labelRes())
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.l),
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        val stats = detail.stats
        if (stats == null) {
            Text(stringResource(R.string.skill_no_data, name.lowercase()))
        } else {
            Text(
                if (stats.overall.reliable) {
                    stringResource(R.string.jlpt_skill_overall, stats.overall.accuracy.percent(), stats.overall.correct, stats.overall.total)
                } else {
                    stringResource(R.string.jlpt_skill_overall_few, stats.overall.total, ExamStatistics.MIN_QUESTIONS)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Caption(stringResource(R.string.jlpt_skill_compare_week, percentOrFew(stats.thisWeek), percentOrFew(stats.lastWeek)))
            Caption(stringResource(R.string.jlpt_skill_compare_month, percentOrFew(stats.thisMonth), percentOrFew(stats.lastMonth)))

            SectionCard(stringResource(R.string.skill_weekly)) {
                val format = DateTimeFormatter.ofPattern("dd/MM")
                val max = detail.weekly.maxOfOrNull { it.tally.total } ?: 0
                DailyBarChart(
                    bars = detail.weekly.map { BarValue(format.format(it.weekStart), it.tally.total, it.tally.correct) },
                    description = stringResource(R.string.skill_weekly_chart, name.lowercase()),
                    maxLabel = stringResource(R.string.skill_weekly_max, max),
                )
            }

            if (detail.byType.isNotEmpty()) {
                SectionCard(stringResource(R.string.skill_by_type)) {
                    detail.byType.forEach { t ->
                        Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xxs)) {
                            Text(
                                stringResource(R.string.skill_type_row, stringResource(t.type.labelRes()), t.tally.correct, t.tally.total),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            N3ProgressBar(t.tally.accuracy.toFloat())
                        }
                    }
                }
            }

            if (detail.weakItems.isNotEmpty()) {
                SectionCard(stringResource(R.string.skill_weak_items)) {
                    detail.weakItems.forEach { (stat, knowledge) ->
                        val text = stringResource(R.string.mistakes_knowledge_row, knowledge.title(), stat.missed, stat.seen)
                        if (knowledge.item != null) {
                            TextButton(onClick = { onOpenItem(stat.ref) }, contentPadding = PaddingValues(0.dp)) { Text(text) }
                        } else {
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.skill_open_errors, detail.openErrors))
        if (detail.openErrors > 0) {
            OutlinedButton(onClick = { onOpenErrors(detail.skill) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skill_open_notebook))
            }
        }
        Caption(stringResource(R.string.jlpt_not_official))
    }
}

@Composable
private fun percentOrFew(t: Tally): String =
    if (t.reliable) stringResource(R.string.jlpt_percent, t.accuracy.percent()) else stringResource(R.string.jlpt_few)
