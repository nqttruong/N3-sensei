package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.domain.furigana.Furigana

/** User setting: whether readings are drawn above kanji. Provided at the app root. */
val LocalShowFurigana = staticCompositionLocalOf { true }

/**
 * Japanese text with ruby (furigana) above kanji, from `{base|reading}` markup.
 *
 * Compose has no native ruby support, so each ruby group and each plain character is laid out
 * as a column in a [FlowRow]; lines break between characters, never inside a ruby group.
 * Accessibility services and tests see the plain base text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaText(
    markup: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    showFurigana: Boolean = LocalShowFurigana.current,
    /** Parts of the base text to emphasize (e.g. the word an example illustrates). */
    highlight: List<IntRange> = emptyList(),
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    val segments = remember(markup) {
        // Content is validated on import; fall back to the raw text rather than crash.
        runCatching { Furigana.parse(markup) }.getOrElse { listOf(Furigana.Segment(markup)) }
    }
    val baseText = remember(segments) { segments.joinToString("") { it.text } }
    val baseStyle = style.japanese()
    val contentColor = LocalContentColor.current
    val textColor = if (color != Color.Unspecified) color else contentColor

    fun highlighted(start: Int, length: Int) = highlight.any { it.first < start + length && start <= it.last }
    val emphasis = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)

    if (!showFurigana || segments.none { it.reading != null }) {
        // The language is part of the text, so TalkBack reads it with a Japanese voice.
        val text = if (highlight.isEmpty()) japaneseText(baseText) else buildAnnotatedString {
            append(japaneseText(baseText))
            highlight.forEach { r ->
                val start = r.first.coerceIn(0, baseText.length)
                val end = (r.last + 1).coerceIn(start, baseText.length)
                addStyle(emphasis, start, end)
            }
        }
        Text(text = text, style = baseStyle, color = textColor, modifier = modifier)
        return
    }

    val rubyStyle = baseStyle.copy(
        fontSize = baseStyle.fontSize.scaled(0.5f),
        lineHeight = baseStyle.fontSize.scaled(0.65f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        modifier = modifier.clearAndSetSemantics { text = japaneseText(baseText) },
        horizontalArrangement = Arrangement.Start,
    ) {
        var offset = 0
        segments.forEach { segment ->
            if (segment.reading != null) {
                val on = highlighted(offset, segment.text.length)
                RubyCell(segment.text, segment.reading, baseStyle.emphasized(on), rubyStyle, if (on) highlightColor else textColor)
            } else {
                segment.text.forEachIndexed { i, char ->
                    val on = highlighted(offset + i, 1)
                    RubyCell(char.toString(), null, baseStyle.emphasized(on), rubyStyle, if (on) highlightColor else textColor)
                }
            }
            offset += segment.text.length
        }
    }
}

/**
 * One ruby group (or plain character). Groups normally fit on a line; one wider than the whole
 * line (a long word given a single reading, large font scale) wraps inside its cell instead
 * of being cut off at the screen edge.
 */
@Composable
private fun RubyCell(base: String, reading: String?, baseStyle: TextStyle, rubyStyle: TextStyle, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // A blank ruby line keeps plain characters on the same baseline as ruby groups.
        Text(text = reading ?: " ", style = rubyStyle, textAlign = TextAlign.Center)
        Text(text = base, style = baseStyle, color = color, textAlign = TextAlign.Center)
    }
}

private fun TextStyle.emphasized(on: Boolean): TextStyle = if (on) copy(fontWeight = FontWeight.Bold) else this

private fun TextUnit.scaled(factor: Float): TextUnit = if (isSpecified) this * factor else this

@Preview(showBackground = true)
@Composable
private fun FuriganaTextPreview() {
    N3SenseiTheme {
        FuriganaText("{日本|にほん}で{働|はたら}いた{経験|けいけん}があります。", style = MaterialTheme.typography.titleLarge)
    }
}
