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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.CardTone
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.component.StackSegment
import com.n3sensei.app.core.designsystem.component.StackedBar
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.AttemptStatus
import com.n3sensei.app.domain.exam.ExamComparison
import com.n3sensei.app.domain.exam.ExamResultOverview
import com.n3sensei.app.domain.exam.ExamScore
import com.n3sensei.app.domain.exam.ExamScoring
import com.n3sensei.app.domain.exam.Improvement
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.ResultItem
import com.n3sensei.app.domain.exam.SkillScore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Which questions the review opens with. */
enum class ReviewFilter { ALL, WRONG, BLANK }

@Composable
fun ExamResultScreen(
    viewModel: ExamResultViewModel,
    onReview: (filter: ReviewFilter, position: Int?) -> Unit,
    onOpenNotebook: () -> Unit,
    onNewExam: () -> Unit,
    onRetake: (examSetId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, onRetry = viewModel::load) { overview ->
        ExamResultContent(overview, onReview, onOpenNotebook, onNewExam, onRetake, onOpenAttempt, onAnalyze)
    }
}

@Composable
fun ExamResultContent(
    overview: ExamResultOverview,
    onReview: (filter: ReviewFilter, position: Int?) -> Unit,
    onOpenNotebook: () -> Unit,
    onNewExam: () -> Unit,
    onRetake: (examSetId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = overview.report
    val score = report.score
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.l),
    ) {
        SummaryCard(report.attempt.examTitle, report.attempt.status, score)
        SkillsCard(score.skills)
        ImprovementsCard(report.improvements)
        ComparisonCard(overview.comparison)
        if (overview.history.size > 1) HistoryCard(overview, onOpenAttempt)

        val wrong = report.items.filter { it.outcome == ItemOutcome.WRONG }
        val blank = report.items.filter { it.outcome == ItemOutcome.UNANSWERED }
        if (wrong.isNotEmpty()) {
            ItemListCard(stringResource(R.string.exam_result_wrong_list, wrong.size), wrong) { onReview(ReviewFilter.WRONG, it) }
        }
        if (blank.isNotEmpty()) {
            ItemListCard(stringResource(R.string.exam_result_blank_list, blank.size), blank) { onReview(ReviewFilter.BLANK, it) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            OutlinedButton(onClick = { onReview(ReviewFilter.ALL, null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.exam_result_review_all))
            }
            if (score.correct < score.total) {
                Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exam_result_analyze))
                }
            }
            OutlinedButton(onClick = onOpenNotebook, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.exam_result_open_notebook))
            }
            if (overview.setAvailable) {
                OutlinedButton(onClick = { onRetake(report.attempt.examSetId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exam_result_retake))
                }
            }
            OutlinedButton(onClick = onNewExam, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.exam_result_new_exam))
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, status: AttemptStatus, score: ExamScore) {
    N3Card(tone = CardTone.Emphasis) {
        Text(
            stringResource(if (status == AttemptStatus.EXPIRED) R.string.exam_result_expired else R.string.exam_result_submitted),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Caption(stringResource(R.string.exam_result_practice_score))
        Text(
            stringResource(R.string.exam_result_score, score.correct, score.total),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(R.string.exam_result_accuracy, score.accuracy.percent()), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.exam_result_counts, score.correct, score.wrong, score.unanswered))
        score.durationSeconds?.let { seconds ->
            Text(
                if (score.timeLimitSeconds > 0) {
                    stringResource(R.string.exam_result_time_of_limit, examTimeSpent(seconds), examDuration(score.timeLimitSeconds))
                } else {
                    stringResource(R.string.exam_result_time, examTimeSpent(seconds))
                },
            )
        }
        Caption(stringResource(R.string.exam_result_not_official))
    }
}

/** One bar per skill: right, wrong and blank answers, in two colours and the empty track. */
@Composable
private fun SkillsCard(skills: List<SkillScore>) {
    if (skills.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.exam_result_skills)) {
        skills.forEach { skill ->
            val name = stringResource(skill.skill.labelRes())
            Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.exam_result_skill_row, skill.correct, skill.total, skill.accuracy.percent()))
                }
                StackedBar(
                    segments = listOf(
                        StackSegment(skill.correct, colors.primary),
                        StackSegment(skill.wrong, colors.error),
                        StackSegment(skill.unanswered, colors.surfaceVariant),
                    ),
                    description = stringResource(R.string.exam_result_skill_chart, name, skill.correct, skill.wrong, skill.unanswered, skill.total),
                )
                if (skill.timeSpentMs >= 1_000) Caption(stringResource(R.string.exam_result_skill_time, examTimeSpent(skill.timeSpentMs / 1_000)))
            }
        }
    }
}

