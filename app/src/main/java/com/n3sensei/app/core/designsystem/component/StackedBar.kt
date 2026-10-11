package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

data class StackSegment(val value: Int, val color: Color)

/** Horizontal 100% stacked bar; an all-zero bar is drawn as an empty track. */
@Composable
fun StackedBar(
    segments: List<StackSegment>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(50))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        drawRect(track)
        val total = segments.sumOf { it.value }
        if (total == 0) return@Canvas
        var x = 0f
        segments.forEach { segment ->
            val w = size.width * segment.value / total
            if (w > 0) drawRect(segment.color, Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
}
