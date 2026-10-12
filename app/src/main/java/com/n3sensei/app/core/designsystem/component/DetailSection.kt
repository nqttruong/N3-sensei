package com.n3sensei.app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.n3sensei.app.core.designsystem.theme.isJapanese
import com.n3sensei.app.core.designsystem.theme.japaneseText

/** Titled block in a detail screen. */
@Composable
fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        SectionHeader(title)
        content()
    }
}

/** Titled block whose content is a single text. */
@Composable
fun DetailText(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    DetailSection(title, modifier) { Text(if (style.isJapanese) japaneseText(text) else AnnotatedString(text), style = style) }
}
