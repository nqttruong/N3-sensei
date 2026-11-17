package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.ErrorContent
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastOption
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.furigana.Furigana
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PracticeTags {
    const val SCREEN = "contrast_practice"
    const val CHECK = "contrast_practice_check"
    const val SKIP = "contrast_practice_skip"
    const val NEXT = "contrast_practice_next"
    const val EXIT = "contrast_practice_exit"
    const val FEEDBACK = "contrast_practice_feedback"
    const val CONTEXT = "contrast_practice_context"
    fun option(id: String) = "contrast_option_$id"
    fun segment(id: String) = "contrast_segment_$id"
}

/** Everything the practice screen can ask the ViewModel to do. */
data class PracticeActions(
    val select: (String) -> Unit = {},
    val submit: () -> Unit = {},
    val skip: () -> Unit = {},
    val next: () -> Unit = {},
    val exit: () -> Unit = {},
    val continueActive: () -> Unit = {},
    val startRequested: () -> Unit = {},
    val retry: () -> Unit = {},
)

@Composable
fun ContrastPracticeScreen(
    viewModel: ContrastPracticeViewModel,
    onExit: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val completed = state as? PracticeUiState.Completed
    LaunchedEffect(completed?.sessionId) { completed?.let { onFinished(it.sessionId) } }
    ContrastPracticeContent(
        state,
        PracticeActions(
            select = viewModel::select,
            submit = viewModel::submit,
            skip = viewModel::skip,
            next = viewModel::next,
            exit = onExit,
            continueActive = viewModel::continueActive,
            startRequested = viewModel::startRequested,
            retry = viewModel::retryLoading,
        ),
        modifier,
    )
}

@Composable
fun ContrastPracticeContent(state: PracticeUiState, actions: PracticeActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag(PracticeTags.SCREEN)) {
        when (state) {
            PracticeUiState.Loading, is PracticeUiState.Completed -> LoadingContent()
            is PracticeUiState.Failed -> ErrorContent(onRetry = actions.retry)
            PracticeUiState.NothingToPractice -> CenteredAction(stringResource(R.string.contrast_practice_nothing), stringResource(R.string.contrast_practice_back_home), actions.exit)
            is PracticeUiState.ConfirmReplace -> ConfirmReplace(state, actions)
            is PracticeUiState.QuestionUnavailable -> CenteredAction(stringResource(R.string.contrast_practice_unavailable), stringResource(R.string.contrast_practice_next), actions.next)
            is PracticeUiState.Ready -> QuestionScaffold(state.item, state.selectedOptionId, null, submitting = false, saveFailed = state.saveFailed, contentChanged = state.contentChanged, actions)
            is PracticeUiState.Submitting -> QuestionScaffold(state.item, state.selectedOptionId, null, submitting = true, saveFailed = false, contentChanged = false, actions)
            is PracticeUiState.Feedback -> QuestionScaffold(state.item, state.feedback.chosenOptionId, state.feedback, submitting = false, saveFailed = false, contentChanged = false, actions)
        }
    }
}

@Composable
private fun CenteredAction(message: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(N3Spacing.screen), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        MessageContent(message, Modifier.weight(1f, fill = false))
        Button(onClick = onClick, modifier = Modifier.padding(top = N3Spacing.l)) { Text(action) }
    }
}

@Composable
private fun ConfirmReplace(state: PracticeUiState.ConfirmReplace, actions: PracticeActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen), verticalArrangement = Arrangement.spacedBy(N3Spacing.m)) {
        Text(stringResource(R.string.contrast_practice_confirm_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.contrast_practice_confirm_body, state.activeTitles.joinToString(", "), state.remaining), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = actions.continueActive, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contrast_practice_continue_old)) }
        OutlinedButton(onClick = actions.startRequested, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contrast_practice_start_new)) }
    }
}

@Composable
private fun QuestionScaffold(
    item: PracticeQuestion,
    selected: String?,
    feedback: PracticeFeedback?,
    submitting: Boolean,
    saveFailed: Boolean,
    contentChanged: Boolean,
    actions: PracticeActions,
) {
    val question = item.question
    val canSelect = feedback == null && !submitting
    Column(Modifier.fillMaxSize()) {
        PracticeHeader(item, actions.exit)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
        ) {
            Text(questionTypeLabel(question.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(question.instructionVi, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (contentChanged) InfoBanner(stringResource(R.string.contrast_practice_content_changed))
            when (question.type) {
                ContrastQuestionType.MULTIPLE_CHOICE -> MultipleChoiceExercise(question, selected, feedback, canSelect, actions.select)
                ContrastQuestionType.FILL_IN_THE_BLANK -> FillBlankExercise(question, selected, feedback, canSelect, actions.select)
                ContrastQuestionType.ERROR_DETECTION -> ErrorDetectionExercise(question, selected, feedback, canSelect, actions.select)
                ContrastQuestionType.CONTEXT_SELECTION -> ContextSelectionExercise(question, selected, feedback, canSelect, actions.select)
            }
            if (feedback != null) FeedbackPanel(item, feedback)
        }
        HorizontalDivider()
        PracticeActionsBar(selected, feedback, submitting, saveFailed, actions)
    }
}

@Composable
private fun PracticeHeader(item: PracticeQuestion, onExit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.groupTitle, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.contrast_practice_progress, (item.done + 1).coerceAtMost(item.total), item.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onExit, modifier = Modifier.testTag(PracticeTags.EXIT)) { Text(stringResource(R.string.contrast_practice_exit)) }
        }
        N3ProgressBar(progress = if (item.total == 0) 0f else item.done.toFloat() / item.total)
    }
}

