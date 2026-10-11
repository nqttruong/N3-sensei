package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.backup.BackupIssue
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.BackupRepository
import com.n3sensei.app.domain.backup.ExportResult
import com.n3sensei.app.domain.backup.InspectResult
import com.n3sensei.app.domain.backup.RestorePreview
import com.n3sensei.app.domain.backup.RestoreReport
import com.n3sensei.app.domain.importing.IssueSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class BackupAction { EXPORT, CHECK, RESTORE }

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val action: BackupAction) : BackupUiState
    data class Exported(val result: ExportResult) : BackupUiState

    /** The file cannot be restored; nothing was written. */
    data class Rejected(val issues: List<BackupIssue>) : BackupUiState

    /** Checked and waiting for the user's confirmation. */
    data class Preview(val preview: RestorePreview, val restoreSettings: Boolean) : BackupUiState
    data class Restored(val report: RestoreReport) : BackupUiState
}

class BackupViewModel(
    private val repository: BackupRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(): String = "n3sensei-backup-${LocalDate.now(clock)}.json"

    fun export(documentUri: String) = run(BackupAction.EXPORT) {
        _state.value = BackupUiState.Exported(
            guarded({ repository.export(documentUri) }) { ExportResult(false, issues = listOf(it)) },
        )
    }

    fun inspect(documentUri: String) = run(BackupAction.CHECK) {
        _state.value = when (val result = guarded({ repository.inspect(documentUri) }) { InspectResult.Rejected(listOf(it)) }) {
            is InspectResult.Rejected -> BackupUiState.Rejected(result.issues)
            is InspectResult.Ready -> BackupUiState.Preview(result.preview, restoreSettings = result.preview.data.settings != null)
        }
    }

    fun setRestoreSettings(restore: Boolean) {
        val preview = _state.value as? BackupUiState.Preview ?: return
        _state.value = preview.copy(restoreSettings = restore && preview.preview.data.settings != null)
    }

    fun confirmRestore() {
        val preview = _state.value as? BackupUiState.Preview ?: return
        if (!preview.preview.hasWork) return
        run(BackupAction.RESTORE) {
            _state.value = BackupUiState.Restored(
                guarded({ repository.restore(preview.preview, preview.restoreSettings) }) { RestoreReport(false, issues = listOf(it)) },
            )
        }
    }

    fun dismiss() {
        if (_state.value !is BackupUiState.Working) _state.value = BackupUiState.Idle
    }

    /** One operation at a time; a new one replaces any report still shown. */
    private fun run(action: BackupAction, block: suspend () -> Unit) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working(action)
        viewModelScope.launch { block() }
    }

    /** Unexpected failures (e.g. the database cannot be read) become a report, not a crash. */
    private suspend fun <T> guarded(block: suspend () -> T, onError: (BackupIssue) -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(BackupIssue(IssueSeverity.FATAL, BackupIssueCode.DATABASE_ERROR, detail = e.message?.take(300)))
    }
}
