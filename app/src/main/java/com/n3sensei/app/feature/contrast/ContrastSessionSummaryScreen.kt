package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import kotlin.math.roundToInt

object SummaryTags {
    const val SCREEN = "contrast_summary"
    const val HOME = "contrast_summary_home"
    const val LIBRARY = "contrast_summary_library"
}

@Composable
fun ContrastSessionSummaryScreen(
    viewModel: ContrastSessionSummaryViewModel,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastSessionSummaryContent(state, onOpenHome, onOpenLibrary, viewModel::retry, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContrastSessionSummaryContent(
    state: UiState<ContrastSessionSummary>,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier, onRetry = onRetry) { data ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s).testTag(SummaryTags.SCREEN),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                Count(R.string.contrast_summary_correct, data.correct)
                Count(R.string.contrast_summary_incorrect, data.incorrect)
                Count(R.string.contrast_summary_skipped, data.skipped)
                if (data.pending > 0) Count(R.string.contrast_summary_pending, data.pending)
                if (data.unavailable > 0) Count(R.string.contrast_summary_unavailable, data.unavailable)
            }
            Text(stringResource(R.string.contrast_summary_handled, data.handled), style = MaterialTheme.typography.bodyLarge)
            val value = data.accuracy.value
            Text(
                if (value == null) {
                    stringResource(R.string.contrast_summary_accuracy_none)
                } else {
                    stringResource(R.string.contrast_summary_accuracy, (value * 100).roundToInt(), data.accuracy.correct, data.accuracy.answered)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            data.groups.forEach { group ->
                N3Card {
                    Text(group.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.contrast_progress_label, masteryLabel(group.mastery)), style = MaterialTheme.typography.bodyMedium)
                    group.nextDueAt?.let { Text(stringResource(R.string.contrast_summary_next_due, formatDate(it)), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (data.answers.isNotEmpty()) {
                SectionHeader(stringResource(R.string.contrast_summary_answers))
                data.answers.forEach { answer ->
                    N3Card {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                            Tag(qtype(answer.type))
                            Tag(outcomeLabel(answer.outcome), tone = if (answer.outcome == AnswerOutcome.CORRECT) TagTone.Accent else TagTone.Neutral)
                        }
                        Text(answer.groupTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        answer.chosenText?.let { Text(stringResource(R.string.contrast_summary_answer_chosen, it), style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.contrast_summary_answer_correct, answer.correctText), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = onOpenHome, modifier = Modifier.fillMaxWidth().testTag(SummaryTags.HOME)) { Text(stringResource(R.string.contrast_practice_back_home)) }
            OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth().testTag(SummaryTags.LIBRARY)) { Text(stringResource(R.string.contrast_summary_library)) }
        }
    }
}

@Composable
private fun Count(labelRes: Int, value: Int) {
    val label = stringResource(labelRes)
    N3Card(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun outcomeLabel(outcome: AnswerOutcome) = stringResource(
    when (outcome) {
        AnswerOutcome.CORRECT -> R.string.contrast_summary_correct
        AnswerOutcome.INCORRECT -> R.string.contrast_summary_incorrect
        AnswerOutcome.SKIPPED -> R.string.contrast_summary_skipped
    },
)

@Composable
private fun qtype(type: ContrastQuestionType) = stringResource(
    when (type) {
        ContrastQuestionType.MULTIPLE_CHOICE -> R.string.contrast_qtype_mc
        ContrastQuestionType.ERROR_DETECTION -> R.string.contrast_qtype_ed
        ContrastQuestionType.FILL_IN_THE_BLANK -> R.string.contrast_qtype_fill
        ContrastQuestionType.CONTEXT_SELECTION -> R.string.contrast_qtype_context
    },
)
