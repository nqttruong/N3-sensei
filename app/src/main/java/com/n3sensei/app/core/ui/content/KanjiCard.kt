package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.domain.model.Kanji

@Composable
fun KanjiCard(
    kanji: Kanji,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(kanji.character, style = MaterialTheme.typography.displaySmall.japanese())
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                kanji.hanViet?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Text(kanji.meaningVi, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = readingsLine(kanji),
                    style = MaterialTheme.typography.bodySmall.japanese(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun readingsLine(kanji: Kanji): String {
    val none = stringResource(R.string.common_none)
    return stringResource(
        R.string.kanji_readings_line,
        kanji.onReadings.joinToString("、").ifEmpty { none },
        kanji.kunReadings.joinToString("、").ifEmpty { none },
    )
}

@Preview(showBackground = true)
@Composable
private fun KanjiCardPreview() = N3SenseiTheme {
    KanjiCard(PreviewContent.kanji, onClick = {})
}
