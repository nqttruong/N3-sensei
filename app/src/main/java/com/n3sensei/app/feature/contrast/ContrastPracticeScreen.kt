package com.n3sensei.app.feature.contrast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.theme.LocalReduceMotion
import com.n3sensei.app.core.designsystem.theme.N3Motion
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
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
    val reduce = LocalReduceMotion.current
    Column(Modifier.fillMaxSize()) {
        PracticeHeader(item, actions.exit)
        // The next question slides in; the previous one (and its answer) leaves with it.
        AnimatedContent(
            targetState = item,
            contentKey = { "${it.question.id}#${it.done}" },
            transitionSpec = {
                if (reduce) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    (fadeIn(tween(N3Motion.LONG, easing = N3Motion.EnterEasing)) + slideInHorizontally(tween(N3Motion.LONG, easing = N3Motion.EnterEasing)) { it / 8 }) togetherWith
                        (fadeOut(tween(N3Motion.SHORT)) + slideOutHorizontally(tween(N3Motion.SHORT)) { -it / 12 })
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "question",
        ) { shown ->
            // Selection and feedback always belong to the question on screen; during the swap the old one shows neither.
            val current = shown.question.id == question.id && shown.done == item.done
            val shownSelected = if (current) selected else null
            val shownFeedback = if (current) feedback else null
            val scroll = rememberScrollState()
            // The explanation appears under the options: bring it into view (instantly with reduced motion).
            LaunchedEffect(shownFeedback != null) {
                if (shownFeedback != null) {
                    kotlinx.coroutines.delay(if (reduce) 0 else N3Motion.MEDIUM.toLong())
                    if (reduce) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue, tween(N3Motion.LONG, easing = N3Motion.StandardEasing))
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TonePill(questionTypeLabel(shown.question.type), ContrastTones.practice())
                Text(shown.question.instructionVi, style = ContrastText.question, modifier = Modifier.semantics { heading() })
                if (current && contentChanged) InfoBanner(stringResource(R.string.contrast_practice_content_changed))
                val enabled = current && canSelect
                when (shown.question.type) {
                    ContrastQuestionType.MULTIPLE_CHOICE -> MultipleChoiceExercise(shown.question, shownSelected, shownFeedback, enabled, actions.select)
                    ContrastQuestionType.FILL_IN_THE_BLANK -> FillBlankExercise(shown.question, shownSelected, shownFeedback, enabled, actions.select)
                    ContrastQuestionType.ERROR_DETECTION -> ErrorDetectionExercise(shown.question, shownSelected, shownFeedback, enabled, actions.select)
                    ContrastQuestionType.CONTEXT_SELECTION -> ContextSelectionExercise(shown.question, shownSelected, shownFeedback, enabled, actions.select)
                }
                AnimatedVisibility(
                    visible = shownFeedback != null,
                    enter = if (reduce) fadeIn(snap()) else fadeIn(tween(N3Motion.MEDIUM)) + expandVertically(tween(N3Motion.MEDIUM, easing = N3Motion.EnterEasing)),
                    exit = fadeOut(snap()),
                ) {
                    shownFeedback?.let { FeedbackPanel(shown, it) }
                }
            }
        }
        PracticeActionsBar(selected, feedback, submitting, saveFailed, actions)
    }
}

