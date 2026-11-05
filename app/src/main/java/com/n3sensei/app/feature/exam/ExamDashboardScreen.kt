package com.n3sensei.app.feature.exam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.dashboard.DailyPlan
import com.n3sensei.app.domain.dashboard.DailyPlanner
import com.n3sensei.app.domain.dashboard.ExamDashboard
import com.n3sensei.app.domain.dashboard.ExamStatistics
import com.n3sensei.app.domain.dashboard.PeriodComparison
import com.n3sensei.app.domain.dashboard.PlanTask
import com.n3sensei.app.domain.dashboard.SkillStats
import com.n3sensei.app.domain.dashboard.Tally
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.model.ItemType

object ExamDashboardTags {
    fun task(index: Int) = "jlpt_task_$index"
    fun skill(skill: ExamSkill) = "jlpt_skill_${skill.name}"
}

@Composable
fun ExamDashboardScreen(
    viewModel: ExamDashboardViewModel,
    onTask: (PlanTask) -> Unit,
    onOpenSkill: (ExamSkill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a task (a review, an exam): the figures and the plan have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }
    UiStateContent(state, modifier, onRetry = viewModel::load) { data ->
        ExamDashboardContent(data, viewModel::setBudget, onTask, onOpenSkill)
    }
}

private enum class StatPeriod { WEEK, MONTH }

@Composable
fun ExamDashboardContent(
    data: ExamDashboard,
    onSetBudget: (Int) -> Unit,
    onTask: (PlanTask) -> Unit,
    onOpenSkill: (ExamSkill) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.l),
    ) {
        if (!data.hasHistory) InfoBanner(stringResource(R.string.jlpt_no_history))
        OverviewCard(data)
        PlanCard(data.plan, onSetBudget, onTask)
        if (data.skills.isNotEmpty()) SkillsCard(data.skills, onOpenSkill)
        ErrorsCard(data)
        if (data.hasHistory) ProgressCard(data.week, data.month)
        Caption(stringResource(R.string.jlpt_not_official))
    }
}

@Composable
internal fun percentOrDash(tally: Tally): String =
    if (tally.reliable) stringResource(R.string.jlpt_percent, tally.accuracy.percent()) else stringResource(R.string.jlpt_none)

@Composable
private fun OverviewCard(data: ExamDashboard) {
    val o = data.overview
    SectionCard(stringResource(R.string.jlpt_overview)) {
        Row(Modifier.fillMaxWidth()) {
            Stat("${o.examsTaken}", R.string.jlpt_exams_taken, Modifier.weight(1f))
            Stat("${o.questions.answered}", R.string.jlpt_answered, Modifier.weight(1f))
            Stat(percentOrDash(o.questions), R.string.jlpt_accuracy, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("${o.openErrors}", R.string.jlpt_open_errors, Modifier.weight(1f))
            Stat("${o.masteredErrors}", R.string.jlpt_mastered_errors, Modifier.weight(1f))
            Stat("${o.dueToday}", R.string.jlpt_due_today, Modifier.weight(1f))
        }
        Caption(
            if (o.questions.reliable) {
                stringResource(R.string.jlpt_accuracy_note)
            } else {
                stringResource(R.string.jlpt_progress_accuracy_few, ExamStatistics.MIN_QUESTIONS)
            },
        )
    }
}

@Composable
private fun Stat(value: String, label: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlanCard(plan: DailyPlan, onSetBudget: (Int) -> Unit, onTask: (PlanTask) -> Unit) {
    SectionCard(stringResource(R.string.jlpt_plan)) {
        Caption(stringResource(R.string.jlpt_plan_budget))
        FilterChipRow(
            options = DailyPlanner.BUDGETS.map { FilterOption(it, stringResource(R.string.jlpt_plan_minutes, it)) },
            selected = plan.budgetMinutes,
            onSelect = onSetBudget,
        )
        if (plan.tasks.isEmpty()) Text(stringResource(R.string.jlpt_plan_empty), style = MaterialTheme.typography.bodyMedium)
        plan.tasks.forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().testTag(ExamDashboardTags.task(index)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(taskText(task), style = MaterialTheme.typography.bodyLarge)
                    Caption(stringResource(R.string.jlpt_task_minutes, (task.seconds + 59) / 60))
                }
                FilledTonalButton(onClick = { onTask(task) }) { Text(stringResource(R.string.jlpt_task_start)) }
            }
        }
        if (plan.tasks.isNotEmpty()) {
            Caption(stringResource(R.string.jlpt_plan_total, (plan.seconds + 59) / 60, plan.budgetMinutes))
        }
        if (plan.errorsLeft > 0) Caption(stringResource(R.string.jlpt_plan_errors_left, plan.errorsLeft))
        if (plan.listeningWeakWithoutAudio) Caption(stringResource(R.string.jlpt_plan_listening))
    }
}

