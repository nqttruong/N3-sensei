package com.n3sensei.app.feature.exam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSessionStatus

/** Test tags of the exam screen (also used by its tests). */
object ExamSessionTags {
    const val TIMER = "exam_timer"
    const val STRIP = "exam_question_strip"
    fun cell(index: Int) = "exam_cell_$index"
    fun sheetCell(index: Int) = "exam_sheet_cell_$index"
    fun option(index: Int) = "exam_option_$index"
}

@Composable
fun ExamSessionScreen(
    viewModel: ExamSessionViewModel,
    onDone: () -> Unit,
    onSubmitted: (attemptId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Submitted (by the learner or the timer): the result has its own screen.
    val submittedId = (state as? UiState.Success)?.data
        ?.takeIf { it.status == ExamSessionStatus.SUBMITTED || it.status == ExamSessionStatus.EXPIRED }?.attempt?.id
    LaunchedEffect(submittedId) { submittedId?.let(onSubmitted) }
    UiStateContent(state, modifier, onRetry = viewModel::load) { data ->
        ExamSessionContent(
            state = data,
            onAnswer = viewModel::answer,
            onGoTo = viewModel::goTo,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onToggleFlag = viewModel::toggleFlag,
            onSubmit = viewModel::submit,
            onDone = onDone,
        )
    }
}

@Composable
fun ExamSessionContent(
    state: ExamSessionUiState,
    onAnswer: (AnswerInput) -> Unit,
    onGoTo: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFlag: () -> Unit,
    onSubmit: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.status) {
        // The attempt is being created or opened.
        ExamSessionStatus.NOT_STARTED -> LoadingContent(modifier)
        ExamSessionStatus.IN_PROGRESS -> ExamInProgress(state, onAnswer, onGoTo, onPrevious, onNext, onToggleFlag, onSubmit, modifier)
        else -> ExamFinished(state, onDone, modifier)
    }
}

@Composable
private fun ExamInProgress(
    state: ExamSessionUiState,
    onAnswer: (AnswerInput) -> Unit,
    onGoTo: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFlag: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    var showGrid by rememberSaveable { mutableStateOf(false) }
    val question = state.currentQuestion ?: return

    Column(modifier.fillMaxSize()) {
        ExamHeader(state.set.title, state.remainingMillis, onSubmit = { confirmSubmit = true })
        HorizontalDivider()
        QuestionStrip(state, onGoTo, onShowAll = { showGrid = true })
        HorizontalDivider()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = N3Spacing.screen, vertical = N3Spacing.l),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.l),
        ) {
            QuestionBody(state, question, onAnswer, onToggleFlag)
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(N3Spacing.m),
        ) {
            OutlinedButton(onClick = onPrevious, enabled = state.currentIndex > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.exam_previous), maxLines = 1)
            }
            Button(onClick = onNext, enabled = state.currentIndex < state.questions.lastIndex, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.exam_next), maxLines = 1)
            }
        }
    }

    if (showGrid) {
        QuestionGridSheet(state, onSelect = { onGoTo(it); showGrid = false }, onDismiss = { showGrid = false })
    }
    // The timer may submit while the dialog is open: it then closes by itself.
    if (confirmSubmit && state.canAnswer) {
        SubmitDialog(state, onConfirm = { confirmSubmit = false; onSubmit() }, onDismiss = { confirmSubmit = false })
    }
}

@Composable
private fun ExamHeader(title: String, remainingMillis: Long?, onSubmit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = N3Spacing.screen, end = N3Spacing.s, top = N3Spacing.s, bottom = N3Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(N3Spacing.s),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ExamTimer(remainingMillis)
        FilledTonalButton(onClick = onSubmit, contentPadding = PaddingValues(horizontal = N3Spacing.m)) {
            Text(stringResource(R.string.exam_submit), maxLines = 1)
        }
    }
}

/** The countdown: large tabular digits; the warning colour only in the last five minutes. */
@Composable
private fun ExamTimer(remainingMillis: Long?) {
    val colors = MaterialTheme.colorScheme
    val warning = remainingMillis != null && remainingMillis <= EXAM_TIME_WARNING_MILLIS
    val color = if (warning) colors.error else colors.onSurface
    val description = if (remainingMillis == null) {
        stringResource(R.string.exam_timer_untimed)
    } else {
        val seconds = (remainingMillis + 999) / 1_000
        stringResource(R.string.exam_timer_description, (seconds / 60).toInt(), (seconds % 60).toInt())
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(if (warning) 2.dp else 1.dp, if (warning) colors.error else colors.outline),
        modifier = Modifier.testTag(ExamSessionTags.TIMER).clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = remainingMillis?.let(::formatCountdown) ?: "—",
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = N3Spacing.s, vertical = N3Spacing.xxs),
        )
    }
}

