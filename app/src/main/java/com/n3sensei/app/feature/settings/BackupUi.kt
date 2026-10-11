package com.n3sensei.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.domain.backup.BackupIssue
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.BackupSection
import com.n3sensei.app.domain.backup.RestorePreview
import com.n3sensei.app.domain.backup.RestoreReport
import com.n3sensei.app.domain.importing.IssueSeverity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_ISSUES_SHOWN = 30
private val createdFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")

/** Settings section with the export and restore actions. */
@Composable
fun BackupSettingsSection(state: BackupUiState, onExport: () -> Unit, onRestore: () -> Unit) {
    SectionHeader(stringResource(R.string.backup_title), Modifier.padding(horizontal = 16.dp))
    Text(
        stringResource(R.string.backup_scope),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    val working = (state as? BackupUiState.Working)?.action
    BackupAction(R.string.backup_export, R.string.backup_export_description, working == BackupAction.EXPORT, working == null, onExport)
    BackupAction(
        R.string.backup_restore,
        R.string.backup_restore_description,
        working == BackupAction.CHECK || working == BackupAction.RESTORE,
        working == null,
        onRestore,
    )
}

@Composable
private fun BackupAction(title: Int, description: Int, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = { if (busy) CircularProgressIndicator(Modifier.size(24.dp)) },
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** Report and confirmation dialogs for the current [state]. */
@Composable
fun BackupDialogs(
    state: BackupUiState,
    onRestoreSettingsChange: (Boolean) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is BackupUiState.Exported -> ReportDialog(
            title = if (state.result.success) R.string.backup_export_done else R.string.backup_export_failed,
            onDismiss = onDismiss,
        ) {
            if (state.result.success) {
                BackupSection.entries.forEach { section ->
                    Text(stringResource(R.string.backup_count_line, stringResource(section.labelRes()), state.result.counts[section] ?: 0))
                }
                Text(stringResource(R.string.backup_export_settings_note), style = MaterialTheme.typography.bodySmall)
            }
            Issues(state.result.issues)
        }
        is BackupUiState.Rejected -> ReportDialog(R.string.backup_rejected, onDismiss) {
            Text(stringResource(R.string.backup_nothing_changed))
            Issues(state.issues)
        }
        is BackupUiState.Preview -> PreviewDialog(state.preview, state.restoreSettings, onRestoreSettingsChange, onConfirmRestore, onDismiss)
        is BackupUiState.Restored -> RestoredDialog(state.report, onDismiss)
        BackupUiState.Idle, is BackupUiState.Working -> Unit
    }
}

@Composable
private fun PreviewDialog(
    preview: RestorePreview,
    restoreSettings: Boolean,
    onRestoreSettingsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = preview.hasWork) { Text(stringResource(R.string.backup_preview_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
        text = {
            ScrollingColumn {
                val created = Instant.ofEpochMilli(preview.createdAt).atZone(ZoneId.systemDefault()).format(createdFormat)
                Text(stringResource(R.string.backup_preview_file, created, preview.appVersion ?: "?", preview.formatVersion))
                Text(stringResource(R.string.backup_preview_plan), style = MaterialTheme.typography.titleSmall)
                BackupSection.entries.forEach { section ->
                    val c = preview.plan.getValue(section)
                    Text(
                        stringResource(R.string.backup_plan_line, stringResource(section.labelRes()), preview.data.count(section), c.added, c.updated, c.unchanged),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.backup_merge_rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preview.skippedEntries > 0) {
                    Text(stringResource(R.string.backup_skipped, preview.skippedEntries), color = MaterialTheme.colorScheme.error)
                }
                if (preview.data.settings != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.toggleable(value = restoreSettings, role = Role.Checkbox, onValueChange = onRestoreSettingsChange),
                    ) {
                        Checkbox(checked = restoreSettings, onCheckedChange = null)
                        Text(stringResource(R.string.backup_restore_settings), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text(stringResource(R.string.backup_no_settings), style = MaterialTheme.typography.bodySmall)
                }
                if (!preview.hasWork) Text(stringResource(R.string.backup_nothing_to_restore), color = MaterialTheme.colorScheme.error)
                Issues(preview.issues)
            }
        },
    )
}

@Composable
private fun RestoredDialog(report: RestoreReport, onDismiss: () -> Unit) {
    ReportDialog(if (report.success) R.string.backup_restore_done else R.string.backup_restore_failed, onDismiss) {
        if (report.success) {
            BackupSection.entries.forEach { section ->
                val c = report.counts[section] ?: return@forEach
                Text(stringResource(R.string.backup_result_line, stringResource(section.labelRes()), c.added, c.updated, c.unchanged))
            }
            Text(stringResource(if (report.settingsRestored) R.string.backup_settings_restored else R.string.backup_settings_not_restored))
            if (report.skippedEntries > 0) Text(stringResource(R.string.backup_skipped, report.skippedEntries), color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(R.string.backup_nothing_changed))
        }
        Issues(report.issues)
    }
}

@Composable
private fun ReportDialog(title: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_report_close)) } },
        title = { Text(stringResource(title)) },
        text = { ScrollingColumn(content) },
    )
}

