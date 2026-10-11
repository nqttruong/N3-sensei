package com.n3sensei.app.feature.grammar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.DetailSection
import com.n3sensei.app.core.designsystem.component.DetailText
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.content.ExampleSentenceItem
import com.n3sensei.app.core.ui.content.ItemActions
import com.n3sensei.app.core.ui.content.SourceNote
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.GrammarDetail

@Composable
fun GrammarDetailScreen(
    viewModel: GrammarDetailViewModel,
    onPracticeLesson: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheduleInfo by viewModel.scheduleInfo.collectAsStateWithLifecycle()
    UiStateContent(state = state, onRetry = viewModel::refresh, modifier = modifier) { detail ->
        GrammarDetailContent(detail) {
            ItemActions(scheduleInfo, viewModel::toggleMark, onPracticeLesson = { onPracticeLesson(detail.grammar.lessonId) })
        }
    }
}

@Composable
private fun GrammarDetailContent(detail: GrammarDetail, actions: @Composable () -> Unit) {
    val grammar = detail.grammar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(grammar.pattern, style = MaterialTheme.typography.headlineMedium.japanese())
        actions()
        DetailText(
            stringResource(R.string.grammar_structure),
            grammar.structure,
            style = MaterialTheme.typography.titleMedium.japanese(),
        )
        DetailText(stringResource(R.string.grammar_meaning), grammar.meaningVi, style = MaterialTheme.typography.titleMedium)
        if (grammar.usage.isNotBlank()) DetailText(stringResource(R.string.grammar_usage), grammar.usage)
        grammar.cautionNote?.let { caution ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DetailText(stringResource(R.string.grammar_caution), caution, modifier = Modifier.padding(12.dp))
            }
        }
        DetailSection(stringResource(R.string.detail_examples)) {
            if (detail.examples.isEmpty()) {
                Text(stringResource(R.string.detail_no_examples), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail.examples.forEach { ExampleSentenceItem(it) }
        }
        if (detail.exercises.isNotEmpty()) {
            DetailSection(stringResource(R.string.grammar_exercises)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    detail.exercises.forEach { GrammarExerciseCard(it) }
                    Text(
                        stringResource(R.string.grammar_exercise_source_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
        SourceNote(detail.source)
    }
}
