package com.n3sensei.app.feature.kanji

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FeaturePendingContent
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme

@Composable
fun KanjiScreen(modifier: Modifier = Modifier) {
    FeaturePendingContent(messageRes = R.string.kanji_pending, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun KanjiScreenPreview() {
    N3SenseiTheme { KanjiScreen() }
}
