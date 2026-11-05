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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.DetailSection
import com.n3sensei.app.core.designsystem.component.DetailText
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.content.ExamplesSection
import com.n3sensei.app.core.ui.content.ItemActions
import com.n3sensei.app.core.ui.content.LocationSection
import com.n3sensei.app.core.ui.content.LocationTag
import com.n3sensei.app.core.ui.content.SourceNote
import com.n3sensei.app.core.ui.state.DetailLocation
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.model.GrammarDetail

@Composable
fun GrammarDetailScreen(
    viewModel: GrammarDetailViewModel,
    onPracticeLesson: (lessonId: String) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from the edit screen: show the saved values.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    val scheduleInfo by viewModel.scheduleInfo.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    UiStateContent(state = state, onRetry = viewModel::refresh, modifier = modifier) { detail ->
        GrammarDetailContent(detail, location) {
            ItemActions(scheduleInfo, viewModel::toggleMark, onPracticeLesson = { onPracticeLesson(detail.grammar.lessonId) })
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit_action)) }
        }
    }
}

@Composable
private fun GrammarDetailContent(detail: GrammarDetail, location: DetailLocation?, actions: @Composable () -> Unit) {
    val grammar = detail.grammar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Where it is studied, first, like the running head of a page.
        location?.let { LocationTag(it.location) }
        Text(japaneseText(grammar.pattern), style = MaterialTheme.typography.headlineMedium.japanese())
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
        // Under the theory; no section when the point has no example. The pattern is emphasized.
        ExamplesSection(stringResource(R.string.detail_examples), detail.examples) {
            ExampleHighlight.ofGrammar(it.japanese, grammar.pattern)
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
        location?.let { LocationSection(it) }
        SourceNote(detail.source)
    }
}
