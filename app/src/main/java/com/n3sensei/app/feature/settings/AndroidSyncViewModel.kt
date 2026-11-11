package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AndroidSyncUiState(
    val paired: Boolean,
    val serverUrl: String,
    val running: Boolean = false,
    val status: String = "",
)

class AndroidSyncViewModel(private val syncRepository: SyncRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        AndroidSyncUiState(syncRepository.isPaired(), syncRepository.serverUrl()),
    )
    val state: StateFlow<AndroidSyncUiState> = _state.asStateFlow()

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
            val message = runCatching { task() }.getOrElse { "$errorPrefix: ${it.message}" }
            _state.value = _state.value.copy(running = false, status = message)
        }
    }
}