@Composable
private fun ImprovementsCard(improvements: List<Improvement>) {
    SectionCard(stringResource(R.string.exam_result_improve)) {
        if (improvements.isEmpty()) {
            Text(stringResource(R.string.exam_result_improve_none, (ExamScoring.WEAK_ACCURACY * 100).toInt()))
        }
        improvements.forEach { item ->
            val text = when (item) {
                is Improvement.WeakSkill -> stringResource(R.string.exam_improve_weak_skill, stringResource(item.skill.labelRes()), item.correct, item.total)
                is Improvement.FrequentMistake -> stringResource(R.string.exam_improve_frequent, stringResource(item.type.labelRes()), item.missed, item.total)
                is Improvement.Unanswered -> stringResource(
                    if (item.timeRanOut) R.string.exam_improve_unanswered_time else R.string.exam_improve_unanswered,
                    item.count,
                )
            }
            Text("• $text")
        }
    }
}

@Composable
private fun ComparisonCard(comparison: ExamComparison?) {
    SectionCard(stringResource(R.string.exam_result_compare)) {
        if (comparison == null) {
            Text(stringResource(R.string.exam_result_compare_first))
            return@SectionCard
        }
        val before = comparison.previousScore
        Text(stringResource(R.string.exam_result_compare_score, signed(comparison.correctDelta), before.correct, before.total))
        Text(stringResource(R.string.exam_result_compare_accuracy, signed(comparison.accuracyDelta.percent())))
        comparison.durationDeltaSeconds?.let { delta ->
            Text(
                when {
                    delta < 0 -> stringResource(R.string.exam_result_compare_time_faster, examTimeSpent(-delta))
                    delta > 0 -> stringResource(R.string.exam_result_compare_time_slower, examTimeSpent(delta))
                    else -> stringResource(R.string.exam_result_compare_time_same)
                },
            )
        }
        val improved = comparison.improved.map { change ->
            stringResource(R.string.exam_result_compare_skill, stringResource(change.skill.labelRes()), signed(change.accuracyDelta!!.percent()) + "%")
        }
        val declined = comparison.declined.map { change ->
            stringResource(R.string.exam_result_compare_skill, stringResource(change.skill.labelRes()), signed(change.accuracyDelta!!.percent()) + "%")
        }
        if (improved.isNotEmpty()) Text(stringResource(R.string.exam_result_compare_improved, improved.joinToString(", ")))
        if (declined.isNotEmpty()) Text(stringResource(R.string.exam_result_compare_declined, declined.joinToString(", ")))
    }
}

@Composable
private fun HistoryCard(overview: ExamResultOverview, onOpenAttempt: (String) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    SectionCard(stringResource(R.string.exam_result_history)) {
        overview.history.forEach { attempt ->
            val current = attempt.id == overview.report.attempt.id
            val text = stringResource(
                R.string.exam_result_history_row,
                formatter.format(Instant.ofEpochMilli(attempt.submittedAt ?: attempt.startedAt)),
                attempt.correctCount ?: 0,
                attempt.questionCount,
                attempt.durationSeconds?.let { examTimeSpent(it) } ?: "—",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (current) Modifier else Modifier.clickable(role = Role.Button) { onOpenAttempt(attempt.id) })
                    .padding(vertical = N3Spacing.xs),
            ) {
                Text(text, modifier = Modifier.weight(1f), fontWeight = if (current) FontWeight.SemiBold else null)
                if (current) Tag(stringResource(R.string.exam_result_history_current))
            }
        }
    }
}

@Composable
private fun ItemListCard(title: String, items: List<ResultItem>, onOpen: (position: Int) -> Unit) {
    SectionCard(title) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(item.position) }
                    .padding(vertical = N3Spacing.xs),
            ) {
                Text(
                    stringResource(R.string.exam_result_item_row, item.position + 1, stringResource(item.skill.labelRes())),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(item.question.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "+2", "−3", "0". */
private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${abs(value)}"
    else -> "0"
}
