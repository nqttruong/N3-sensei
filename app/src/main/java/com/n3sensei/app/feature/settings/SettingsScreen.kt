package com.n3sensei.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.PlaceholderContent

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(
        titleRes = R.string.nav_settings,
        messageRes = R.string.settings_placeholder,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    N3SenseiTheme { SettingsScreen() }
}