@Composable
private fun PracticeHeader(item: PracticeQuestion, onExit: () -> Unit) {
    val tone = ContrastTones.practice()
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(japaneseText(item.groupTitle), style = ContrastText.label.copy(fontSize = 16.sp).japanese(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.contrast_practice_progress, (item.done + 1).coerceAtMost(item.total), item.total),
                    style = ContrastText.caption,
                    color = tone.accent,
                )
            }
            TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp).testTag(PracticeTags.EXIT)) {
                Text(stringResource(R.string.contrast_practice_exit), style = ContrastText.label.copy(fontSize = 15.sp), color = tone.accent)
            }
        }
        ToneBar(if (item.total == 0) 0f else item.done.toFloat() / item.total, tone, Modifier.padding(end = 8.dp))
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
private fun PromptCard(content: @Composable ColumnScope.() -> Unit) {
    ContrastCard(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun MultipleChoiceExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    question.prompt?.let { prompt ->
        PromptCard {
            FuriganaText(prompt.furigana ?: prompt.japanese, style = ContrastText.exampleJp)
            if (feedback != null) prompt.translationVi?.let { Text(it, style = ContrastText.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    OptionList(question.options, selected, feedback, enabled, onSelect)
}

/** The blank shows the chosen form in place, highlighted; options are the forms of the pack (no typed answers in the contract). */
@Composable
private fun FillBlankExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    val prompt = question.prompt
    if (prompt != null) {
        PromptCard {
            val markup = prompt.furigana ?: prompt.japanese
            val shown = (feedback?.correctOptionId ?: selected)?.let(question::option)
            val blankAt = runCatching { Furigana.baseText(markup).indexOf(BLANK) }.getOrDefault(-1)
            val highlightColor = if (feedback != null) ContrastTones.correct().accent else ContrastTones.practice().accent
            if (shown != null && blankAt >= 0) {
                val inserted = shown.furigana ?: shown.text
                val filled = markup.replaceFirst(BLANK, inserted)
                val length = runCatching { Furigana.baseText(inserted).length }.getOrDefault(shown.text.length)
                FuriganaText(filled, style = ContrastText.exampleJp, highlight = listOf(blankAt until blankAt + length), highlightColor = highlightColor)
            } else {
                FuriganaText(markup, style = ContrastText.exampleJp)
            }
            if (feedback != null) prompt.translationVi?.let { Text(it, style = ContrastText.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    OptionList(question.options, selected, feedback, enabled, onSelect)
}

/** Segments of the sentence as the pack defines them (option ids): the learner taps the wrong one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorDetectionExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    val practice = ContrastTones.practice()
    val wrong = ContrastTones.incorrect()
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        question.options.forEachIndexed { index, segment ->
            val isSelected = segment.id == selected
            val isWrongSegment = feedback != null && segment.id == feedback.correctOptionId
            val tone = when {
                isWrongSegment -> wrong
                isSelected -> practice
                else -> null
            }
            val colors = MaterialTheme.colorScheme
            val description = stringResource(R.string.contrast_segment_description, index + 1, segment.text)
            val marker = when {
                isWrongSegment -> stringResource(R.string.contrast_option_correct)
                feedback != null && isSelected -> stringResource(R.string.contrast_option_chosen)
                else -> null
            }
            val scale by animateFloatAsState(if (isSelected && feedback == null && !LocalReduceMotion.current) 1.04f else 1f, spring(stiffness = 500f), label = "segment")
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (tone != null) 2.dp else 1.dp, tone?.accent ?: colors.outlineVariant),
                color = tone?.container ?: colors.surfaceContainerLow,
                modifier = Modifier
                    .scale(scale)
                    .heightIn(min = 56.dp)
                    .testTag(PracticeTags.segment(segment.id))
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(segment.id) })
                    .semantics { contentDescription = description; marker?.let { stateDescription = it } },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    FuriganaText(segment.furigana ?: segment.text, style = ContrastText.answer.copy(fontSize = 22.sp, lineHeight = 32.sp), color = tone?.onContainer ?: Color.Unspecified)
                    marker?.let { Text(it, style = ContrastText.label.copy(fontSize = 13.sp), color = tone?.accent ?: colors.onSurfaceVariant) }
                }
            }
        }
    }
    if (feedback != null) question.prompt?.translationVi?.let { Text(it, style = ContrastText.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** The situation (and its Japanese lines, kept as written), then whole sentences to choose from. */
@Composable
private fun ContextSelectionExercise(question: ContrastQuestion, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    question.context?.let { context ->
        ContrastCard(tone = ContrastTones.review(), modifier = Modifier.testTag(PracticeTags.CONTEXT)) {
            Text(stringResource(R.string.contrast_ui_situation), style = ContrastText.label, color = ContrastTones.review().accent)
            Text(context.descriptionVi, style = ContrastText.body)
            context.japanese?.let { japanese ->
                val lines = (context.furigana ?: japanese).split('\n')
                lines.forEach { line -> if (line.isNotBlank()) FuriganaText(line, style = ContrastText.exampleJp.copy(fontSize = 20.sp, lineHeight = 32.sp)) }
            }
        }
    }
    OptionList(question.options, selected, feedback, enabled, onSelect)
}

/** True for a short option made of kanji only (e.g. 鳥, 成績): shown large so the shapes can be compared. */
private fun isKanjiOption(option: ContrastOption): Boolean =
    option.text.length <= 3 && option.text.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

/**
 * Single-choice answer cards. Selecting one tints and outlines it with a tiny scale; after the answer
 * each card says in words and with an icon (not only colour) whether it was the answer or the choice.
 */
@Composable
private fun OptionList(options: List<ContrastOption>, selected: String?, feedback: PracticeFeedback?, enabled: Boolean, onSelect: (String) -> Unit) {
    val practice = ContrastTones.practice()
    val right = ContrastTones.correct()
    val wrong = ContrastTones.incorrect()
    val reduce = LocalReduceMotion.current
    val kanji = options.all(::isKanjiOption)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEachIndexed { index, option ->
            val isSelected = option.id == selected
            val isCorrect = feedback != null && option.id == feedback.correctOptionId
            val isWrongChoice = feedback != null && isSelected && !isCorrect
            val tone = when {
                isCorrect -> right
                isWrongChoice -> wrong
                isSelected -> practice
                else -> null
            }
            val colors = MaterialTheme.colorScheme
            val marker = when {
                isCorrect -> stringResource(R.string.contrast_option_correct)
                isWrongChoice -> stringResource(R.string.contrast_option_chosen)
                else -> null
            }
            val scale by animateFloatAsState(if (isSelected && feedback == null && !reduce) 1.02f else 1f, spring(stiffness = 500f), label = "option")
            val container by animateColorAsState(tone?.container ?: colors.surfaceContainerLow, if (reduce) snap() else tween(N3Motion.MEDIUM), label = "optionBg")
            val dim = feedback != null && tone == null
            Surface(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(if (tone != null) 2.dp else 1.dp, tone?.accent ?: colors.outlineVariant),
                color = container,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .heightIn(min = 64.dp)
                    .testTag(PracticeTags.option(option.id))
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option.id) })
                    .semantics { marker?.let { stateDescription = it } },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(tone?.accent ?: colors.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isCorrect -> Icon(Icons.Filled.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(20.dp))
                            isWrongChoice -> Icon(Icons.Filled.Close, contentDescription = null, tint = colors.surface, modifier = Modifier.size(20.dp))
                            else -> Text(('A' + index).toString(), style = ContrastText.label, color = if (tone != null) colors.surface else colors.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FuriganaText(
                            option.furigana ?: option.text,
                            style = if (kanji) ContrastText.answerKanji else ContrastText.answer,
                            color = when {
                                tone != null -> tone.onContainer
                                dim -> colors.onSurfaceVariant
                                else -> Color.Unspecified
                            },
                        )
                        marker?.let { Text(it, style = ContrastText.label.copy(fontSize = 13.sp), color = tone?.accent ?: colors.onSurfaceVariant) }
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
    val (icon, title, tone) = when (feedback.outcome) {
        AnswerOutcome.CORRECT -> Triple(Icons.Filled.Check, stringResource(R.string.contrast_feedback_correct), ContrastTones.correct())
        AnswerOutcome.INCORRECT -> Triple(Icons.Filled.Close, stringResource(R.string.contrast_feedback_incorrect), ContrastTones.incorrect())
        AnswerOutcome.SKIPPED -> Triple(Icons.Filled.Info, stringResource(R.string.contrast_feedback_skipped), ContrastTones.neutral())
    }
    ContrastCard(tone = tone, modifier = Modifier.testTag(PracticeTags.FEEDBACK).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tone.accent), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
            }
            Text(title, style = ContrastText.section.copy(fontSize = 22.sp, lineHeight = 30.sp), color = tone.accent, modifier = Modifier.semantics { heading() })
        }
        val correct = question.option(feedback.correctOptionId)
        if (correct != null) Text(japaneseText(stringResource(R.string.contrast_feedback_answer, correct.text)), style = ContrastText.answer.copy(fontSize = 19.sp))
        if (question.type == ContrastQuestionType.ERROR_DETECTION) {
            question.correction?.let { Text(japaneseText(stringResource(R.string.contrast_feedback_correction, it.text)), style = ContrastText.answer.copy(fontSize = 19.sp)) }
        }
        Text(feedback.explanationVi, style = ContrastText.body)
        feedback.chosenFeedbackVi?.let { Text(it, style = ContrastText.body, color = tone.onContainer.copy(alpha = 0.85f)) }
        feedback.confusion?.let { (expected, chosen) ->
            Text(
                stringResource(R.string.contrast_feedback_confusion, item.itemLabels[expected] ?: expected, item.itemLabels[chosen] ?: chosen),
                style = ContrastText.body,
            )
        }
        if (feedback.retryAdded) Text(stringResource(R.string.contrast_feedback_retry_added), style = ContrastText.caption)
        feedback.nextDueAt?.let { Text(stringResource(R.string.contrast_feedback_next_due, formatDate(it)), style = ContrastText.caption) }
    }
}

@Composable
private fun PracticeActionsBar(selected: String?, feedback: PracticeFeedback?, submitting: Boolean, saveFailed: Boolean, actions: PracticeActions) {
    val practice = ContrastTones.practice()
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saveFailed) {
                Text(stringResource(R.string.contrast_practice_save_failed), style = ContrastText.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            when {
                feedback != null -> GradientButton(
                    stringResource(if (feedback.sessionCompleted) R.string.contrast_practice_finish else R.string.contrast_practice_next),
                    if (feedback.outcome == AnswerOutcome.CORRECT) ContrastTones.correct() else practice,
                    actions.next,
                    Modifier.testTag(PracticeTags.NEXT),
                )
                submitting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(min = 56.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = practice.accent)
                    Text(stringResource(R.string.contrast_practice_saving), style = ContrastText.body)
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = actions.skip,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, practice.accent.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(PracticeTags.SKIP),
                    ) { Text(stringResource(R.string.contrast_practice_skip), style = ContrastText.label.copy(fontSize = 17.sp), color = practice.accent) }
                    GradientButton(
                        stringResource(R.string.contrast_practice_check),
                        practice,
                        actions.submit,
                        Modifier.weight(1.4f).testTag(PracticeTags.CHECK),
                        enabled = selected != null,
                    )
                }
            }
        }
    }
}

private const val BLANK = "＿＿＿"

private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatDate(instant: Instant): String = DATE.format(instant.atZone(ZoneId.systemDefault()))
