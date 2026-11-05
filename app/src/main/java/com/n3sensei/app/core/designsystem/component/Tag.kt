package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TagTone { Neutral, Accent, Info }

/** Small pill label: what kind of figure a card shows, a status, a count. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    tone: TagTone = TagTone.Neutral,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        TagTone.Neutral -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        TagTone.Accent -> colors.primaryContainer to colors.onPrimaryContainer
        TagTone.Info -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** Secondary explanatory text under figures: how a number is computed, what is not counted. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
