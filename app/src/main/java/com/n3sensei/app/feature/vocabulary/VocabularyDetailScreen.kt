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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.DetailSection
import com.n3sensei.app.core.designsystem.component.DetailText
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.content.ExamplesSection
import com.n3sensei.app.core.ui.content.ItemActions
import com.n3sensei.app.core.ui.content.KanjiCard
import com.n3sensei.app.core.ui.content.LocationSection
import com.n3sensei.app.core.ui.content.LocationTag
import com.n3sensei.app.core.ui.content.SourceNote
import com.n3sensei.app.core.ui.state.DetailLocation
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.model.VocabularyDetail

object VocabularyDetailTags {
    const val SAVED_NOTE = "vocabulary_saved_note"
}

@Composable
fun VocabularyDetailScreen(
    viewModel: VocabularyDetailViewModel,
    onPracticeLesson: (lessonId: String) -> Unit,
    onEdit: () -> Unit,
    onOpenKanji: (kanjiId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Optional entry point to Contrast Lab (shown only when a published group contains this item). */
    contrastLinks: @Composable () -> Unit = {},
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
        VocabularyDetailContent(detail, location, onOpenKanji) {
            if (detail.saved) {
                // Saved from "Dịch nhanh", not studied: no "Cần học lại" and no practice from here.
                Text(
                    stringResource(R.string.vocabulary_saved_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(VocabularyDetailTags.SAVED_NOTE),
                )
            } else {
                ItemActions(scheduleInfo, viewModel::toggleMark, onPracticeLesson = { onPracticeLesson(detail.vocabulary.lessonId) })
            }
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit_action)) }
            contrastLinks()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyDetailContent(detail: VocabularyDetail, location: DetailLocation?, onOpenKanji: (String) -> Unit, actions: @Composable () -> Unit) {
    val vocabulary = detail.vocabulary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Where it is studied, first, like the running head of a page.
        location?.let { LocationTag(it.location) }
        if (vocabulary.furigana != null) {
            FuriganaText(vocabulary.furigana, style = MaterialTheme.typography.displaySmall)
        } else {
            Text(japaneseText(vocabulary.expression), style = MaterialTheme.typography.displaySmall.japanese())
        }
        actions()
        // No reading for a word without one (an English or Vietnamese bookmark).
        if (vocabulary.reading.isNotBlank()) {
            DetailText(
                stringResource(R.string.vocabulary_reading),
                vocabulary.reading,
                style = MaterialTheme.typography.titleLarge.japanese(),
            )
        }
        DetailText(
            stringResource(R.string.vocabulary_meaning),
            vocabulary.meaningVi,
            style = MaterialTheme.typography.titleLarge,
        )
        vocabulary.partOfSpeech?.let { DetailText(stringResource(R.string.vocabulary_part_of_speech), it) }
        if (detail.translations.isNotEmpty()) {
            DetailSection(stringResource(R.string.vocabulary_translations)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.translations.forEach { t ->
                        val label = when (t.language) {
                            "en" -> stringResource(R.string.quick_translate_language_en)
                            "ja" -> stringResource(R.string.quick_translate_language_ja)
                            else -> stringResource(R.string.quick_translate_language_vi)
                        }
                        val reading = t.reading?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        Text(
                            japaneseText("$label: ${t.text}$reading"),
                            style = MaterialTheme.typography.bodyLarge.japanese(),
                        )
                    }
                }
            }
        }
        if (detail.tags.isNotEmpty()) {
            DetailSection(stringResource(R.string.vocabulary_tags)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Display-only labels; filtering by tag is not implemented.
                    detail.tags.forEach { tag -> SuggestionChip(onClick = {}, enabled = false, label = { Text(tag.name) }) }
                }
            }
        }
        vocabulary.note?.let { DetailText(stringResource(R.string.vocabulary_note), it) }
        // Under the theory; no section when the word has no example. The word is emphasized.
        ExamplesSection(stringResource(R.string.detail_examples), detail.examples) {
            ExampleHighlight.ofVocabulary(it.japanese, vocabulary.expression, vocabulary.reading)
        }
        if (detail.linkedKanji.isNotEmpty()) {
            DetailSection(stringResource(R.string.vocabulary_linked_kanji)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.linkedKanji.forEach { KanjiCard(it, onClick = { onOpenKanji(it.id) }) }
                }
            }
        }
        HorizontalDivider()
        location?.let { LocationSection(it) }
        SourceNote(detail.source)
    }
}
