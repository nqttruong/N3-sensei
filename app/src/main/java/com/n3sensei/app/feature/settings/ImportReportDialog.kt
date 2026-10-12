package com.n3sensei.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.domain.importing.ImportIssue
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportPreview
import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.importing.IssueSeverity

private const val MAX_ISSUES_SHOWN = 30

@Composable
fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_report_close)) } },
        title = {
            Text(
                stringResource(
                    when (report.outcome) {
                        ImportOutcome.IMPORTED -> R.string.import_report_title_success
                        ImportOutcome.UP_TO_DATE -> R.string.import_report_title_up_to_date
                        ImportOutcome.FAILED -> R.string.import_report_title_failed
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (report.packTitle != null && report.packVersion != null) {
                    Text(stringResource(R.string.import_report_pack, report.packTitle, report.packVersion), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    stringResource(
                        R.string.import_report_counts,
                        report.validRecords,
                        report.skippedRecords,
                        report.errorCount,
                        report.warningCount,
                    ),
                )
                report.counts.forEach { (type, count) ->
                    Text(
                        stringResource(R.string.import_report_type_line, stringResource(type.labelRes()), count.valid, count.skipped),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (report.issues.isNotEmpty()) HorizontalDivider()
                report.issues.take(MAX_ISSUES_SHOWN).forEach { IssueLine(it) }
                if (report.issues.size > MAX_ISSUES_SHOWN) {
                    Text(stringResource(R.string.import_report_more, report.issues.size - MAX_ISSUES_SHOWN), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

/** Confirmation before a pack replaces the installed version of the same pack. */
@Composable
fun ReplacePackDialog(preview: ImportPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val report = preview.report
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_replace_title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_replace_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (report.packTitle != null && report.packVersion != null) {
                    Text(
                        stringResource(R.string.import_replace_versions, report.packTitle, preview.installedVersion ?: 0, report.packVersion),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                report.counts.forEach { (type, count) ->
                    Text(
                        stringResource(R.string.import_report_type_line, stringResource(type.labelRes()), count.valid, count.skipped),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (preview.removedItems > 0) {
                    Text(
                        stringResource(R.string.import_replace_removed, preview.removedItems, preview.removedWithProgress),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.import_replace_rule), style = MaterialTheme.typography.bodySmall)
                if (report.issues.isNotEmpty()) HorizontalDivider()
                report.issues.take(MAX_ISSUES_SHOWN).forEach { IssueLine(it) }
                if (report.issues.size > MAX_ISSUES_SHOWN) {
                    Text(stringResource(R.string.import_report_more, report.issues.size - MAX_ISSUES_SHOWN), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

@Composable
private fun IssueLine(issue: ImportIssue) {
    val severity = stringResource(
        when (issue.severity) {
            IssueSeverity.FATAL -> R.string.import_severity_fatal
            IssueSeverity.ERROR -> R.string.import_severity_error
            IssueSeverity.WARNING -> R.string.import_severity_warning
        },
    )
    val color = if (issue.severity == IssueSeverity.WARNING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    val text = buildString {
        append(severity).append(": ").append(issueMessage(issue))
        if (issue.location.isNotEmpty()) append(" — ").append(issue.location)
        issue.detail?.let { append(" [").append(it).append("]") }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun issueMessage(issue: ImportIssue): String = stringResource(issue.code.labelRes())

private fun IssueCode.labelRes(): Int = when (this) {
    IssueCode.INVALID_JSON -> R.string.import_issue_INVALID_JSON
    IssueCode.READ_ERROR -> R.string.import_issue_READ_ERROR
    IssueCode.UNSUPPORTED_FORMAT -> R.string.import_issue_UNSUPPORTED_FORMAT
    IssueCode.MISSING_FIELD -> R.string.import_issue_MISSING_FIELD
    IssueCode.INVALID_VALUE -> R.string.import_issue_INVALID_VALUE
    IssueCode.DUPLICATE_ID -> R.string.import_issue_DUPLICATE_ID
    IssueCode.DUPLICATE_ENTRY -> R.string.import_issue_DUPLICATE_ENTRY
    IssueCode.DUPLICATE_LESSON_NUMBER -> R.string.import_issue_DUPLICATE_LESSON_NUMBER
    IssueCode.UNKNOWN_SOURCE -> R.string.import_issue_UNKNOWN_SOURCE
    IssueCode.UNKNOWN_EXAMPLE -> R.string.import_issue_UNKNOWN_EXAMPLE
    IssueCode.UNKNOWN_TAG -> R.string.import_issue_UNKNOWN_TAG
    IssueCode.INVALID_FURIGANA -> R.string.import_issue_INVALID_FURIGANA
    IssueCode.FURIGANA_MISMATCH -> R.string.import_issue_FURIGANA_MISMATCH
    IssueCode.INVALID_KANJI_CHARACTER -> R.string.import_issue_INVALID_KANJI_CHARACTER
    IssueCode.INVALID_EXERCISE -> R.string.import_issue_INVALID_EXERCISE
    IssueCode.LESSON_SIZE_DIFFERS -> R.string.import_issue_LESSON_SIZE_DIFFERS
    IssueCode.NO_LESSONS -> R.string.import_issue_NO_LESSONS
    IssueCode.OLDER_VERSION -> R.string.import_issue_OLDER_VERSION
    IssueCode.DATABASE_ERROR -> R.string.import_issue_DATABASE_ERROR
}

private fun ImportRecordType.labelRes(): Int = when (this) {
    ImportRecordType.LESSON -> R.string.import_type_lesson
    ImportRecordType.VOCABULARY -> R.string.import_type_vocabulary
    ImportRecordType.KANJI -> R.string.import_type_kanji
    ImportRecordType.GRAMMAR -> R.string.import_type_grammar
    ImportRecordType.EXAMPLE -> R.string.import_type_example
    ImportRecordType.EXERCISE -> R.string.import_type_exercise
    ImportRecordType.TAG -> R.string.import_type_tag
}
