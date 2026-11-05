package com.n3sensei.app.feature.kanji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.DetailSection
import com.n3sensei.app.core.designsystem.component.DetailText
import com.n3sensei.app.core.designsystem.component.GlyphSize
import com.n3sensei.app.core.designsystem.component.GlyphTile
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.content.ExamplesSection
import com.n3sensei.app.core.ui.content.ItemActions
import com.n3sensei.app.core.ui.content.LocationSection
import com.n3sensei.app.core.ui.content.LocationTag
import com.n3sensei.app.core.ui.content.SourceNote
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.core.ui.state.DetailLocation
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.model.KanjiDetail

@Composable
fun KanjiDetailScreen(
    viewModel: KanjiDetailViewModel,
    onPracticeLesson: (lessonId: String) -> Unit,
    onEdit: () -> Unit,
    onOpenVocabulary: (vocabularyId: String) -> Unit,
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
        KanjiDetailContent(detail, location, onOpenVocabulary) {
            ItemActions(scheduleInfo, viewModel::toggleMark, onPracticeLesson = { onPracticeLesson(detail.kanji.lessonId) })
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit_action)) }
        }
    }
}

@Composable
private fun KanjiDetailContent(detail: KanjiDetail, location: DetailLocation?, onOpenVocabulary: (String) -> Unit, actions: @Composable () -> Unit) {
    val kanji = detail.kanji
    val none = stringResource(R.string.common_none)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Where it is studied, first, like the running head of a page.
        location?.let { LocationTag(it.location) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphTile(kanji.character, size = GlyphSize.Large)
            Column {
                kanji.hanViet?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
                Text(kanji.meaningVi, style = MaterialTheme.typography.titleMedium)
                kanji.strokeCount?.let {
                    Text(
                        stringResource(R.string.kanji_strokes_value, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        actions()
        val readingStyle = MaterialTheme.typography.titleMedium.japanese()
        DetailText(stringResource(R.string.kanji_on), kanji.onReadings.joinToString("、").ifEmpty { none }, style = readingStyle)
        DetailText(stringResource(R.string.kanji_kun), kanji.kunReadings.joinToString("、").ifEmpty { none }, style = readingStyle)
        kanji.note?.let { DetailText(stringResource(R.string.vocabulary_note), it) }
        Text(
            stringResource(R.string.kanji_stroke_order_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The kanji's own example words (its group in N3.pdf); not vocabulary items.
        // No section when the kanji has no word. The kanji is emphasized in each word.
        ExamplesSection(stringResource(R.string.kanji_example_words), detail.examples) {
            ExampleHighlight.ofKanji(it.japanese, kanji.character)
        }
        // Vocabulary items the learner linked to this kanji: separate from its example words.
        if (detail.linkedVocabulary.isNotEmpty()) {
            DetailSection(stringResource(R.string.kanji_linked_words)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.linkedVocabulary.forEach { VocabularyCard(it, onClick = { onOpenVocabulary(it.id) }) }
                }
            }
        }
        HorizontalDivider()
        location?.let { LocationSection(it) }
        SourceNote(detail.source)
    }
}
