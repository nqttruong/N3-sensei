package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
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
private fun AnswerCounts(counts: OutcomeCounts) {
    Text(stringResource(R.string.contrast_answers_counts, counts.total, counts.correct, counts.incorrect, counts.skipped), style = ContrastText.body)
}

private val listPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

private fun fraction(part: Int, whole: Int) = if (whole <= 0) 0f else part.toFloat() / whole

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
        val wrong = ContrastTones.incorrect()
        LazyColumn(Modifier.fillMaxSize().testTag(InsightTags.LIST), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                GradientHeader(wrong) {
                    Text(stringResource(R.string.contrast_errors_total, data.totals.incorrect), style = ContrastText.screenTitle.copy(fontSize = 26.sp), modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.contrast_answers_counts, data.totals.total, data.totals.correct, data.totals.incorrect, data.totals.skipped), style = ContrastText.body, color = Color.White.copy(alpha = 0.92f))
                    Text(rateText(data.rate), style = ContrastText.body, color = Color.White.copy(alpha = 0.92f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val contentDescription = stringResource(R.string.contrast_filter_type)
                    ToneFilterRow(
                        options = listOf<Pair<ContrastContentType?, String>>(null to all) + ContrastContentType.entries.map { it to typeLabel(it) },
                        selected = data.contentType,
                        onSelect = onSelectContentType,
                        tone = { it?.let { t -> ContrastTones.category(t) } ?: ContrastTones.incorrect() },
                        glyph = { it?.let(::categoryGlyph) },
                        modifier = Modifier.testTag(InsightTags.CONTENT_FILTER).semantics { this.contentDescription = contentDescription },
                    )
                    val qDescription = stringResource(R.string.contrast_filter_qtype)
                    ToneFilterRow(
                        options = listOf<Pair<ContrastQuestionType?, String>>(null to all) + ContrastQuestionType.entries.map { it to questionTypeName(it) },
                        selected = data.questionType,
                        onSelect = onSelectQuestionType,
                        tone = { ContrastTones.practice() },
                        glyph = { null },
                        modifier = Modifier.testTag(InsightTags.QTYPE_FILTER).semantics { this.contentDescription = qDescription },
                    )
                    val gDescription = stringResource(R.string.contrast_filter_group)
                    ToneFilterRow(
                        options = listOf<Pair<String?, String>>(null to stringResource(R.string.contrast_filter_all_groups)) + data.groupOptions.map { it.id to it.title },
                        selected = data.groupId,
                        onSelect = onSelectGroup,
                        tone = { ContrastTones.review() },
                        glyph = { null },
                        modifier = Modifier.testTag(InsightTags.GROUP_FILTER).semantics { this.contentDescription = gDescription },
                    )
                }
            }
            if (data.totals.incorrect == 0) {
                item { Text(stringResource(R.string.contrast_errors_none), style = ContrastText.body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                if (data.directions.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.contrast_errors_directions), wrong, glyph = "→") }
                    val max = data.directions.maxOf { it.count }
                    items(data.directions, key = { "d_${it.groupId}_${it.expectedLabel}_${it.chosenLabel}" }) { row -> DirectionCard(row, max, onOpenGroup) }
                }
                if (data.otherIncorrect > 0) {
                    item { Text(stringResource(R.string.contrast_errors_other, data.otherIncorrect), style = ContrastText.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (data.groups.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.contrast_errors_groups), wrong) }
                    val max = data.groups.maxOf { it.incorrect }
                    items(data.groups, key = { "g_${it.groupId}" }) { row ->
                        ContrastCard(onClick = if (row.published) ({ onOpenGroup(row.groupId) }) else null) {
                            Text(japaneseText(row.title), style = ContrastText.section.copy(fontSize = 18.sp).japanese())
                            ToneBar(fraction(row.incorrect, max), wrong)
                            Text(stringResource(R.string.contrast_errors_group_row, row.incorrect, shortRate(row.rate)), style = ContrastText.bodySecondary)
                        }
                    }
                }
                if (data.types.isNotEmpty()) {
                    item {
                        ContrastCard {
                            Text(stringResource(R.string.contrast_errors_types), style = ContrastText.section.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
                            data.types.forEach { t ->
                                Text(stringResource(R.string.contrast_errors_type_row, questionTypeName(t.type), t.incorrect, shortRate(t.rate)), style = ContrastText.bodySecondary)
                            }
                        }
                    }
                }
                if (data.recent.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.contrast_errors_recent), ContrastTones.practice()) }
                    items(data.recent, key = { "r_${it.snapshot.questionId}_${it.snapshot.answeredAt}" }) { r ->
                        ContrastCard {
                            Text(japaneseText(r.groupTitle), style = ContrastText.caption.japanese(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                japaneseText(stringResource(R.string.contrast_errors_recent_row, questionTypeName(r.snapshot.questionType), r.snapshot.chosenText.orEmpty(), r.snapshot.correctText)),
                                style = ContrastText.body.japanese(),
                            )
                            Text(formatDate(r.snapshot.answeredAt), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (data.sessions.isNotEmpty()) {
                item {
                    ContrastCard {
                        Text(stringResource(R.string.contrast_errors_trend), style = ContrastText.section.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.contrast_errors_trend_compare, data.recentSessionCount, shortRate(data.recentRate), shortRate(data.earlierRate)), style = ContrastText.bodySecondary)
                        data.sessions.forEach { s ->
                            Text(stringResource(R.string.contrast_errors_session_row, formatDate(s.firstAnsweredAt), s.counts.correct, s.counts.incorrect, s.counts.skipped), style = ContrastText.caption)
                        }
                    }
                }
            }
        }
    }
}

