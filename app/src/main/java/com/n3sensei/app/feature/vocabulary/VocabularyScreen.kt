package com.n3sensei.app.feature.vocabulary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FeaturePendingContent
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme

@Composable
fun VocabularyScreen(modifier: Modifier = Modifier) {
    FeaturePendingContent(messageRes = R.string.vocabulary_pending, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun VocabularyScreenPreview() {
    N3SenseiTheme { VocabularyScreen() }
}
