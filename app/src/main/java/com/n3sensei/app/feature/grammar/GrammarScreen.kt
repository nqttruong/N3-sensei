package com.n3sensei.app.feature.grammar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FeaturePendingContent
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme

@Composable
fun GrammarScreen(modifier: Modifier = Modifier) {
    FeaturePendingContent(messageRes = R.string.grammar_pending, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun GrammarScreenPreview() {
    N3SenseiTheme { GrammarScreen() }
}
