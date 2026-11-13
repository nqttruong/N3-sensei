package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class AndroidSyncUiState(
    val paired: Boolean,
    val serverUrl: String,
    val running: Boolean = false,
    val status: String = "",
    val lastSuccessAt: Long? = null,
    val downloadedCount: Long = 0,
    val knowledgeCount: Int = 0,
    val pendingEvents: Int = 0,
    val lastError: String? = null,
)

class AndroidSyncViewModel(private val syncRepository: SyncRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        AndroidSyncUiState(syncRepository.isPaired(), syncRepository.serverUrl()),
    )
    val state: StateFlow<AndroidSyncUiState> = _state.asStateFlow()

    init { refreshStatus() }

    fun pair(serverUrl: String, pairingKey: String, deviceName: String) = run("Ghép nối thất bại") {
        syncRepository.pair(serverUrl, pairingKey, deviceName).also {
            _state.value = _state.value.copy(paired = syncRepository.isPaired(), serverUrl = syncRepository.serverUrl())
        }
    }

    fun pushReviews() = run("Đồng bộ thất bại") { syncRepository.pushReviews() }

    private fun run(errorPrefix: String, task: suspend () -> String) {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, status = "")
        viewModelScope.launch {
            val message = runCatching { task() }.getOrElse { error ->
                when {
                    error.message?.contains("HTTP 401") == true || error.message?.contains("HTTP 403") == true -> "Xác thực thiết bị hết hạn hoặc đã bị thu hồi: ${error.message}"
                    error is IOException -> "Offline / lỗi mạng: ${error.message}"
                    else -> "$errorPrefix: ${error.message}"
                }
            }
            val detail = runCatching { syncRepository.status() }.getOrNull()
            _state.value = _state.value.copy(running = false, paired = syncRepository.isPaired(), status = message,
                lastSuccessAt = detail?.lastSuccessAtMillis, downloadedCount = detail?.downloadedCount ?: 0,
                knowledgeCount = detail?.knowledgeCount ?: 0, pendingEvents = detail?.pendingEvents ?: 0, lastError = detail?.lastError)
        }
    }

    fun disconnect() = run("Đã xóa credential cục bộ, nhưng máy chủ chưa xác nhận thu hồi") {
        syncRepository.disconnect()
        "Đã ngắt kết nối và thu hồi thiết bị trên máy chủ."
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            val detail = runCatching { syncRepository.status() }.getOrNull() ?: return@launch
            _state.value = _state.value.copy(lastSuccessAt = detail.lastSuccessAtMillis,
                downloadedCount = detail.downloadedCount, knowledgeCount = detail.knowledgeCount,
                pendingEvents = detail.pendingEvents, lastError = detail.lastError)
        }
    }
}