@Composable
private fun questionTypeLabel(type: ContrastQuestionType) = stringResource(
    when (type) {
        ContrastQuestionType.MULTIPLE_CHOICE -> R.string.contrast_qtype_mc
        ContrastQuestionType.ERROR_DETECTION -> R.string.contrast_qtype_ed
        ContrastQuestionType.FILL_IN_THE_BLANK -> R.string.contrast_qtype_fill
        ContrastQuestionType.CONTEXT_SELECTION -> R.string.contrast_qtype_context
    },
)

// ---- The four exercise types ----

@Composable
private fun MultipleChoiceExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    question.prompt?.let { prompt ->
        FuriganaText(prompt.furigana ?: prompt.japanese, style = MaterialTheme.typography.titleLarge)
        if (feedback != null) prompt.translationVi?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    OptionList(question.options, selected, feedback, enabled, onSelect)
}

/** The blank shows the chosen form in place, highlighted; options are the forms of the pack (no typed answers in the contract). */
@Composable
private fun FillBlankExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    val prompt = question.prompt
    if (prompt != null) {
        val markup = prompt.furigana ?: prompt.japanese
        val shown = (feedback?.correctOptionId ?: selected)?.let(question::option)
        val blankAt = runCatching { Furigana.baseText(markup).indexOf(BLANK) }.getOrDefault(-1)
        if (shown != null && blankAt >= 0) {
            val inserted = shown.furigana ?: shown.text
            val filled = markup.replaceFirst(BLANK, inserted)
            val length = runCatching { Furigana.baseText(inserted).length }.getOrDefault(shown.text.length)
            FuriganaText(filled, style = MaterialTheme.typography.titleLarge, highlight = listOf(blankAt until blankAt + length))
        } else {
            FuriganaText(markup, style = MaterialTheme.typography.titleLarge)
        }
        if (feedback != null) prompt.translationVi?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    OptionList(question.options, selected, feedback, enabled, onSelect)
}

/** Segments of the sentence as the pack defines them (option ids): the learner taps the wrong one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorDetectionExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.xs),
    ) {
        question.options.forEachIndexed { index, segment ->
            val isSelected = segment.id == selected
            val isWrongSegment = feedback != null && segment.id == feedback.correctOptionId
            val colors = MaterialTheme.colorScheme
            val border = when {
                isWrongSegment -> BorderStroke(2.dp, colors.error)
                isSelected -> BorderStroke(2.dp, colors.primary)
                else -> BorderStroke(1.dp, colors.outlineVariant)
            }
            val description = stringResource(R.string.contrast_segment_description, index + 1, segment.text)
            val marker = when {
                isWrongSegment -> stringResource(R.string.contrast_option_correct)
                feedback != null && isSelected -> stringResource(R.string.contrast_option_chosen)
                else -> null
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                border = border,
                color = if (isSelected) colors.secondaryContainer else colors.surface,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PracticeTags.segment(segment.id))
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(segment.id) })
                    .semantics { contentDescription = description; marker?.let { stateDescription = it } },
            ) {
                Column(Modifier.padding(horizontal = N3Spacing.s, vertical = N3Spacing.xs)) {
                    FuriganaText(segment.furigana ?: segment.text, style = MaterialTheme.typography.titleMedium)
                    marker?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (isWrongSegment) colors.error else colors.onSurfaceVariant) }
                }
            }
        }
    }
    if (feedback != null) question.prompt?.translationVi?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

/** The situation (and its Japanese lines, kept as written), then whole sentences to choose from. */
@Composable
private fun ContextSelectionExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    question.context?.let { context ->
        N3Card(modifier = Modifier.testTag(PracticeTags.CONTEXT)) {
            Text(context.descriptionVi, style = MaterialTheme.typography.bodyLarge)
            context.japanese?.let { japanese ->
                val lines = (context.furigana ?: japanese).split('\n')
                lines.forEach { line -> if (line.isNotBlank()) FuriganaText(line) }
            }
        }
    }
    OptionList(question.options, selected, feedback, enabled, onSelect)
}

