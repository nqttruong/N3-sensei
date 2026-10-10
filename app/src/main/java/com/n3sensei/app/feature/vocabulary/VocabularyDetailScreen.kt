package com.n3sensei.app.feature.vocabulary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.content.ExampleSentenceItem
import com.n3sensei.app.core.ui.content.SourceNote
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.VocabularyDetail

@Composable
fun VocabularyDetailScreen(
    viewModel: VocabularyDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state = state, onRetry = viewModel::refresh, modifier = modifier) { detail ->
        VocabularyDetailContent(detail)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyDetailContent(detail: VocabularyDetail) {
    val vocabulary = detail.vocabulary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vocabulary.furigana != null) {
            FuriganaText(vocabulary.furigana, style = MaterialTheme.typography.displaySmall)
        } else {
            Text(vocabulary.expression, style = MaterialTheme.typography.displaySmall.japanese())
        }
        DetailText(
            stringResource(R.string.vocabulary_reading),
            vocabulary.reading,
            style = MaterialTheme.typography.titleLarge.japanese(),
        )
        DetailText(
            stringResource(R.string.vocabulary_meaning),
            vocabulary.meaningVi,
            style = MaterialTheme.typography.titleLarge,
        )
        vocabulary.partOfSpeech?.let { DetailText(stringResource(R.string.vocabulary_part_of_speech), it) }
        if (detail.tags.isNotEmpty()) {
            DetailSection(stringResource(R.string.vocabulary_tags)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Display-only labels; filtering by tag is not implemented.
                    detail.tags.forEach { tag -> SuggestionChip(onClick = {}, enabled = false, label = { Text(tag.name) }) }
                }
            }
        }
        vocabulary.note?.let { DetailText(stringResource(R.string.vocabulary_note), it) }
        DetailSection(stringResource(R.string.detail_examples)) {
            if (detail.examples.isEmpty()) {
                Text(stringResource(R.string.detail_no_examples), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail.examples.forEach { ExampleSentenceItem(it) }
        }
        HorizontalDivider()
        SourceNote(detail.source)
    }
}
