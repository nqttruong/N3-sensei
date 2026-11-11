package com.n3sensei.app.core.ui.content

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.LocalReduceMotion
import com.n3sensei.app.core.designsystem.theme.N3Motion
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.model.KanjiWord
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
    val reduceMotion = LocalReduceMotion.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SessionProgress(position, total)
        // Each card slides in a little from the side so "next card" is felt without reading the
        // counter; the answer then unfolds under the question. Both are instant when the system
        // asks for reduced motion.
        AnimatedContent(
            targetState = CardFace(position, item, isAnswerRevealed),
            contentKey = { it.position },
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(N3Motion.MEDIUM, easing = N3Motion.EnterEasing)) +
                        slideInHorizontally(tween(N3Motion.MEDIUM, easing = N3Motion.EnterEasing)) { it / 8 }) togetherWith
                        fadeOut(tween(N3Motion.SHORT))
                }
            },
            label = "card",
            modifier = Modifier.weight(1f),
        ) { face ->
            // Revealing turns the card over (about 300 ms): the first half shows the question
            // side, the second half the answer side, so nothing of the answer shows before.
            val turn = remember { Animatable(if (face.revealed) 180f else 0f) }
            LaunchedEffect(face.revealed) {
                val target = if (face.revealed) 180f else 0f
                if (reduceMotion) turn.snapTo(target) else turn.animateTo(target, tween(FLIP_MILLIS, easing = N3Motion.StandardEasing))
            }
            val showBack = turn.value >= 90f
            // Everything above the buttons scrolls, so with large fonts on a small screen the
            // card is never squeezed to nothing and the answer buttons stay in place. A new card
            // starts at its top.
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                banner?.let { InfoBanner(it) }
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                N3Card(
                    modifier = Modifier
                        .heightIn(min = 240.dp)
                        .graphicsLayer {
                            rotationY = if (showBack) turn.value - 180f else turn.value
                            cameraDistance = 16 * density
                        },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Tag(stringResource(face.item.typeLabel()), tone = TagTone.Accent)
                        CardFront(face.item, revealed = showBack)
                        if (showBack) {
                            // A short rule between question and answer, as on a printed card.
                            HorizontalDivider(Modifier.width(64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            CardBack(face.item)
                        }
                        if (face.item is PracticeItem.KanjiItem) KanjiWords(face.item.words, revealed = showBack)
                    }
                }
                footer()
            }
        }
        AnimatedContent(
            targetState = isAnswerRevealed,
            transitionSpec = {
                if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(tween(N3Motion.SHORT)) togetherWith fadeOut(tween(N3Motion.SHORT))
            },
            label = "actions",
        ) { revealed ->
            if (revealed) {
                RatingButtons(onRate, ratingHints, ratingEnabled)
            } else {
                Button(onClick = onReveal, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.practice_reveal))
                }
            }
        }
    }
}

/** What one card shows; the position identifies the card across recompositions. */
private data class CardFace(val position: Int, val item: PracticeItem, val revealed: Boolean)

/** "Thẻ 3/20" with a thin bar: fixed above the card, so it never scrolls away. */
@Composable
private fun SessionProgress(position: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.practice_progress, position + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        N3ProgressBar(position.toFloat() / total, height = 4.dp)
    }
}

private fun PracticeItem.typeLabel(): Int = when (this) {
    is PracticeItem.VocabularyItem -> R.string.practice_type_vocabulary
    is PracticeItem.KanjiItem -> R.string.practice_type_kanji
    is PracticeItem.GrammarItem -> R.string.practice_type_grammar
}

/**
 * The question: only the word, the kanji or the pattern, large; nothing of the answer. A grammar
 * card also asks what the pattern means and how it is used (gone once the answer shows).
 */
