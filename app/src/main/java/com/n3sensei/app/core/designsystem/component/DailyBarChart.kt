package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One bar: a total with an optional highlighted part (e.g. new items within all answers). */
data class BarValue(val label: String, val total: Int, val highlighted: Int = 0)

/**
 * Small vertical bar chart for phones. Axis labels show the first, middle and last bar; the
 * maximum is printed above the chart. [description] is read by TalkBack instead of the drawing.
 */
@Composable
fun DailyBarChart(
    bars: List<BarValue>,
    description: String,
    maxLabel: String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val max = bars.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barWidth = (slot * 0.7f).coerceAtLeast(2f)
            val radius = CornerRadius(barWidth / 4, barWidth / 4)
            bars.forEachIndexed { i, bar ->
                val x = i * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), radius, alpha = 0.35f)
                val h = size.height * bar.total / max
                if (h > 0) drawRoundRect(barColor, Offset(x, size.height - h), Size(barWidth, h), radius)
                val hh = size.height * bar.highlighted.coerceAtMost(bar.total) / max
                if (hh > 0) drawRoundRect(highlightColor, Offset(x, size.height - hh), Size(barWidth, hh), radius)
            }
        }
        if (bars.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                val style = MaterialTheme.typography.labelSmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Text(bars.first().label, style = style, color = color, modifier = Modifier.weight(1f))
                Text(bars[bars.size / 2].label, style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text(bars.last().label, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}
