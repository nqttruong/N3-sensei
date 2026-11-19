package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.repository.SyncHttpException
import com.n3sensei.app.domain.repository.QrPairingProgress
import com.n3sensei.app.domain.repository.QrPairingStatus
import com.n3sensei.app.domain.repository.SyncRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val pendingHistory: Int = 0,
    val skippedHistory: Int = 0,
    /** QR pairing in progress (code shown to the human) or just ended. */
    val qr: QrPairingProgress? = null,
)

class AndroidSyncViewModel(private val syncRepository: SyncRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        AndroidSyncUiState(syncRepository.isPaired(), syncRepository.serverUrl()),
    )
    val state: StateFlow<AndroidSyncUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refreshStatus()
        syncRepository.pendingQrPairing()?.let { pending ->
            _state.value = _state.value.copy(qr = pending, status = qrMessage(pending))
            startPolling()
        }
    }

    /** Text read from the QR (scanner or pasted). Validation happens in the repository before any request. */
    fun startQrPairing(qrText: String, deviceName: String) = run("Ghép nối QR thất bại", deviceAuth = false) {
        pollJob?.cancel()
        val progress = syncRepository.startQrPairing(qrText, deviceName)
        _state.value = _state.value.copy(qr = progress, paired = syncRepository.isPaired(), serverUrl = syncRepository.serverUrl())
        if (!progress.status.terminal) startPolling()
        qrMessage(progress)
    }

    fun onScanFailed(message: String?) {
        _state.value = _state.value.copy(status = "Không mở được trình quét QR" + (message?.let { ": $it" } ?: "") +
            ". Có thể dán nội dung mã QR vào ô bên dưới.")
    }

    fun cancelQrPairing() {
        pollJob?.cancel()
        viewModelScope.launch {
            syncRepository.cancelQrPairing()
            _state.value = _state.value.copy(qr = null, status = "Đã hủy phiên ghép nối trên điện thoại. Hãy hủy hoặc từ chối phiên trên Web nếu cần.")
        }
    }

    /** Polls every 3 s until Web confirms or the session ends; a network error keeps polling until expiry. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_MILLIS)
                val current = _state.value.qr ?: return@launch
                val result = runCatching { syncRepository.checkQrPairing() }
                val progress = result.getOrNull()
                if (progress != null) {
                    _state.value = _state.value.copy(qr = progress, status = qrMessage(progress),
                        paired = syncRepository.isPaired(), serverUrl = syncRepository.serverUrl())
                    if (progress.status.terminal) { refreshStatus(); return@launch }
                } else {
                    val error = result.exceptionOrNull()
                    val permanent = error !is IOException || (error is SyncHttpException && !error.temporary)
                    val expired = System.currentTimeMillis() > current.expiresAtMillis + EXPIRY_GRACE_MILLIS
                    _state.value = _state.value.copy(status = "Kiểm tra trạng thái ghép nối thất bại: ${error?.message}")
                    if (permanent || expired) {
                        if (expired) syncRepository.cancelQrPairing()
                        _state.value = _state.value.copy(qr = syncRepository.pendingQrPairing())
                        return@launch
                    }
                }
            }
        }
    }

    private fun qrMessage(progress: QrPairingProgress): String = when (progress.status) {
        QrPairingStatus.CLAIMED, QrPairingStatus.AWAITING_CONFIRMATION ->
            "Đang chờ xác nhận trên Web. So sánh tên thiết bị và nhập mã ${progress.confirmationCode} trên Web."
        QrPairingStatus.PAIRED -> "Đã ghép nối với ${progress.serverUrl}. Bấm \"Đồng bộ ngay\" để gửi lịch sử học và tải nội dung."
        QrPairingStatus.EXPIRED -> "Mã QR đã hết hạn. Hãy tạo mã mới trên Web và quét lại."
        QrPairingStatus.REJECTED -> "Web đã từ chối thiết bị này."
        QrPairingStatus.CANCELLED -> "Phiên QR đã bị hủy trên Web (có thể do đã tạo mã mới)."
        QrPairingStatus.FAILED -> "Nhập sai mã xác nhận quá nhiều lần. Hãy tạo mã QR mới."
    }

    fun pair(serverUrl: String, pairingKey: String, deviceName: String) = run("Ghép nối thất bại") {
        syncRepository.pair(serverUrl, pairingKey, deviceName).also {
            _state.value = _state.value.copy(paired = syncRepository.isPaired(), serverUrl = syncRepository.serverUrl())
        }
    }

    fun pushReviews() = run("Đồng bộ thất bại") { syncRepository.pushReviews() }

    /** [deviceAuth]: a 401/403 means the stored device credential was revoked (not true while pairing). */
    private fun run(errorPrefix: String, deviceAuth: Boolean = true, task: suspend () -> String) {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, status = "")
        viewModelScope.launch {
            val message = runCatching { task() }.getOrElse { error ->
                when {
                    deviceAuth && error is SyncHttpException && (error.code == 401 || error.code == 403) ->
                        "Xác thực thiết bị hết hạn hoặc đã bị thu hồi: ${error.message} Hãy ngắt kết nối và ghép nối lại."
                    error is SyncHttpException -> "$errorPrefix: ${error.message}"
                    error is IOException -> "Offline / lỗi mạng: ${error.message}"
                    else -> "$errorPrefix: ${error.message}"
                }
            }
            val detail = runCatching { syncRepository.status() }.getOrNull()
            _state.value = _state.value.copy(running = false, paired = syncRepository.isPaired(), status = message,
                lastSuccessAt = detail?.lastSuccessAtMillis, downloadedCount = detail?.downloadedCount ?: 0,
                knowledgeCount = detail?.knowledgeCount ?: 0, pendingEvents = detail?.pendingEvents ?: 0, lastError = detail?.lastError,
                pendingHistory = detail?.pendingHistory ?: 0, skippedHistory = detail?.skippedHistory ?: 0)
        }
    }

    fun disconnect() = run("Đã xóa credential cục bộ, nhưng máy chủ chưa xác nhận thu hồi") {
        syncRepository.disconnect()
        _state.value = _state.value.copy(qr = null)
        "Đã ngắt kết nối và thu hồi thiết bị trên máy chủ."
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            val detail = runCatching { syncRepository.status() }.getOrNull() ?: return@launch
            _state.value = _state.value.copy(lastSuccessAt = detail.lastSuccessAtMillis,
                downloadedCount = detail.downloadedCount, knowledgeCount = detail.knowledgeCount,
                pendingEvents = detail.pendingEvents, lastError = detail.lastError,
                pendingHistory = detail.pendingHistory, skippedHistory = detail.skippedHistory)
        }
    }

    private companion object {
        const val POLL_MILLIS = 3_000L
        const val EXPIRY_GRACE_MILLIS = 60_000L
    }
}
