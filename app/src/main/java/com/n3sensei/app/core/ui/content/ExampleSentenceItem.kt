package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ExampleSentence

@Composable
fun ExampleSentenceItem(example: ExampleSentence, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        if (example.furigana != null) {
            FuriganaText(example.furigana, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(example.japanese, style = MaterialTheme.typography.bodyLarge.japanese())
        }
        Text(
            example.translationVi,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Source and license line. Never implies a verified license unless it was verified. */
@Composable
fun SourceNote(source: ContentSource, modifier: Modifier = Modifier) {
    val license = if (source.licenseVerified) {
        source.license
    } else {
        stringResource(R.string.source_license_unverified, source.license)
    }
    Text(
        text = stringResource(R.string.source_line, source.name, license),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
