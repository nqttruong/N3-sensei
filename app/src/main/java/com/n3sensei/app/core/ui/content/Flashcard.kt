package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
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
    banner: String?,
    position: Int,
    total: Int,
    item: PracticeItem,
    isAnswerRevealed: Boolean,
    onReveal: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier,
    ratingHints: Map<ReviewRating, String> = emptyMap(),
    ratingEnabled: Boolean = true,
    /** Optional line under the progress, e.g. why the card is in the session. */
    subtitle: String? = null,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Everything above the buttons scrolls, so with large fonts on a small screen the card
        // is never squeezed to nothing and the answer buttons stay in place.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            banner?.let { InfoBanner(it) }
            Text(stringResource(R.string.practice_progress, position + 1, total), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { position.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ElevatedCard(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
        }
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
    Text(japaneseText(text), style = style.japanese(), textAlign = TextAlign.Center)
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
                Text(japaneseText(v.reading), style = MaterialTheme.typography.titleLarge.japanese(), textAlign = TextAlign.Center, modifier = center)
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
            Text(japaneseText(g.structure), style = MaterialTheme.typography.bodyLarge.japanese(), textAlign = TextAlign.Center, modifier = center)
        }
    }
}

/**
 * Four buttons in one row; two rows of two when a button would be narrower than its label
 * needs (large font scale or narrow screen), so labels like "Quên · 20 phút" are not cut.
 */
@Composable
private fun RatingButtons(onRate: (ReviewRating) -> Unit, hints: Map<ReviewRating, String>, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val perButton = (maxWidth - 24.dp) / 4
        val twoRows = perButton < 56.dp * fontScale
        val again: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.AGAIN, R.string.rating_again, colors.errorContainer, colors.onErrorContainer, hints, enabled, onRate) }
        val hard: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.HARD, R.string.rating_hard, colors.surfaceVariant, colors.onSurfaceVariant, hints, enabled, onRate) }
        val good: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.GOOD, R.string.rating_good, colors.primaryContainer, colors.onPrimaryContainer, hints, enabled, onRate) }
        val easy: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.EASY, R.string.rating_easy, colors.tertiaryContainer, colors.onTertiaryContainer, hints, enabled, onRate) }
        if (twoRows) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { again(); hard() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { good(); easy() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { again(); hard(); good(); easy() }
        }
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