@Composable
private fun ScrollingColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Issues(issues: List<BackupIssue>) {
    if (issues.isEmpty()) return
    HorizontalDivider()
    issues.take(MAX_ISSUES_SHOWN).forEach { IssueLine(it) }
    if (issues.size > MAX_ISSUES_SHOWN) {
        Text(stringResource(R.string.import_report_more, issues.size - MAX_ISSUES_SHOWN), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IssueLine(issue: BackupIssue) {
    val severity = stringResource(
        when (issue.severity) {
            IssueSeverity.FATAL -> R.string.import_severity_fatal
            IssueSeverity.ERROR -> R.string.import_severity_error
            IssueSeverity.WARNING -> R.string.import_severity_warning
        },
    )
    val color = if (issue.severity == IssueSeverity.WARNING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    val message = if (issue.code == BackupIssueCode.UNKNOWN_ITEMS) {
        stringResource(R.string.backup_issue_UNKNOWN_ITEMS, issue.detail?.toIntOrNull() ?: 0)
    } else {
        stringResource(issue.code.labelRes())
    }
    val text = buildString {
        append(severity).append(": ").append(message)
        if (issue.location.isNotEmpty()) append(" — ").append(issue.location)
        if (issue.code != BackupIssueCode.UNKNOWN_ITEMS) issue.detail?.let { append(" [").append(it).append("]") }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

private fun BackupSection.labelRes(): Int = when (this) {
    BackupSection.PROGRESS -> R.string.backup_section_progress
    BackupSection.REVIEW_RECORDS -> R.string.backup_section_records
    BackupSection.MARKS -> R.string.backup_section_marks
    BackupSection.SESSIONS -> R.string.backup_section_sessions
}

private fun BackupIssueCode.labelRes(): Int = when (this) {
    BackupIssueCode.READ_ERROR -> R.string.backup_issue_READ_ERROR
    BackupIssueCode.WRITE_ERROR -> R.string.backup_issue_WRITE_ERROR
    BackupIssueCode.TOO_LARGE -> R.string.backup_issue_TOO_LARGE
    BackupIssueCode.EMPTY_FILE -> R.string.backup_issue_EMPTY_FILE
    BackupIssueCode.INVALID_JSON -> R.string.backup_issue_INVALID_JSON
    BackupIssueCode.NOT_A_BACKUP -> R.string.backup_issue_NOT_A_BACKUP
    BackupIssueCode.NEWER_VERSION -> R.string.backup_issue_NEWER_VERSION
    BackupIssueCode.UNSUPPORTED_VERSION -> R.string.backup_issue_UNSUPPORTED_VERSION
    BackupIssueCode.MISSING_FIELD -> R.string.backup_issue_MISSING_FIELD
    BackupIssueCode.INVALID_VALUE -> R.string.backup_issue_INVALID_VALUE
    BackupIssueCode.DUPLICATE_ID -> R.string.backup_issue_DUPLICATE_ID
    BackupIssueCode.INVALID_SETTINGS -> R.string.backup_issue_INVALID_SETTINGS
    BackupIssueCode.UNKNOWN_ITEMS -> R.string.backup_issue_UNKNOWN_ITEMS
    BackupIssueCode.DATABASE_ERROR -> R.string.backup_issue_DATABASE_ERROR
    BackupIssueCode.SETTINGS_ERROR -> R.string.backup_issue_SETTINGS_ERROR
}