@Composable
internal fun taskText(task: PlanTask): String = when (task) {
    is PlanTask.ReviewErrors -> task.skill?.let { stringResource(R.string.jlpt_task_errors, task.count, stringResource(it.labelRes()).lowercase()) }
        ?: stringResource(R.string.jlpt_task_errors_any, task.count)
    is PlanTask.StudyWeakItems -> stringResource(
        when (task.type) {
            ItemType.VOCABULARY -> R.string.jlpt_task_items_vocabulary
            ItemType.KANJI -> R.string.jlpt_task_items_kanji
            ItemType.GRAMMAR -> R.string.jlpt_task_items_grammar
        },
        task.refs.size,
    )
    is PlanTask.TakeSectionExam -> stringResource(R.string.jlpt_task_exam, task.title, task.questions)
    is PlanTask.ReviewFlashcards -> stringResource(R.string.jlpt_task_flashcards, task.count)
}

/** One bar per skill (all-time share right), the chosen period against the one before, tap for details. */
@Composable
private fun SkillsCard(skills: List<SkillStats>, onOpenSkill: (ExamSkill) -> Unit) {
    var period by rememberSaveable { mutableStateOf(StatPeriod.WEEK) }
    SectionCard(stringResource(R.string.jlpt_skills)) {
        FilterChipRow(
            options = listOf(
                FilterOption(StatPeriod.WEEK, stringResource(R.string.jlpt_period_week)),
                FilterOption(StatPeriod.MONTH, stringResource(R.string.jlpt_period_month)),
            ),
            selected = period,
            onSelect = { period = it },
        )
        skills.forEach { s ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag(ExamDashboardTags.skill(s.skill))
                    .clickable(role = Role.Button) { onOpenSkill(s.skill) }
                    .padding(vertical = N3Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(N3Spacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(s.skill.labelRes()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (s.overall.reliable) {
                            stringResource(R.string.jlpt_skill_overall, s.overall.accuracy.percent(), s.overall.correct, s.overall.total)
                        } else {
                            stringResource(R.string.jlpt_skill_overall_few, s.overall.total, ExamStatistics.MIN_QUESTIONS)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                N3ProgressBar(if (s.overall.reliable) s.overall.accuracy.toFloat() else 0f)
                val (now, before) = if (period == StatPeriod.WEEK) s.thisWeek to s.lastWeek else s.thisMonth to s.lastMonth
                Caption(
                    stringResource(
                        if (period == StatPeriod.WEEK) R.string.jlpt_skill_compare_week else R.string.jlpt_skill_compare_month,
                        periodText(now), periodText(before),
                    ),
                )
            }
        }
    }
}

@Composable
private fun periodText(t: Tally): String = if (t.reliable) stringResource(R.string.jlpt_percent, t.accuracy.percent()) else stringResource(R.string.jlpt_few)

@Composable
private fun ErrorsCard(data: ExamDashboard) {
    SectionCard(stringResource(R.string.jlpt_errors)) {
        if (data.categories.isEmpty()) {
            Text(stringResource(R.string.jlpt_errors_none), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Caption(stringResource(R.string.jlpt_common_categories))
        data.categories.take(4).forEach {
            Text(stringResource(R.string.jlpt_category_row, stringResource(it.category.labelRes()), it.count), style = MaterialTheme.typography.bodyMedium)
        }
        val confused = data.weakness.frequentKnowledge.take(5)
        if (confused.isNotEmpty()) {
            Caption(stringResource(R.string.jlpt_confused))
            confused.forEach { stat ->
                val name = data.knowledge[stat.ref]?.title() ?: stat.ref.id
                Text(stringResource(R.string.mistakes_knowledge_row, name, stat.missed, stat.seen), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.jlpt_recurring, data.weakness.recurring.size), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.jlpt_improving, data.weakness.improvedQuestions.size), style = MaterialTheme.typography.bodyMedium)
        val few = data.weakness.skills.filter { !it.reliable }
        if (few.isNotEmpty()) {
            Caption(stringResource(R.string.jlpt_insufficient, few.map { stringResource(it.skill.labelRes()) }.joinToString(", ")))
        }
    }
}

@Composable
private fun ProgressCard(week: PeriodComparison, month: PeriodComparison) {
    SectionCard(stringResource(R.string.jlpt_progress)) {
        Comparison(R.string.jlpt_progress_week, week)
        HorizontalDivider()
        Comparison(R.string.jlpt_progress_month, month)
    }
}

@Composable
private fun Comparison(title: Int, c: PeriodComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xxs)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.jlpt_progress_questions, c.current.total, c.previous.total), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (c.accuracyDelta != null) {
                stringResource(R.string.jlpt_progress_accuracy, c.current.accuracy.percent(), c.previous.accuracy.percent())
            } else {
                stringResource(R.string.jlpt_progress_accuracy_few, ExamStatistics.MIN_QUESTIONS)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.jlpt_progress_fixed, c.fixedCurrent, c.fixedPrevious), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.jlpt_progress_recurring, c.recurringCurrent, c.recurringPrevious), style = MaterialTheme.typography.bodyMedium)
    }
}
