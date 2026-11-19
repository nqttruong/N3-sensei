package com.n3sensei.app.feature.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.n3sensei.app.core.designsystem.component.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object JlptWebSyncTags {
    const val SECTION = "jlpt_sync_section"
    const val SCAN = "jlpt_sync_scan"
    const val PASTE_TOGGLE = "jlpt_sync_paste_toggle"
    const val PASTE_FIELD = "jlpt_sync_paste_field"
    const val PASTE_SUBMIT = "jlpt_sync_paste_submit"
    const val DEVICE_NAME = "jlpt_sync_device_name"
    const val CODE = "jlpt_sync_code"
    const val CANCEL_QR = "jlpt_sync_cancel_qr"
    const val SYNC_NOW = "jlpt_sync_now"
    const val STATUS = "jlpt_sync_status"
}

/**
 * "Đồng bộ JLPT AI Review": QR pairing (v1), manual sync, background sync status and disconnect.
 * The legacy manual key pairing stays available for an older Web version.
 */
@Composable
fun JlptWebSyncSection(viewModel: AndroidSyncViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deviceName by rememberSaveable { mutableStateOf("N3 Sensei · ${Build.MODEL}".take(120)) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var showLegacy by rememberSaveable { mutableStateOf(false) }
    val qr = state.qr?.takeIf { !it.status.terminal }

    SectionHeader("Đồng bộ JLPT AI Review", Modifier.padding(horizontal = 16.dp))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(JlptWebSyncTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (state.paired) "Đã ghép nối: ${state.serverUrl}" else "Chưa ghép nối. Lịch sử học trên điện thoại vẫn được giữ nguyên.",
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            state.paired -> PairedContent(state, viewModel)
            qr != null -> {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Xác nhận trên Web", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("Mở Web → Cài đặt → Đồng bộ Android, kiểm tra tên thiết bị và nhập mã này:", style = MaterialTheme.typography.bodyMedium)
                        val code = qr.confirmationCode.orEmpty()
                        Text(
                            code.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                            modifier = Modifier.testTag(JlptWebSyncTags.CODE).semantics { contentDescription = "Mã xác nhận ${code.toList().joinToString(" ")}" },
                        )
                        Text("Tên thiết bị: ${qr.deviceName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Máy chủ: ${qr.serverUrl}", style = MaterialTheme.typography.bodySmall)
                        Text("Hết hạn lúc ${formatTime(qr.expiresAtMillis)}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Đang chờ Web xác nhận…", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = viewModel::cancelQrPairing, modifier = Modifier.testTag(JlptWebSyncTags.CANCEL_QR)) { Text("Hủy ghép nối") }
                    }
                }
            }
            else -> {
                Text(
                    "Trên Web: Cài đặt → Đồng bộ Android → Tạo mã QR. Mã chỉ chứa địa chỉ HTTPS và mã ghép nối dùng một lần (5 phút), không chứa mật khẩu hay dữ liệu học.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    deviceName, { deviceName = it.take(120) }, label = { Text("Tên thiết bị hiển thị trên Web") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(JlptWebSyncTags.DEVICE_NAME),
                )
                Button(
                    enabled = !state.running && deviceName.isNotBlank(),
                    onClick = { scanQr(context, onText = { viewModel.startQrPairing(it, deviceName) }, onError = viewModel::onScanFailed) },
                    modifier = Modifier.fillMaxWidth().testTag(JlptWebSyncTags.SCAN),
                ) { Text("Quét mã QR") }
                TextButton(onClick = { showPaste = !showPaste }, modifier = Modifier.testTag(JlptWebSyncTags.PASTE_TOGGLE)) {
                    Text(if (showPaste) "Ẩn ô nhập nội dung QR" else "Không quét được? Dán nội dung mã QR")
                }
                if (showPaste) {
                    OutlinedTextField(
                        pasted, { pasted = it }, label = { Text("Nội dung mã QR (JSON)") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag(JlptWebSyncTags.PASTE_FIELD),
                    )
                    OutlinedButton(
                        enabled = !state.running && pasted.isNotBlank() && deviceName.isNotBlank(),
                        onClick = { viewModel.startQrPairing(pasted, deviceName); pasted = "" },
                        modifier = Modifier.testTag(JlptWebSyncTags.PASTE_SUBMIT),
                    ) { Text("Ghép nối bằng nội dung này") }
                }
                TextButton(onClick = { showLegacy = !showLegacy }) {
                    Text(if (showLegacy) "Ẩn ghép nối bằng khóa (cũ)" else "Ghép nối bằng khóa (Web phiên bản cũ)")
                }
                if (showLegacy) LegacyPairing(state.serverUrl, state.running, viewModel)
            }
        }
        if (state.running) CircularProgressIndicator(Modifier.size(24.dp))
        if (state.status.isNotBlank()) {
            Text(
                state.status, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(JlptWebSyncTags.STATUS).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun PairedContent(state: AndroidSyncUiState, viewModel: AndroidSyncViewModel) {
    Button(
        enabled = !state.running, onClick = viewModel::pushReviews,
        modifier = Modifier.fillMaxWidth().testTag(JlptWebSyncTags.SYNC_NOW),
    ) { Text("Đồng bộ ngay") }
    Text("Lịch sử ôn tập chờ gửi: ${state.pendingHistory} lượt · JLPT review chờ gửi: ${state.pendingEvents}", style = MaterialTheme.typography.bodySmall)
    if (state.skippedHistory > 0) {
        Text("${state.skippedHistory} lượt ôn cũ không gửi được (thiếu mã định danh hợp lệ, ví dụ từ bản sao lưu cũ); chúng vẫn nằm trên điện thoại.", style = MaterialTheme.typography.bodySmall)
    }
    Text("Đã tải ${state.downloadedCount} thay đổi · ${state.knowledgeCount} mục kiến thức", style = MaterialTheme.typography.bodySmall)
    Text(
        state.lastSuccessAt?.let { "Đồng bộ thành công gần nhất: ${formatTime(it)}" } ?: "Chưa đồng bộ lần nào.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Khi có mạng, ứng dụng tự đồng bộ khoảng 6 giờ một lần.", style = MaterialTheme.typography.bodySmall)
    state.lastError?.let { Text("Lỗi đồng bộ: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    TextButton(onClick = viewModel::disconnect, enabled = !state.running) { Text("Ngắt kết nối") }
}

@Composable
private fun LegacyPairing(currentServer: String, running: Boolean, viewModel: AndroidSyncViewModel) {
    var serverUrl by remember(currentServer) { mutableStateOf(currentServer) }
    var pairingKey by remember { mutableStateOf("") }
    OutlinedTextField(serverUrl, { serverUrl = it }, label = { Text("URL máy chủ HTTPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(pairingKey, { pairingKey = it }, label = { Text("Khóa ghép nối") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedButton(
        enabled = !running && serverUrl.isNotBlank() && pairingKey.length >= 32,
        onClick = { viewModel.pair(serverUrl, pairingKey, Build.MODEL); pairingKey = "" },
    ) { Text("Ghép nối bằng khóa") }
}

/** Google code scanner (Play services): QR only, the decoded text comes back, no camera permission here. */
private fun scanQr(context: Context, onText: (String) -> Unit, onError: (String?) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onText) ?: onError("mã QR không có nội dung văn bản") }
        .addOnFailureListener { error -> onError(error.message) }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
private fun formatTime(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
