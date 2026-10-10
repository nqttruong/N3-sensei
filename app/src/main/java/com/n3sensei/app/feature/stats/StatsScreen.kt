package com.n3sensei.app.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.PlaceholderContent

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(
        titleRes = R.string.nav_stats,
        messageRes = R.string.stats_placeholder,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    N3SenseiTheme { StatsScreen() }
}
