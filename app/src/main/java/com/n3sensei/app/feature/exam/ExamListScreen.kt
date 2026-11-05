package com.n3sensei.app.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.ExamSetSummary
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamType

object ExamListTags {
    const val LEVELS = "exam_filter_levels"
    const val KINDS = "exam_filter_kinds"
}

@Composable
fun ExamListScreen(
    viewModel: ExamListViewModel,
    onOpenExam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier) { data ->
        ExamListContent(data, viewModel::selectLevel, viewModel::selectKind, onOpenExam)
    }
}

@Composable
fun ExamListContent(
    state: ExamListUiState,
    onSelectLevel: (String?) -> Unit,
    onSelectKind: (ExamKindFilter) -> Unit,
    onOpenExam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (state.totalSets == 0) {
            MessageContent(stringResource(R.string.exam_list_empty))
            return@Column
        }
        val all = stringResource(R.string.common_all)
        FilterChipRow(
            options = listOf(FilterOption<String?>(null, all)) + ExamListViewModel.LEVELS.map { FilterOption(it, it) },
            selected = state.level,
            onSelect = onSelectLevel,
            modifier = Modifier.padding(top = N3Spacing.s).testTag(ExamListTags.LEVELS),
        )
        FilterChipRow(
            options = ExamKindFilter.entries.map { FilterOption(it, kindLabel(it, all)) },
            selected = state.kind,
            onSelect = onSelectKind,
            modifier = Modifier.padding(vertical = N3Spacing.s).testTag(ExamListTags.KINDS),
        )
        if (state.sets.isEmpty()) {
            MessageContent(stringResource(R.string.exam_list_empty_filter))
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = N3Spacing.screen, end = N3Spacing.screen, bottom = N3Spacing.l),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
        ) {
            items(state.sets, key = { it.id }) { set -> ExamSetCard(set, onClick = { onOpenExam(set.id) }) }
        }
    }
}

@Composable
private fun kindLabel(kind: ExamKindFilter, all: String): String = when {
    kind == ExamKindFilter.ALL -> all
    kind == ExamKindFilter.FULL -> stringResource(R.string.exam_kind_full)
    else -> stringResource(kind.skill!!.labelRes())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamSetCard(set: ExamSetSummary, onClick: () -> Unit) {
    N3Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            Tag(set.jlptLevel, tone = TagTone.Accent)
            if (set.type == ExamType.FULL) Tag(stringResource(R.string.exam_kind_full))
            Text(
                set.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.exam_intro_facts, stringResource(R.string.exam_questions_count, set.totalQuestions), examDuration(set.durationSeconds)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (set.skills.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                ExamSkill.entries.filter { it in set.skills }.forEach { Tag(stringResource(it.labelRes())) }
            }
        }
        val status = when {
            set.inProgressAttempts > 0 -> stringResource(R.string.exam_status_in_progress)
            set.submittedAttempts > 0 -> stringResource(R.string.exam_status_done, set.submittedAttempts)
            else -> stringResource(R.string.exam_status_new)
        }
        val score = set.lastCorrect?.let { stringResource(R.string.exam_last_score, it, set.totalQuestions) }
        Caption(listOfNotNull(status, score).joinToString(" · "))
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamListPreview() = N3SenseiTheme {
    ExamListContent(
        ExamListUiState(
            level = null, kind = ExamKindFilter.ALL, totalSets = 2,
            sets = listOf(
                ExamSetSummary("a", "Đề luyện số 1", "N3", ExamType.FULL, 6_000, 60, 2, 41, setOf(ExamSkill.KANJI, ExamSkill.READING)),
                ExamSetSummary("b", "Nghe hiểu 1", "N3", ExamType.SECTION, 2_400, 20, 0, null, setOf(ExamSkill.LISTENING), 1),
            ),
        ),
        onSelectLevel = {}, onSelectKind = {}, onOpenExam = {},
    )
}
