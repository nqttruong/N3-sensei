package com.n3sensei.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.n3sensei.app.core.ui.content.intervalLabel
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    backupViewModel: BackupViewModel,
    onOpenReviewSettings: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importDocument(it.toString()) }
    }
    (importState as? ImportUiState.Finished)?.let { ImportReportDialog(it.report, viewModel::dismissImportReport) }

    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { backupViewModel.export(it.toString()) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupViewModel.inspect(it.toString()) }
    }
    BackupDialogs(backupState, backupViewModel::setRestoreSettings, backupViewModel::confirmRestore, backupViewModel::dismiss)

    // Hoisted so the position survives a reload, e.g. after returning from the file picker.
    val scrollState = rememberScrollState()
    UiStateContent(state = state, modifier = modifier) { ui ->
        val prefs = ui.preferences
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
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

            SectionHeader(stringResource(R.string.settings_review), Modifier.padding(horizontal = 16.dp))
            val review = prefs.reviewSettings
            ListItem(
                headlineContent = { Text(stringResource(R.string.review_settings_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_review_summary,
                            review.ladder.intervals.map { intervalLabel(it) }.joinToString(" → "),
                            review.limits.newPerDay,
                            review.limits.reviewsPerDay,
                        ),
                    )
                },
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenReviewSettings),
            )
            HorizontalDivider()

            val reminders = prefs.reminderSettings
            ListItem(
                headlineContent = { Text(stringResource(R.string.reminder_settings_title)) },
                supportingContent = {
                    Text(
                        if (reminders.enabled) {
                            stringResource(
                                R.string.settings_reminders_summary_on,
                                frequencyLabel(reminders.frequency),
                                reminders.startHour,
                                reminders.endHour,
                            )
                        } else {
                            stringResource(R.string.settings_reminders_summary_off)
                        },
                    )
                },
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenReminderSettings),
            )
            HorizontalDivider()

            BackupSettingsSection(
                state = backupState,
                onExport = { createBackup.launch(backupViewModel.suggestedFileName()) },
                // Some providers report JSON as text/plain or octet-stream.
                onRestore = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_content), Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (ui.packs.isEmpty()) {
                    Text(stringResource(R.string.settings_no_packs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.packs.forEach { pack ->
                    Text(
                        stringResource(R.string.settings_pack_line, pack.title, pack.version, pack.lessonCount),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            val importing = importState == ImportUiState.Running
            ListItem(
                headlineContent = { Text(stringResource(if (importing) R.string.settings_importing else R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_description)) },
                trailingContent = { if (importing) CircularProgressIndicator(Modifier.size(24.dp)) },
                modifier = Modifier.clickable(enabled = !importing, role = Role.Button) {
                    // Some providers report JSON as text/plain or octet-stream.
                    pickDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            )
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
