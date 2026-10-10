package com.n3sensei.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.ui.content.SourceNote
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state = state, modifier = modifier) { ui ->
        val prefs = ui.preferences
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance), Modifier.padding(horizontal = 16.dp))
            Column(Modifier.selectableGroup()) {
                listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                ).forEach { (mode, label) ->
                    ListItem(
                        headlineContent = { Text(stringResource(label)) },
                        leadingContent = { RadioButton(selected = prefs.themeMode == mode, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = prefs.themeMode == mode,
                            role = Role.RadioButton,
                            onClick = { viewModel.setThemeMode(mode) },
                        ),
                    )
                }
            }
            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_learning), Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_furigana)) },
                supportingContent = { Text(stringResource(R.string.settings_show_furigana_description)) },
                trailingContent = { Switch(checked = prefs.showFurigana, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = prefs.showFurigana,
                    role = Role.Switch,
                    onValueChange = viewModel::setShowFurigana,
                ),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_replay_onboarding)) },
                modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = viewModel::replayOnboarding),
            )
            HorizontalDivider()

            // Planned features: listed so users know they exist, but shown disabled.
            SectionHeader(stringResource(R.string.settings_upcoming), Modifier.padding(horizontal = 16.dp))
            listOf(R.string.settings_review_intervals, R.string.settings_reminders, R.string.settings_backup).forEach {
                ListItem(
                    headlineContent = { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    supportingContent = { Text(stringResource(R.string.common_not_implemented)) },
                )
            }
            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_sources), Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (ui.sources.isEmpty()) {
                    Text(stringResource(R.string.settings_no_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.sources.forEach { SourceNote(it, Modifier.padding(vertical = 4.dp)) }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.settings_about, appVersionName()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull()
        .orEmpty()
}
