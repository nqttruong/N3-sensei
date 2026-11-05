package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.N3Spacing

enum class CardTone {
    /** Flat paper card with a hairline border: the default for content and figures. */
    Default,

    /** Tinted with the accent, for the one thing on a screen to act on. */
    Emphasis,
}

/**
 * The app's card: flat, hairline border, no shadow (a page of a notebook, not a floating tile).
 * Clickable when [onClick] is given; the whole card is then one button.
 */
@Composable
fun N3Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tone: CardTone = CardTone.Default,
    contentPadding: PaddingValues = PaddingValues(N3Spacing.l),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(N3Spacing.s),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val cardColors = when (tone) {
        CardTone.Default -> CardDefaults.cardColors(containerColor = colors.surfaceContainerLow, contentColor = colors.onSurface)
        CardTone.Emphasis -> CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
    }
    val border = if (tone == CardTone.Default) BorderStroke(1.dp, colors.outlineVariant) else null
    val shape = MaterialTheme.shapes.large
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = cardColors, border = border, content = body)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = cardColors, border = border, content = body)
    }
}

/** [N3Card] with a heading and an optional [tag] (e.g. "Thực tế" / "Ước tính") beside it. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Default,
    tag: (@Composable () -> Unit)? = null,
    /** Makes the whole card one button (e.g. "Cần ôn" opens the review). */
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    N3Card(modifier = modifier, onClick = onClick, tone = tone, verticalArrangement = Arrangement.spacedBy(N3Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            tag?.invoke()
        }
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionCardPreview() = N3SenseiTheme {
    Column(Modifier.padding(N3Spacing.l), verticalArrangement = Arrangement.spacedBy(N3Spacing.m)) {
        SectionCard("Mục tiêu hôm nay", tag = { Tag("Thực tế") }) { Caption("Tính từ lịch sử ôn tập đã lưu.") }
        SectionCard("Dự báo", tone = CardTone.Emphasis, tag = { Tag("Ước tính", tone = TagTone.Info) }) { Text("12") }
    }
}