@Composable
private fun QuestionStrip(state: ExamSessionUiState, onGoTo: (Int) -> Unit, onShowAll: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentIndex) { listState.animateScrollToItem((state.currentIndex - 2).coerceAtLeast(0)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).testTag(ExamSessionTags.STRIP),
            contentPadding = PaddingValues(horizontal = N3Spacing.m, vertical = N3Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs),
        ) {
            itemsIndexed(state.questions, key = { _, q -> q.id }) { index, q ->
                QuestionCell(
                    index, answered = q.id in state.answers, flagged = q.id in state.flagged, current = index == state.currentIndex,
                    tag = ExamSessionTags.cell(index),
                ) {
                    onGoTo(index)
                }
            }
        }
        IconButton(onClick = onShowAll) {
            Icon(N3Icons.QuestionGrid, contentDescription = stringResource(R.string.exam_all_questions))
        }
    }
}

/**
 * A question number. Answered: filled with the accent; not answered: outline only; flagged: a
 * flag in the corner; the current one has a thick border. Three states, two colours.
 */
@Composable
private fun QuestionCell(index: Int, answered: Boolean, flagged: Boolean, current: Boolean, tag: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val status = buildList {
        add(stringResource(if (answered) R.string.exam_legend_answered else R.string.exam_legend_unanswered).lowercase())
        if (flagged) add(stringResource(R.string.exam_cell_flagged))
        if (current) add(stringResource(R.string.exam_cell_current))
    }.joinToString(", ")
    val description = stringResource(R.string.exam_cell_description, index + 1, status)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (answered) colors.primaryContainer else colors.surface,
        contentColor = if (answered) colors.onPrimaryContainer else colors.onSurface,
        border = when {
            current -> BorderStroke(2.dp, colors.primary)
            answered -> null
            else -> BorderStroke(1.dp, colors.outline)
        },
        modifier = Modifier
            .size(44.dp)
            .testTag(tag)
            .clearAndSetSemantics {
                contentDescription = description
                selected = current
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = if (current) FontWeight.Bold else null)
            if (flagged) {
                Icon(
                    N3Icons.Flagged,
                    contentDescription = null,
                    tint = colors.tertiary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuestionGridSheet(state: ExamSessionUiState, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = N3Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = N3Spacing.l),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
        ) {
            Text(stringResource(R.string.exam_all_questions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Legend()
            Caption(stringResource(R.string.exam_answered_count, state.answeredCount, state.questions.size))
            state.set.sections.forEach { section ->
                val indexed = state.questions.withIndex().filter { it.value.sectionId == section.id }
                if (indexed.isEmpty()) return@forEach
                Text(stringResource(section.kind.labelRes()), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    indexed.forEach { (index, q) ->
                        QuestionCell(index, q.id in state.answers, q.id in state.flagged, index == state.currentIndex, ExamSessionTags.sheetCell(index)) {
                            onSelect(index)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.l), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
        LegendItem(stringResource(R.string.exam_legend_answered)) {
            Surface(shape = MaterialTheme.shapes.extraSmall, color = colors.primaryContainer, modifier = Modifier.size(14.dp)) {}
        }
        LegendItem(stringResource(R.string.exam_legend_unanswered)) {
            Box(Modifier.size(14.dp).border(1.dp, colors.outline, MaterialTheme.shapes.extraSmall))
        }
        LegendItem(stringResource(R.string.exam_legend_flagged)) {
            Icon(N3Icons.Flagged, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
        swatch()
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuestionBody(
    state: ExamSessionUiState,
    question: ExamQuestion,
    onAnswer: (AnswerInput) -> Unit,
    onToggleFlag: () -> Unit,
) {
    val section = state.set.sections.firstOrNull { it.id == question.sectionId }
    val flagged = question.id in state.flagged
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.exam_question_position, state.currentIndex + 1, state.questions.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            section?.let { Caption(stringResource(it.kind.labelRes())) }
        }
        IconToggleButton(checked = flagged, onCheckedChange = { onToggleFlag() }) {
            Icon(
                if (flagged) N3Icons.Flagged else N3Icons.Unflagged,
                contentDescription = stringResource(if (flagged) R.string.exam_unflag else R.string.exam_flag),
                tint = if (flagged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val passage = question.passageId?.let { id -> state.set.passages.firstOrNull { it.id == id } }
    if (passage != null && (passage.text != null || passage.title != null)) {
        N3Card {
            passage.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            (passage.furigana ?: passage.text)?.let { FuriganaText(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
    if (passage?.audioPath != null || question.audioPath != null) {
        InfoBanner(stringResource(R.string.exam_audio_unavailable))
    }

    FuriganaText(question.contentFurigana ?: question.content, style = MaterialTheme.typography.titleLarge)

    val answer = state.answers[question.id]
    when (question.format) {
        AnswerFormat.CHOICE -> ChoiceOptions(question.options, answer?.selectedIndex) { onAnswer(AnswerInput.Choice(it)) }
        AnswerFormat.TEXT -> TextAnswer(question.id, answer?.textAnswer) { onAnswer(AnswerInput.Text(it)) }
    }
}

@Composable
private fun ChoiceOptions(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) colors.primaryContainer else colors.surface,
                contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.primary else colors.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ExamSessionTags.option(index))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) }),
            ) {
                Row(
                    Modifier.padding(horizontal = N3Spacing.m, vertical = N3Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(N3Spacing.s),
                ) {
                    // The row is the radio button; this one only shows the state.
                    RadioButton(selected = isSelected, onClick = null)
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                    FuriganaText(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TextAnswer(questionId: String, saved: String?, onSave: (String) -> Unit) {
    var draft by rememberSaveable(questionId) { mutableStateOf(saved.orEmpty()) }
    val save = { if (draft.isNotBlank()) onSave(draft.trim()) }
    Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.exam_text_answer_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            saved?.let { Caption(stringResource(R.string.exam_text_answer_saved, it), Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = save, enabled = draft.isNotBlank() && draft.trim() != saved) {
                Text(stringResource(R.string.exam_text_answer_save))
            }
        }
    }
}

@Composable
private fun SubmitDialog(state: ExamSessionUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val total = state.questions.size
    val unanswered = total - state.answeredCount
    val flagged = state.flagged.count { id -> state.questions.any { it.id == id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_submit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                Text(stringResource(R.string.exam_submit_answered, state.answeredCount, total))
                if (unanswered > 0) {
                    Text(
                        stringResource(R.string.exam_submit_unanswered, unanswered),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (flagged > 0) Text(stringResource(R.string.exam_submit_flagged, flagged))
                Text(stringResource(R.string.exam_submit_final))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.exam_submit_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.exam_submit_keep_going)) } },
    )
}

@Composable
private fun ExamFinished(state: ExamSessionUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val attempt = state.result?.attempt ?: state.attempt
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.l),
    ) {
        N3Card {
            val heading = when (state.status) {
                ExamSessionStatus.EXPIRED -> R.string.exam_result_expired
                ExamSessionStatus.ABANDONED -> R.string.exam_result_abandoned
                else -> R.string.exam_result_submitted
            }
            Text(stringResource(heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(state.set.title, style = MaterialTheme.typography.bodyMedium)
            if (attempt != null && state.status != ExamSessionStatus.ABANDONED) {
                Text(
                    stringResource(R.string.exam_result_score, attempt.correctCount ?: 0, attempt.questionCount),
                    style = MaterialTheme.typography.headlineMedium,
                )
                attempt.durationSeconds?.let { Text(stringResource(R.string.exam_result_time, examTimeSpent(it))) }
                val unanswered = attempt.questionCount - state.answers.size
                if (unanswered > 0) Text(stringResource(R.string.exam_result_unanswered, unanswered))
                if ((attempt.correctCount ?: 0) < attempt.questionCount) Caption(stringResource(R.string.exam_result_notebook))
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.exam_result_done)) }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ExamInProgressPreview() = N3SenseiTheme {
    ExamSessionContent(
        PreviewExam.inProgress, onAnswer = {}, onGoTo = {}, onPrevious = {}, onNext = {}, onToggleFlag = {}, onSubmit = {}, onDone = {},
    )
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ExamInProgressDarkPreview() = N3SenseiTheme(darkTheme = true) {
    Surface {
        ExamSessionContent(
            PreviewExam.inProgress.copy(remainingMillis = 240_000), onAnswer = {}, onGoTo = {}, onPrevious = {}, onNext = {},
            onToggleFlag = {}, onSubmit = {}, onDone = {},
        )
    }
}
