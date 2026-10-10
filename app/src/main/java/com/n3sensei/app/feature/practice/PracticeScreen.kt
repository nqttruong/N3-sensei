package com.n3sensei.app.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.practice.PracticeSession

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    markedOnly: Boolean,
    onFinished: (PracticeResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(
        state = state,
        emptyMessage = stringResource(if (markedOnly) R.string.practice_marked_empty else R.string.practice_empty),
        notFoundMessage = stringResource(R.string.lesson_not_found),
        onRetry = viewModel::load,
        modifier = modifier,
    ) { ui ->
        val result = ui.result
        if (result != null) {
            LaunchedEffect(result) { onFinished(result) }
        } else {
            PracticeContent(
                session = ui.session,
                onReveal = viewModel::revealAnswer,
                onRate = viewModel::rate,
            )
        }
    }
}

@Composable
private fun PracticeContent(
    session: PracticeSession,
    onReveal: () -> Unit,
    onRate: (ReviewRating) -> Unit,
) {
    val item = session.current ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoBanner(stringResource(R.string.practice_not_saved))
        Text(
            stringResource(R.string.practice_progress, session.currentIndex + 1, session.total),
            style = MaterialTheme.typography.labelLarge,
        )
        LinearProgressIndicator(
            progress = { session.currentIndex.toFloat() / session.total },
            modifier = Modifier.fillMaxWidth(),
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(item.typeLabel()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                CardFront(item)
                if (session.isAnswerRevealed) {
                    HorizontalDivider()
                    CardBack(item)
                }
            }
        }
        if (session.isAnswerRevealed) {
            RatingButtons(onRate)
        } else {
            Button(onClick = onReveal, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.practice_reveal))
            }
        }
    }
}

private fun PracticeItem.typeLabel(): Int = when (this) {
    is PracticeItem.VocabularyItem -> R.string.practice_type_vocabulary
    is PracticeItem.KanjiItem -> R.string.practice_type_kanji
    is PracticeItem.GrammarItem -> R.string.practice_type_grammar
}

@Composable
private fun CardFront(item: PracticeItem) {
    val text = when (item) {
        is PracticeItem.VocabularyItem -> item.vocabulary.expression
        is PracticeItem.KanjiItem -> item.kanji.character
        is PracticeItem.GrammarItem -> item.grammar.pattern
    }
    val style = if (item is PracticeItem.KanjiItem) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall
    Text(text, style = style.japanese(), textAlign = TextAlign.Center)
}

@Composable
private fun CardBack(item: PracticeItem) {
    val center = Modifier.fillMaxWidth()
    when (item) {
        is PracticeItem.VocabularyItem -> {
            val v = item.vocabulary
            // Furigana already shows the reading; plain kana only when there is no markup.
            if (v.furigana != null && LocalShowFurigana.current) {
                FuriganaText(v.furigana, style = MaterialTheme.typography.headlineSmall)
            } else {
                Text(v.reading, style = MaterialTheme.typography.titleLarge.japanese(), textAlign = TextAlign.Center, modifier = center)
            }
            Text(v.meaningVi, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = center)
        }
        is PracticeItem.KanjiItem -> {
            val k = item.kanji
            k.hanViet?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
            Text(k.meaningVi, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = center)
            Text(
                stringResource(
                    R.string.kanji_readings_line,
                    k.onReadings.joinToString("、").ifEmpty { stringResource(R.string.common_none) },
                    k.kunReadings.joinToString("、").ifEmpty { stringResource(R.string.common_none) },
                ),
                style = MaterialTheme.typography.bodyLarge.japanese(),
                textAlign = TextAlign.Center,
                modifier = center,
            )
        }
        is PracticeItem.GrammarItem -> {
            val g = item.grammar
            Text(g.meaningVi, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = center)
            Text(g.structure, style = MaterialTheme.typography.bodyLarge.japanese(), textAlign = TextAlign.Center, modifier = center)
        }
    }
}

@Composable
private fun RatingButtons(onRate: (ReviewRating) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingButton(R.string.rating_again, colors.errorContainer, colors.onErrorContainer) { onRate(ReviewRating.AGAIN) }
        RatingButton(R.string.rating_hard, colors.surfaceVariant, colors.onSurfaceVariant) { onRate(ReviewRating.HARD) }
        RatingButton(R.string.rating_good, colors.primaryContainer, colors.onPrimaryContainer) { onRate(ReviewRating.GOOD) }
        RatingButton(R.string.rating_easy, colors.tertiaryContainer, colors.onTertiaryContainer) { onRate(ReviewRating.EASY) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RatingButton(
    labelRes: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        // Tight padding so four buttons fit on narrow screens with large font scale.
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
    ) { Text(stringResource(labelRes), textAlign = TextAlign.Center) }
}