/** Single-choice options; after the answer each one says (in words, not only colour) whether it was the answer or the choice. */
@Composable
private fun OptionList(options: List<ContrastOption>, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
        options.forEach { option ->
            val isSelected = option.id == selected
            val isCorrect = feedback != null && option.id == feedback.correctOptionId
            val isWrongChoice = feedback != null && isSelected && !isCorrect
            val colors = MaterialTheme.colorScheme
            val marker = when {
                isCorrect -> stringResource(R.string.contrast_option_correct)
                isWrongChoice -> stringResource(R.string.contrast_option_chosen)
                else -> null
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = when {
                    isCorrect -> BorderStroke(2.dp, colors.primary)
                    isWrongChoice -> BorderStroke(2.dp, colors.error)
                    isSelected -> BorderStroke(2.dp, colors.primary)
                    else -> BorderStroke(1.dp, colors.outlineVariant)
                },
                color = if (isSelected && feedback == null) colors.secondaryContainer else colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(PracticeTags.option(option.id))
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option.id) })
                    .semantics { marker?.let { stateDescription = it } },
            ) {
                Row(Modifier.padding(N3Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                    RadioButton(selected = isSelected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        FuriganaText(option.furigana ?: option.text)
                        marker?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                                Icon(if (isCorrect) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isCorrect) colors.primary else colors.error)
                                Text(it, style = MaterialTheme.typography.labelMedium, color = if (isCorrect) colors.primary else colors.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Feedback and actions ----

@Composable
private fun FeedbackPanel(item: PracticeQuestion, feedback: PracticeFeedback) {
    val question = item.question
    val colors = MaterialTheme.colorScheme
    val (icon, title, tint) = when (feedback.outcome) {
        AnswerOutcome.CORRECT -> Triple(Icons.Filled.Check, stringResource(R.string.contrast_feedback_correct), colors.primary)
        AnswerOutcome.INCORRECT -> Triple(Icons.Filled.Close, stringResource(R.string.contrast_feedback_incorrect), colors.error)
        AnswerOutcome.SKIPPED -> Triple(Icons.Filled.Info, stringResource(R.string.contrast_feedback_skipped), colors.onSurfaceVariant)
    }
    N3Card(modifier = Modifier.testTag(PracticeTags.FEEDBACK).semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint, modifier = Modifier.semantics { heading() })
        }
        val correct = question.option(feedback.correctOptionId)
        if (correct != null) Text(stringResource(R.string.contrast_feedback_answer, correct.text), style = MaterialTheme.typography.bodyLarge)
        if (question.type == ContrastQuestionType.ERROR_DETECTION) {
            question.correction?.let { Text(stringResource(R.string.contrast_feedback_correction, it.text), style = MaterialTheme.typography.bodyLarge) }
        }
        Text(feedback.explanationVi, style = MaterialTheme.typography.bodyMedium)
        feedback.chosenFeedbackVi?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        feedback.confusion?.let { (expected, chosen) ->
            Text(
                stringResource(R.string.contrast_feedback_confusion, item.itemLabels[expected] ?: expected, item.itemLabels[chosen] ?: chosen),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (feedback.retryAdded) Text(stringResource(R.string.contrast_feedback_retry_added), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        feedback.nextDueAt?.let { Text(stringResource(R.string.contrast_feedback_next_due, formatDate(it)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

@Composable
private fun PracticeActionsBar(selected: String?, feedback: PracticeFeedback?, submitting: Boolean, saveFailed: Boolean, actions: PracticeActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
        if (saveFailed) {
            Text(stringResource(R.string.contrast_practice_save_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        when {
            feedback != null -> Button(onClick = actions.next, modifier = Modifier.fillMaxWidth().testTag(PracticeTags.NEXT)) {
                Text(stringResource(if (feedback.sessionCompleted) R.string.contrast_practice_finish else R.string.contrast_practice_next))
            }
            submitting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s), modifier = Modifier.heightIn(min = 48.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.contrast_practice_saving))
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                OutlinedButton(onClick = actions.skip, modifier = Modifier.weight(1f).testTag(PracticeTags.SKIP)) { Text(stringResource(R.string.contrast_practice_skip)) }
                Button(onClick = actions.submit, enabled = selected != null, modifier = Modifier.weight(1f).testTag(PracticeTags.CHECK)) {
                    Text(stringResource(R.string.contrast_practice_check))
                }
            }
        }
    }
}

private const val BLANK = "＿＿＿"

private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatDate(instant: Instant): String = DATE.format(instant.atZone(ZoneId.systemDefault()))
