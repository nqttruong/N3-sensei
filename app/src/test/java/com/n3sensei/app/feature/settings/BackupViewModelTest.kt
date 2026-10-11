package com.n3sensei.app.feature.settings

import com.n3sensei.app.domain.backup.BackupData
import com.n3sensei.app.domain.backup.BackupIssue
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.BackupRepository
import com.n3sensei.app.domain.backup.BackupSection
import com.n3sensei.app.domain.backup.ExportResult
import com.n3sensei.app.domain.backup.InspectResult
import com.n3sensei.app.domain.backup.MergeCount
import com.n3sensei.app.domain.backup.ReminderBackup
import com.n3sensei.app.domain.backup.RestorePreview
import com.n3sensei.app.domain.backup.RestoreReport
import com.n3sensei.app.domain.backup.SessionBackup
import com.n3sensei.app.domain.backup.SettingsBackup
import com.n3sensei.app.domain.importing.IssueSeverity
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.testing.MainDispatcherRule
import com.n3sensei.app.testing.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = SettingsBackup(ThemeMode.DARK, true, ReviewSettings(), ReminderBackup.from(ReminderSettings()))
    private val plan = BackupSection.entries.associateWith { MergeCount() } + (BackupSection.SESSIONS to MergeCount(added = 1))
    private val preview = RestorePreview(1, 1, "0.1.0", BackupData(sessions = listOf(SessionBackup("s", 1, 1, 1, 1, true)), settings = settings), 0, emptyList(), plan)

    private class FakeBackupRepository : BackupRepository {
        var inspectResult: InspectResult = InspectResult.Rejected(emptyList())
        var exportError: Exception? = null
        val restored = mutableListOf<Pair<RestorePreview, Boolean>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun export(documentUri: String): ExportResult {
            exportError?.let { throw it }
            return ExportResult(true, BackupSection.entries.associateWith { 1 })
        }

        override suspend fun inspect(documentUri: String) = inspectResult

        override suspend fun restore(preview: RestorePreview, restoreSettings: Boolean): RestoreReport {
            gate?.await()
            restored += preview to restoreSettings
            return RestoreReport(true, preview.plan, settingsRestored = restoreSettings)
        }
    }

    private val repo = FakeBackupRepository()
    private val clock = MutableClock(Instant.parse("2026-10-11T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val vm = BackupViewModel(repo, clock)

    @Test
    fun suggestedFileName_usesLocalDate() {
        assertEquals("n3sensei-backup-2026-10-11.json", vm.suggestedFileName())
    }

    @Test
    fun export_reportsCounts_andUnexpectedErrors() = runTest {
        vm.export("content://x")
        assertTrue((vm.state.value as BackupUiState.Exported).result.success)
        vm.dismiss()
        repo.exportError = IOException("db closed")
        vm.export("content://x")
        val failed = (vm.state.value as BackupUiState.Exported).result
        assertFalse(failed.success)
        assertEquals(BackupIssueCode.DATABASE_ERROR, failed.issues.single().code)
    }

    @Test
    fun rejectedFile_isShown_andNothingIsRestored() = runTest {
        repo.inspectResult = InspectResult.Rejected(listOf(BackupIssue(IssueSeverity.FATAL, BackupIssueCode.INVALID_JSON)))
        vm.inspect("content://x")
        assertEquals(BackupIssueCode.INVALID_JSON, (vm.state.value as BackupUiState.Rejected).issues.single().code)
        vm.confirmRestore()
        assertTrue(repo.restored.isEmpty())
    }

    @Test
    fun previewThenConfirm_restoresWithChosenSettingsOption() = runTest {
        repo.inspectResult = InspectResult.Ready(preview)
        vm.inspect("content://x")
        val shown = vm.state.value as BackupUiState.Preview
        assertTrue(shown.restoreSettings) // on by default when the file has settings
        vm.setRestoreSettings(false)
        vm.confirmRestore()
        assertEquals(listOf(preview to false), repo.restored)
        assertFalse((vm.state.value as BackupUiState.Restored).report.settingsRestored)
    }

    @Test
    fun cancel_restoresNothing() = runTest {
        repo.inspectResult = InspectResult.Ready(preview)
        vm.inspect("content://x")
        vm.dismiss()
        assertEquals(BackupUiState.Idle, vm.state.value)
        vm.confirmRestore()
        assertTrue(repo.restored.isEmpty())
    }

    @Test
    fun nothingToRestore_cannotBeConfirmed() = runTest {
        repo.inspectResult = InspectResult.Ready(preview.copy(data = BackupData(), plan = BackupSection.entries.associateWith { MergeCount() }))
        vm.inspect("content://x")
        vm.confirmRestore()
        assertTrue(repo.restored.isEmpty())
    }

    @Test
    fun whileRestoring_otherActionsAreIgnored_andDismissDoesNotHideProgress() = runTest {
        repo.inspectResult = InspectResult.Ready(preview)
        repo.gate = CompletableDeferred()
        vm.inspect("content://x")
        vm.confirmRestore()
        assertEquals(BackupUiState.Working(BackupAction.RESTORE), vm.state.value)
        vm.export("content://y")
        vm.dismiss()
        assertEquals(BackupUiState.Working(BackupAction.RESTORE), vm.state.value)
        repo.gate!!.complete(Unit)
        assertEquals(1, repo.restored.size)
        assertTrue(vm.state.value is BackupUiState.Restored)
    }
}
