package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.mistakes.ErrorReviewSchedule
import com.n3sensei.app.domain.mistakes.ReviewPriority
import com.n3sensei.app.domain.mistakes.ReviewQuestionKind

object ErrorReviewTags {
    fun option(index: Int) = "error_review_option_$index"
}

@StringRes
internal fun ReviewQuestionKind.labelRes(): Int = when (this) {
    ReviewQuestionKind.ORIGINAL -> R.string.error_kind_original
    ReviewQuestionKind.SIMILAR -> R.string.error_kind_similar
    ReviewQuestionKind.APPLICATION -> R.string.error_kind_application
    ReviewQuestionKind.CONTRAST -> R.string.error_kind_contrast
}

@Composable
fun ErrorReviewSessionScreen(viewModel: ErrorReviewSessionViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, emptyMessage = stringResource(R.string.error_session_none), onRetry = viewModel::load) { data ->
        ErrorReviewSessionContent(data, viewModel::select, viewModel::check, viewModel::next, onDone)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorReviewSessionContent(
    state: ErrorReviewSessionUiState,
    onSelect: (AnswerInput) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
    ) {
        if (state.finished) {
            Summary(state, onDone)
            return@Column
        }
        val card = state.card
        val q = card.question.question
        val result = state.result
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.dueSession) stringResource(R.string.error_session_due, state.cards.size) else stringResource(R.string.error_session_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(stringResource(R.string.error_session_progress, state.index + 1, state.cards.size), style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
            when (card.priority) {
                ReviewPriority.HIGH -> Tag(stringResource(R.string.mistake_priority_high), tone = TagTone.Accent)
                ReviewPriority.RAISED -> Tag(stringResource(R.string.mistake_priority_raised))
                ReviewPriority.NORMAL -> Unit
            }
            Tag(stringResource(card.question.kind.labelRes()))
            card.error.skill?.let { Tag(stringResource(it.labelRes())) }
        }
        when {
            card.question.kind != ReviewQuestionKind.ORIGINAL -> Caption(stringResource(R.string.error_kind_note))
            card.questionCount == 1 -> Caption(stringResource(R.string.error_only_original))
        }
        card.question.passage?.let { p ->
            if (p.text != null || p.title != null) {
                N3Card {
                    p.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    (p.furigana ?: p.text)?.let { FuriganaText(it, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
        if (q.audioPath != null || card.question.passage?.audioPath != null) InfoBanner(stringResource(R.string.exam_audio_unavailable))
        FuriganaText(q.contentFurigana ?: q.content, style = MaterialTheme.typography.titleLarge)

        when (q.format) {
            AnswerFormat.CHOICE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                q.options.forEachIndexed { index, option ->
                    val selected = (state.answer as? AnswerInput.Choice)?.index == index
                    val right = result != null && q.answerIndex == index
                    val colors = MaterialTheme.colorScheme
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (right) colors.primaryContainer else colors.surface,
                        border = BorderStroke(if (selected || right) 2.dp else 1.dp, if (right) colors.primary else if (selected) colors.outline else colors.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ErrorReviewTags.option(index))
                            .selectable(selected = selected, enabled = result == null, role = Role.RadioButton, onClick = { onSelect(AnswerInput.Choice(index)) }),
                    ) {
                        Row(Modifier.padding(horizontal = N3Spacing.m, vertical = N3Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null, enabled = result == null)
                            FuriganaText(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = N3Spacing.s))
                        }
                    }
                }
            }
            AnswerFormat.TEXT -> OutlinedTextField(
                value = (state.answer as? AnswerInput.Text)?.value.orEmpty(),
                onValueChange = { onSelect(AnswerInput.Text(it)) },
                enabled = result == null,
                singleLine = true,
                label = { Text(stringResource(R.string.exam_text_answer_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (result == null) {
            val ready = when (val a = state.answer) {
                is AnswerInput.Choice -> true
                is AnswerInput.Text -> a.value.isNotBlank()
                null -> false
            }
            Button(onClick = onCheck, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.error_review_check)) }
        } else {
            N3Card(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    stringResource(if (result.correct) R.string.error_review_right else R.string.error_review_wrong),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.mistake_correct_answer, ErrorReviewSchedule.correctAnswer(q)))
                Text(q.explanation?.takeIf { it.isNotBlank() } ?: stringResource(R.string.mistake_no_explanation), style = MaterialTheme.typography.bodyMedium)
                Caption(stringResource(R.string.error_level_change, result.stepBefore, result.stepAfter, ErrorReviewSchedule.LEVELS))
                when {
                    result.mastered -> Text(stringResource(R.string.error_review_mastered))
                    !result.counted -> Caption(stringResource(R.string.error_review_practice))
                    !result.correct -> Caption(stringResource(R.string.error_review_wrong_note))
                    result.needsAnotherQuestion -> Caption(
                        stringResource(if (card.questionCount > 1) R.string.error_needs_other_question else R.string.error_needs_other_question_none),
                    )
                }
                if (!result.mastered) Caption(stringResource(R.string.error_review_next, formatDate(result.nextReviewAt)))
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.index < state.cards.lastIndex) R.string.error_session_next else R.string.error_session_finish))
            }
        }
    }
}

/** "Đánh giá kết quả ôn": right answers, errors mastered, early reviews. */
@Composable
private fun Summary(state: ErrorReviewSessionUiState, onDone: () -> Unit) {
    val results = state.results.values
    N3Card {
        Text(stringResource(R.string.error_session_summary_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.error_session_summary, results.count { it.correct }, state.cards.size), style = MaterialTheme.typography.headlineSmall)
        results.count { it.mastered }.takeIf { it > 0 }?.let { Text(stringResource(R.string.error_session_summary_mastered, it)) }
        results.count { !it.counted }.takeIf { it > 0 }?.let { Caption(stringResource(R.string.error_session_summary_practice, it)) }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.error_review_done)) }
}
