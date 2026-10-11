package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.practice.PracticeItem

/**
 * One flashcard of a session (practice or scheduled review): progress, card front/back and
 * the reveal / rating buttons.
 *
 * @param ratingHints optional second line per rating, e.g. the next interval.
 * @param ratingEnabled false while an answer is being saved, to block double taps.
 */
@Composable
fun FlashcardSession(
    banner: String,
    position: Int,
    total: Int,
    item: PracticeItem,
    isAnswerRevealed: Boolean,
    onReveal: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier,
    ratingHints: Map<ReviewRating, String> = emptyMap(),
    ratingEnabled: Boolean = true,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoBanner(banner)
        Text(stringResource(R.string.practice_progress, position + 1, total), style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { position.toFloat() / total }, modifier = Modifier.fillMaxWidth())
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
                if (isAnswerRevealed) {
                    HorizontalDivider()
                    CardBack(item)
                }
            }
        }
        footer()
        if (isAnswerRevealed) {
            RatingButtons(onRate, ratingHints, ratingEnabled)
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
private fun RatingButtons(onRate: (ReviewRating) -> Unit, hints: Map<ReviewRating, String>, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingButton(ReviewRating.AGAIN, R.string.rating_again, colors.errorContainer, colors.onErrorContainer, hints, enabled, onRate)
        RatingButton(ReviewRating.HARD, R.string.rating_hard, colors.surfaceVariant, colors.onSurfaceVariant, hints, enabled, onRate)
        RatingButton(ReviewRating.GOOD, R.string.rating_good, colors.primaryContainer, colors.onPrimaryContainer, hints, enabled, onRate)
        RatingButton(ReviewRating.EASY, R.string.rating_easy, colors.tertiaryContainer, colors.onTertiaryContainer, hints, enabled, onRate)
    }
}

@Composable
private fun RowScope.RatingButton(
    rating: ReviewRating,
    labelRes: Int,
    container: Color,
    content: Color,
    hints: Map<ReviewRating, String>,
    enabled: Boolean,
    onRate: (ReviewRating) -> Unit,
) {
    val label = stringResource(labelRes)
    val hint = hints[rating]
    Button(
        onClick = { onRate(rating) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        // Tight padding so four buttons fit on narrow screens with large font scale.
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
    ) {
        Text(if (hint != null) stringResource(R.string.rating_with_interval, label, hint) else label, textAlign = TextAlign.Center)
    }
}