@Composable
private fun CardFront(item: PracticeItem, revealed: Boolean) {
    val text = when (item) {
        is PracticeItem.VocabularyItem -> item.vocabulary.expression
        is PracticeItem.KanjiItem -> item.kanji.character
        is PracticeItem.GrammarItem -> item.grammar.pattern
    }
    // The kanji is the whole question: the largest text in the app, in the serif face.
    val style = when (item) {
        is PracticeItem.KanjiItem -> MaterialTheme.typography.displayLarge.copy(fontSize = 104.sp, lineHeight = 120.sp)
        is PracticeItem.VocabularyItem -> MaterialTheme.typography.displayMedium
        is PracticeItem.GrammarItem -> MaterialTheme.typography.displaySmall
    }
    Text(japaneseText(text), style = style.japanese(), textAlign = TextAlign.Center, modifier = Modifier.testTag(FlashcardTags.FRONT))
    if (item is PracticeItem.GrammarItem && !revealed) {
        Text(
            stringResource(R.string.flashcard_grammar_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(FlashcardTags.PROMPT),
        )
    }
}

object FlashcardTags {
    const val FRONT = "flashcard_front"
    const val PROMPT = "flashcard_prompt"
    const val EXAMPLES = "flashcard_examples"
    const val NO_EXAMPLES = "flashcard_no_examples"
    const val MORE_EXAMPLES = "flashcard_more_examples"
}

/** Examples shown before "Xem thêm": enough to learn from, short enough for a card. */
private const val VISIBLE_EXAMPLES = 3

/**
 * The answer, from the content as stored (never filled in): a word's reading, meaning, part of
 * speech, examples and note; a pattern's meaning, structure, examples, usage and caution. A part
 * the content does not have is left out, except the examples, which say there are none.
 */
@Composable
private fun CardBack(item: PracticeItem) {
    val center = Modifier.fillMaxWidth()
    when (item) {
        is PracticeItem.VocabularyItem -> {
            val v = item.vocabulary
            // Furigana already shows the reading; plain kana only when there is no markup, and
            // nothing when the word is written in kana already.
            if (v.furigana != null && LocalShowFurigana.current) {
                FuriganaText(v.furigana, style = MaterialTheme.typography.headlineSmall)
            } else if (v.reading.isNotBlank() && v.reading != v.expression) {
                Text(japaneseText(v.reading), style = MaterialTheme.typography.titleLarge.japanese(), textAlign = TextAlign.Center, modifier = center)
            }
            Text(v.meaningVi, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = center)
            v.partOfSpeech?.takeIf { it.isNotBlank() }?.let { Tag(it) }
            ExamplesBlock(item.examples, R.string.flashcard_no_examples_word) { ExampleHighlight.ofVocabulary(it.japanese, v.expression, v.reading) }
            v.note?.takeIf { it.isNotBlank() }?.let { AnswerSection(R.string.flashcard_note) { BodyText(it) } }
        }
        is PracticeItem.KanjiItem -> {
            val k = item.kanji
            k.hanViet?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
            Text(k.meaningVi, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = center)
            Text(
                stringResource(
                    R.string.kanji_readings_line,
                    k.onReadings.joinToString("、").ifEmpty { stringResource(R.string.common_none) },
                    k.kunReadings.joinToString("、").ifEmpty { stringResource(R.string.common_none) },
                ),
                style = MaterialTheme.typography.titleMedium.japanese(),
                textAlign = TextAlign.Center,
                modifier = center,
            )
            // The kanji's own note (as stored, possibly edited by the learner): an explanation
            // that belongs with the answer.
            k.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = center)
            }
        }
        is PracticeItem.GrammarItem -> {
            val g = item.grammar
            Text(g.meaningVi, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = center)
            // How the pattern attaches (the content's structure line: forms and conjugation).
            g.structure.takeIf { it.isNotBlank() }?.let {
                AnswerSection(R.string.flashcard_structure) { Text(japaneseText(it), style = MaterialTheme.typography.titleMedium.japanese()) }
            }
            ExamplesBlock(item.examples, R.string.flashcard_no_examples_grammar) { ExampleHighlight.ofGrammar(it.japanese, g.pattern) }
            // Why a sentence takes this pattern: the pattern's usage as written in the content
            // (the content has no explanation per example, so none is made up).
            g.usage.takeIf { it.isNotBlank() }?.let { AnswerSection(R.string.flashcard_usage) { BodyText(it) } }
            g.cautionNote?.takeIf { it.isNotBlank() }?.let { AnswerSection(R.string.flashcard_caution) { BodyText(it) } }
        }
    }
}

