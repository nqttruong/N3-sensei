package com.n3sensei.app.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamType

@Composable
fun ExamIntroScreen(
    viewModel: ExamIntroViewModel,
    onStart: () -> Unit,
    onResume: (attemptId: String) -> Unit,
    onOpenResult: (attemptId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier) { data ->
        ExamIntroContent(data, onStart, onResume, viewModel::abandon, onOpenResult)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamIntroContent(
    state: ExamIntroUiState,
    onStart: () -> Unit,
    onResume: (attemptId: String) -> Unit,
    onAbandon: (attemptId: String) -> Unit,
    onOpenResult: (attemptId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = state.set
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(N3Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
                    Tag(set.jlptLevel, tone = TagTone.Accent)
                    if (set.type == ExamType.FULL) Tag(stringResource(R.string.exam_kind_full))
                }
                Text(set.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                set.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    stringResource(R.string.exam_intro_facts, stringResource(R.string.exam_questions_count, set.totalQuestions), examDuration(set.durationSeconds)),
                    style = MaterialTheme.typography.titleMedium,
                )
                val skills = set.questions.map(set::skillOf).toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    ExamSkill.entries.filter { it in skills }.forEach { Tag(stringResource(it.labelRes())) }
                }
                set.source?.let { Caption(it) }
                state.lastResult?.let { last ->
                    Caption(
                        listOf(
                            stringResource(R.string.exam_status_done, state.submittedAttempts),
                            stringResource(R.string.exam_last_score, last.correctCount ?: 0, last.questionCount),
                        ).joinToString(" · "),
                    )
                    TextButton(onClick = { onOpenResult(last.id) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.exam_intro_last_result))
                    }
                }
            }

            if (set.sections.size > 1) {
                SectionCard(stringResource(R.string.exam_intro_sections)) {
                    set.sections.forEach { section ->
                        val count = set.questions.count { it.sectionId == section.id }
                        Text(stringResource(R.string.exam_intro_section_row, stringResource(section.kind.labelRes()), count))
                    }
                }
            }

            SectionCard(stringResource(R.string.exam_intro_rules)) {
                val rules = buildList {
                    add(if (set.durationSeconds > 0) R.string.exam_rule_timer else R.string.exam_rule_untimed)
                    add(R.string.exam_rule_saved)
                    add(R.string.exam_rule_flag)
                    if (set.durationSeconds > 0) add(R.string.exam_rule_time_up)
                    add(R.string.exam_rule_final)
                }
                rules.forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }

        // Always on screen, under the rules: start, or continue the attempt left open.
        HorizontalDivider()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen, vertical = N3Spacing.m),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.xs),
        ) {
            val unfinished = state.unfinished
            if (unfinished != null) {
                state.unfinishedRemainingMillis?.let {
                    Text(stringResource(R.string.exam_resume_remaining, formatCountdown(it)), style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = { onResume(unfinished.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exam_resume))
                }
                TextButton(onClick = { confirmAbandon = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exam_abandon))
                }
            } else {
                Button(onClick = onStart, enabled = set.questions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exam_start))
                }
            }
        }
    }

    val pending = state.unfinished
    if (confirmAbandon && pending != null) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text(stringResource(R.string.exam_abandon_title)) },
            text = { Text(stringResource(R.string.exam_abandon_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbandon = false
                    onAbandon(pending.id)
                }) { Text(stringResource(R.string.exam_abandon_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamIntroPreview() = N3SenseiTheme {
    ExamIntroContent(
        ExamIntroUiState(PreviewExam.set, unfinished = null, unfinishedRemainingMillis = null, submittedAttempts = 0, lastResult = null),
        onStart = {}, onResume = {}, onAbandon = {}, onOpenResult = {},
    )
}
