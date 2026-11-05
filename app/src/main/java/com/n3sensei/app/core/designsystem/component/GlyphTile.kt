package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText

enum class GlyphSize(val size: Dp) { Small(48.dp), Medium(64.dp), Large(112.dp) }

/**
 * A Han character on a tinted square, the same everywhere a kanji or a section glyph is shown.
 * [decorative] hides it from TalkBack when a label next to it already says what it is.
 */
@Composable
fun GlyphTile(
    glyph: String,
    modifier: Modifier = Modifier,
    size: GlyphSize = GlyphSize.Medium,
    decorative: Boolean = false,
) {
    val typography = MaterialTheme.typography
    val style: TextStyle = when (size) {
        GlyphSize.Small -> typography.headlineSmall
        GlyphSize.Medium -> typography.displaySmall
        GlyphSize.Large -> typography.displayLarge
    }
    Surface(
        shape = if (size == GlyphSize.Large) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.size(size.size).then(if (decorative) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(japaneseText(glyph), style = style.japanese())
        }
    }
}
