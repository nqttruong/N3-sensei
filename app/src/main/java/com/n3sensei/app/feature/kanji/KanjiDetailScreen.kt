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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.n3sensei.app.domain.model.KanjiDetail

@Composable
fun KanjiDetailScreen(
    viewModel: KanjiDetailViewModel,
    onPracticeLesson: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheduleInfo by viewModel.scheduleInfo.collectAsStateWithLifecycle()
    UiStateContent(state = state, onRetry = viewModel::refresh, modifier = modifier) { detail ->
        KanjiDetailContent(detail) {
            ItemActions(scheduleInfo, viewModel::toggleMark, onPracticeLesson = { onPracticeLesson(detail.kanji.lessonId) })
        }
    }
}

@Composable
private fun KanjiDetailContent(detail: KanjiDetail, actions: @Composable () -> Unit) {
    val kanji = detail.kanji
    val none = stringResource(R.string.common_none)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(112.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(kanji.character, style = MaterialTheme.typography.displayLarge.japanese())
                }
            }
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