/** A → B in two colours, how often (bar + count), its status, an example and "study again". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionCard(row: DirectionRow, max: Int, onOpenGroup: (String) -> Unit) {
    val wrong = ContrastTones.incorrect()
    ContrastCard(modifier = Modifier.testTag(InsightTags.direction(row.groupId, row.expectedLabel, row.chosenLabel))) {
        Text(japaneseText(row.groupTitle), style = ContrastText.caption.japanese(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.contrast_errors_direction, row.expectedLabel, row.chosenLabel), style = ContrastText.answer, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TonePill(row.expectedLabel, ContrastTones.correct())
            Text("→", style = ContrastText.section, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TonePill(row.chosenLabel, wrong)
        }
        ToneBar(fraction(row.count, max), wrong)
        Text(stringResource(R.string.contrast_errors_direction_count, row.count, row.sessions), style = ContrastText.bodySecondary)
        when (row.status) {
            DirectionStatus.TO_REINFORCE -> TonePill(stringResource(R.string.contrast_errors_to_reinforce), ContrastTones.category(ContrastContentType.KANJI))
            DirectionStatus.SETTLING -> TonePill(stringResource(R.string.contrast_errors_settling, row.sessionsSince), ContrastTones.review())
            DirectionStatus.SINGLE -> Unit
        }
        row.example?.let { e ->
            Text(japaneseText(stringResource(R.string.contrast_errors_example, e.chosenText.orEmpty(), e.correctText)), style = ContrastText.bodySecondary.japanese())
            if (row.exampleOutdated) Text(stringResource(R.string.contrast_errors_example_old), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.published) {
            OutlinedButton(onClick = { onOpenGroup(row.groupId) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.contrast_errors_open_group), style = ContrastText.label.copy(fontSize = 15.sp))
            }
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
        val review = ContrastTones.review()
        val reinforce = ContrastTones.category(ContrastContentType.KANJI)
        val upcoming = ContrastTones.practice()
        val neutral = ContrastTones.neutral()
        LazyColumn(Modifier.fillMaxSize().testTag(InsightTags.LIST), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                GradientHeader(review) {
                    Text(stringResource(R.string.contrast_review_due, data.due.size), style = ContrastText.screenTitle.copy(fontSize = 26.sp), modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.contrast_review_upcoming_count, data.upcoming.size), style = ContrastText.body, color = Color.White.copy(alpha = 0.92f))
                    data.nextDueAt?.let { Text(stringResource(R.string.contrast_review_next, formatDate(it)), style = ContrastText.bodySecondary, color = Color.White.copy(alpha = 0.92f)) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    data.activeRemaining?.let { remaining ->
                        GradientButton(stringResource(R.string.contrast_continue_session, remaining), ContrastTones.practice(), onResume, Modifier.testTag(InsightTags.RESUME_REVIEW))
                    }
                    if (data.due.isNotEmpty()) {
                        GradientButton(stringResource(R.string.contrast_review_start), review, onStartReview, Modifier.testTag(InsightTags.START_REVIEW))
                    } else {
                        Text(
                            stringResource(if (data.upcoming.isEmpty()) R.string.contrast_review_nothing_scheduled else R.string.contrast_review_none_due),
                            style = ContrastText.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.contrast_review_new_note, data.newAvailableToday), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (data.due.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.contrast_review_due_section), review, glyph = "今") }
                items(data.due, key = { "due_${it.groupId}" }) { ReviewCard(it, review, onOpenGroup) }
            }
            if (data.toReinforce.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.contrast_review_reinforce_section), reinforce, glyph = "強") }
                items(data.toReinforce, key = { "re_${it.groupId}" }) { ReviewCard(it, reinforce, onOpenGroup) }
            }
            if (data.upcoming.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.contrast_review_upcoming_section), upcoming, glyph = "次") }
                items(data.upcoming, key = { "up_${it.groupId}" }) { ReviewCard(it, null, onOpenGroup) }
            }
            if (data.unreviewable.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.contrast_review_unavailable_section), neutral) }
                items(data.unreviewable, key = { "un_$it" }) { Text(stringResource(R.string.contrast_review_unavailable_row, it), style = ContrastText.bodySecondary) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(row: ReviewRow, tone: Tone?, onOpenGroup: (String) -> Unit) {
    ContrastCard(tone = tone, onClick = { onOpenGroup(row.groupId) }, modifier = Modifier.testTag(InsightTags.reviewRow(row.groupId))) {
        Text(japaneseText(row.title), style = ContrastText.section.copy(fontSize = 18.sp).japanese(), modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryBadge(row.type)
            StatusPill(row.mastery)
            if (row.toReinforce) TonePill(stringResource(R.string.contrast_errors_to_reinforce), ContrastTones.category(ContrastContentType.KANJI))
        }
        Text(stringResource(R.string.contrast_review_due_at, formatDate(row.dueAt)), style = ContrastText.bodySecondary)
        ToneBar(fraction(row.step, row.stepCount), tone ?: ContrastTones.review())
        Text(stringResource(R.string.contrast_review_step, row.step, row.stepCount, row.intervalDays), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val correct = ContrastTones.correct()
        LazyColumn(Modifier.fillMaxSize().testTag(InsightTags.LIST), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                // Overview: mastery ring + the five real counts.
                ContrastCard(tone = correct) {
                    Text(stringResource(R.string.contrast_progress_overview), style = ContrastText.section, modifier = Modifier.semantics { heading() })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProgressRing(fraction(r.mastered, r.published), correct, size = 92.dp, stroke = 10.dp) {
                            Text("${(fraction(r.mastered, r.published) * 100).roundToInt()}%", style = ContrastText.section, color = correct.accent)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.contrast_ui_mastered_of, r.mastered, r.published), style = ContrastText.body.copy(fontWeight = FontWeight.SemiBold))
                            ProgressLegend(stringResource(R.string.contrast_progress_learning), r.learning, ContrastTones.practice())
                            ProgressLegend(stringResource(R.string.contrast_progress_due), r.due, ContrastTones.review())
                            ProgressLegend(stringResource(R.string.contrast_progress_not_started), r.notStarted, ContrastTones.neutral())
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FigureTile(r.published.toString(), stringResource(R.string.contrast_progress_published), ContrastTones.practice(), Modifier.weight(1f))
                    FigureTile(r.mastered.toString(), stringResource(R.string.contrast_progress_mastered), correct, Modifier.weight(1f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.contrast_progress_due_note), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.contrast_progress_mastery_note), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                ContrastCard {
                    Text(stringResource(R.string.contrast_progress_answers), style = ContrastText.section.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
                    AnswerCounts(r.totals)
                    Text(rateText(data.rate), style = ContrastText.body)
                    if (r.historical.total > 0) Text(stringResource(R.string.contrast_progress_historical, r.historical.total), style = ContrastText.caption)
                }
            }
            item { SectionTitle(stringResource(R.string.contrast_progress_categories), ContrastTones.practice()) }
            items(r.categories, key = { "c_${it.type}" }) { c ->
                val tone = ContrastTones.category(c.type)
                ContrastCard(tone = tone) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryBadge(c.type)
                        Text(stringResource(R.string.contrast_ui_groups_count, c.total), style = ContrastText.label, color = tone.accent)
                    }
                    ToneBar(fraction(c.started, c.total), tone)
                    Text(stringResource(R.string.contrast_progress_category_row, c.started, c.total, c.mastered, c.due), style = ContrastText.bodySecondary)
                }
            }
            if (data.types.isNotEmpty()) {
                item {
                    ContrastCard {
                        Text(stringResource(R.string.contrast_progress_by_type), style = ContrastText.section.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
                        data.types.forEach { t -> Text("${questionTypeName(t.type)}: ${shortRate(t.rate)}", style = ContrastText.bodySecondary) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.nextDueAt?.let { Text(stringResource(R.string.contrast_review_next, formatDate(it)), style = ContrastText.body) }
                    GradientButton(stringResource(R.string.contrast_progress_open_review), ContrastTones.review(), onOpenReview, Modifier.testTag(InsightTags.PROGRESS_REVIEW))
                }
            }
            item { SectionTitle(stringResource(R.string.contrast_progress_groups), ContrastTones.practice()) }
            items(data.groups, key = { "p_${it.groupId}" }) { g ->
                ContrastCard(onClick = { onOpenGroup(g.groupId) }, modifier = Modifier.testTag(InsightTags.progressRow(g.groupId))) {
                    Text(japaneseText(g.title), style = ContrastText.section.copy(fontSize = 18.sp).japanese())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryBadge(g.type)
                        StatusPill(g.mastery)
                        if (g.isDue) TonePill(stringResource(R.string.contrast_progress_due), ContrastTones.review())
                    }
                    Text(if (g.counts.total == 0) stringResource(R.string.contrast_progress_group_none) else shortRate(g.rate), style = ContrastText.bodySecondary)
                    g.dueAt?.let { Text(stringResource(R.string.contrast_review_due_at, formatDate(it)), style = ContrastText.caption) }
                }
            }
        }
    }
}

@Composable
private fun ProgressLegend(label: String, value: Int, tone: Tone) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tone.accent))
        Text("$label: $value", style = ContrastText.bodySecondary)
    }
}
