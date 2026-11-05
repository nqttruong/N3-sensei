package com.n3sensei.app.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.mistakes.ErrorReviewSchedule
import com.n3sensei.app.domain.mistakes.ErrorState

@Composable
fun ErrorDetailScreen(
    viewModel: ErrorDetailViewModel,
    actions: MistakeActions,
    onReviewNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier) { data ->
        ErrorDetailContent(data, actions, onReviewNow, viewModel::setImportant, viewModel::saveNote, viewModel::setUnderstood)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorDetailContent(
    state: ErrorDetailUiState,
    actions: MistakeActions,
    onReviewNow: () -> Unit,
    onSetImportant: (Boolean) -> Unit,
    onSaveNote: (String) -> Unit,
    onSetUnderstood: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = state.error
    val record = error.record
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                if (error.isPriority) Tag(stringResource(R.string.error_priority), tone = TagTone.Accent)
                Tag(stringResource(error.state.labelRes()))
                error.skill?.let { Tag(stringResource(it.labelRes())) }
                error.jlptLevel?.let { Tag(it) }
            }
            IconToggleButton(checked = error.isImportant, onCheckedChange = onSetImportant) {
                Icon(
                    if (error.isImportant) N3Icons.Flagged else N3Icons.Unflagged,
                    contentDescription = stringResource(if (error.isImportant) R.string.error_unmark_important else R.string.error_mark_important),
                    tint = if (error.isImportant) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Caption(stringResource(R.string.error_times, record.errorCount, formatDate(record.firstWrongAt), formatDate(record.lastWrongAt)))

        // The question, answers, explanation, cause, knowledge, fixes and lessons.
        val analysis = state.mistakes?.analysis
        when {
            analysis != null -> MistakeAnalysisCard(analysis, actions)
            state.mistakes != null -> N3Card {
                record.question?.let { FuriganaText(it.contentFurigana ?: it.content, style = MaterialTheme.typography.titleMedium) }
                ErrorReviewSchedule.correctAnswer(record)?.let { Text(stringResource(R.string.mistake_correct_answer, it), fontWeight = FontWeight.SemiBold) }
                Text(record.question?.explanation?.takeIf { it.isNotBlank() } ?: stringResource(R.string.mistake_no_explanation))
                Caption(stringResource(R.string.error_no_analysis))
            }
        }

        state.mistakes?.history?.takeIf { it.isNotEmpty() }?.let { history ->
            SectionCard(stringResource(R.string.error_history)) {
                history.reversed().forEach { o ->
                    val outcome = when (o.outcome) {
                        ItemOutcome.CORRECT -> R.string.exam_review_outcome_correct
                        ItemOutcome.WRONG -> R.string.exam_review_outcome_wrong
                        ItemOutcome.UNANSWERED -> R.string.exam_review_outcome_blank
                    }
                    Text(stringResource(R.string.error_history_row, formatDate(o.submittedAt), stringResource(outcome)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionCard(stringResource(R.string.error_reviews)) {
            when (error.state) {
                ErrorState.MASTERED -> Text(stringResource(R.string.error_mastered_note))
                else -> {
                    error.review?.let { Text(stringResource(R.string.error_next_review, formatDate(it.nextReviewAt))) }
                    Caption(stringResource(R.string.error_mastery_progress, error.review?.stepIndex ?: 0, ErrorReviewSchedule.LEVELS))
                }
            }
            if (state.reviews.isEmpty()) Text(stringResource(R.string.error_reviews_none), style = MaterialTheme.typography.bodyMedium)
            state.reviews.forEach { r ->
                val text = when {
                    !r.correct -> R.string.error_review_row_wrong
                    !r.counted -> R.string.error_review_row_practice
                    else -> R.string.error_review_row_right
                }
                Text(stringResource(text, formatDate(r.reviewedAt)), style = MaterialTheme.typography.bodyMedium)
            }
            if (record.question != null) {
                Button(onClick = onReviewNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.error_review_now)) }
            }
        }

        NoteCard(record.notes, onSaveNote)

        N3Card {
            Row(
                Modifier.fillMaxWidth().toggleable(value = error.understoodAt != null, role = Role.Checkbox, onValueChange = onSetUnderstood),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = error.understoodAt != null, onCheckedChange = null)
                Text(stringResource(R.string.error_understood), modifier = Modifier.padding(start = N3Spacing.s))
            }
            error.understoodAt?.let { Caption(stringResource(R.string.error_understood_on, formatDate(it))) }
            Caption(stringResource(R.string.error_understood_note))
        }
    }
}

@Composable
private fun NoteCard(saved: String?, onSave: (String) -> Unit) {
    var draft by rememberSaveable(saved) { mutableStateOf(saved.orEmpty()) }
    SectionCard(stringResource(R.string.error_note)) {
        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (saved != null && draft == saved) Caption(stringResource(R.string.error_note_saved), Modifier.weight(1f)) else Column(Modifier.weight(1f)) {}
            OutlinedButton(onClick = { onSave(draft) }, enabled = draft.trim() != saved.orEmpty()) { Text(stringResource(R.string.error_note_save)) }
        }
    }
}