/** A labelled part of the answer, aligned to the start (long text reads better than centred). */
@Composable
private fun AnswerSection(@StringRes label: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun BodyText(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/**
 * The examples on their own light background: the Japanese large (furigana when the content has
 * it, so not too small), the Vietnamese under it, the word or pattern emphasized. The first
 * [VISIBLE_EXAMPLES], then "Xem thêm" for the rest; a line saying there is none when the content
 * has none.
 */
@Composable
private fun ExamplesBlock(examples: List<ExampleSentence>, @StringRes noneText: Int, highlight: (ExampleSentence) -> List<IntRange>) {
    var expanded by remember(examples) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FlashcardTags.EXAMPLES),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.flashcard_examples, examples.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (examples.isEmpty()) {
                Text(
                    stringResource(noneText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp).testTag(FlashcardTags.NO_EXAMPLES),
                )
            }
            val shown = if (expanded) examples else examples.take(VISIBLE_EXAMPLES)
            shown.forEachIndexed { i, example ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExampleSentenceItem(
                    example,
                    highlight = highlight(example),
                    japaneseStyle = MaterialTheme.typography.titleLarge.japanese(),
                    translationStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            val hidden = examples.size - VISIBLE_EXAMPLES
            if (hidden > 0) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag(FlashcardTags.MORE_EXAMPLES)) {
                    Text(stringResource(if (expanded) R.string.flashcard_fewer_examples else R.string.flashcard_more_examples, hidden))
                }
            }
        }
    }
}

/**
 * Every word that goes with the kanji, all of them, on both sides of the card. The question
 * side shows only how each word is written (no furigana, reading or meaning: that would give
 * the answer away); the answer side adds the reading and the Vietnamese meaning. The card area
 * scrolls (see [FlashcardSession]), so a long list never pushes the buttons off screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanjiWords(words: List<KanjiWord>, revealed: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.flashcard_kanji_words, words.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (words.isEmpty()) {
            Text(stringResource(R.string.flashcard_kanji_no_words), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!revealed) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                words.forEach { word ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            japaneseText(word.japanese),
                            style = wordStyle().japanese(),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        } else {
            words.forEach { RevealedWord(it) }
        }
    }
}

/** Japanese words on the card: a size up from the list style, readable at arm's length. */
@Composable
private fun wordStyle() = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default)

@Composable
private fun RevealedWord(word: KanjiWord) {
    val showRuby = LocalShowFurigana.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(0.5f)) {
                if (word.furigana != null && showRuby) {
                    FuriganaText(word.furigana, style = wordStyle())
                } else {
                    Text(japaneseText(word.japanese), style = wordStyle().japanese())
                    // Ruby turned off in settings: the reading still is part of the answer.
                    word.furigana?.let { Furigana.readingText(it) }?.takeIf { it != word.japanese }?.let {
                        Text(japaneseText(it), style = MaterialTheme.typography.bodyMedium.japanese(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.weight(0.5f)) {
                Text(word.meaningVi, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (word.vocabularyId != null) {
                    Text(
                        stringResource(R.string.flashcard_kanji_word_linked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val FLIP_MILLIS = 300

/**
 * Four buttons in one row; two rows of two when a button would be narrower than its label
 * needs (large font scale or narrow screen), so labels like "Quên · 20 phút" are not cut.
 */
@Composable
private fun RatingButtons(onRate: (ReviewRating) -> Unit, hints: Map<ReviewRating, String>, enabled: Boolean) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val perButton = (maxWidth - 24.dp) / 4
        val twoRows = perButton < 56.dp * fontScale
        val again: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.AGAIN, R.string.rating_again, hints, enabled, onRate) }
        val hard: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.HARD, R.string.rating_hard, hints, enabled, onRate) }
        val good: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.GOOD, R.string.rating_good, hints, enabled, onRate) }
        val easy: @Composable RowScope.() -> Unit = { RatingButton(ReviewRating.EASY, R.string.rating_easy, hints, enabled, onRate) }
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
    hints: Map<ReviewRating, String>,
    enabled: Boolean,
    onRate: (ReviewRating) -> Unit,
) {
    val label = stringResource(labelRes)
    val hint = hints[rating]
    val (container, content) = StudyColors.ratingContainer(rating)
    Button(
        onClick = { onRate(rating) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        // Tight padding so four buttons fit on narrow screens with large font scale.
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            // Read as "Nhớ, 1 ngày" by TalkBack, like the two lines on screen.
            .semantics(mergeDescendants = true) {},
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}
