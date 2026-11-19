package com.n3sensei.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.data.sync.QrPairingPayload
import com.n3sensei.app.domain.model.JlptKnowledge
import com.n3sensei.app.domain.model.JlptMistakeDetail
import com.n3sensei.app.domain.model.JlptQuestion
import com.n3sensei.app.domain.model.JlptReviewItem
import com.n3sensei.app.domain.repository.QrPairingProgress
import com.n3sensei.app.domain.repository.QrPairingStatus
import com.n3sensei.app.domain.repository.SyncRepository
import com.n3sensei.app.domain.repository.SyncStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Settings section with a scripted repository: paste QR → code shown → Web confirms → paired → sync. */
@RunWith(AndroidJUnit4::class)
class JlptWebSyncSectionTest {
    @get:Rule val composeRule = createComposeRule()

    private class ScriptedRepository : SyncRepository {
        var paired = false
        var confirmedOnWeb = false
        var syncCalls = 0
        var receivedName: String? = null
        private var progress: QrPairingProgress? = null
        override fun isPaired() = paired
        override fun serverUrl() = if (paired) "https://jlpt.example.test" else ""
        override suspend fun pair(serverUrl: String, pairingKey: String, deviceName: String) = error("not used")
        override suspend fun startQrPairing(qrText: String, deviceName: String): QrPairingProgress {
            val payload = QrPairingPayload.parse(qrText).getOrThrow()
            receivedName = deviceName
            return QrPairingProgress(payload.sessionId, payload.baseUrl, deviceName, "042731", QrPairingStatus.AWAITING_CONFIRMATION,
                payload.expiresAt.toEpochMilli()).also { progress = it }
        }
        override suspend fun checkQrPairing(): QrPairingProgress {
            val current = progress!!
            if (!confirmedOnWeb) return current
            paired = true
            return current.copy(status = QrPairingStatus.PAIRED, confirmationCode = null)
        }
        override suspend fun cancelQrPairing() { progress = null }
        override fun pendingQrPairing(): QrPairingProgress? = null
        override suspend fun pushReviews(): String { syncCalls++; return "Đồng bộ hoàn tất: tải 0 thay đổi (cursor 0), gửi 3 review event." }
        override suspend fun syncNow() = pushReviews()
        override suspend fun disconnect() { paired = false }
        override suspend fun status() = SyncStatus(null, 0, 0, 0, null, pendingHistory = if (syncCalls > 0) 0 else 3)
        override fun observeKnowledge(): Flow<List<JlptKnowledge>> = flowOf(emptyList())
        override fun observeMistakes(): Flow<List<JlptQuestion>> = flowOf(emptyList())
        override fun observeReviewItems(): Flow<List<JlptReviewItem>> = flowOf(emptyList())
        override fun observePendingEventCount(): Flow<Int> = flowOf(0)
        override suspend fun mistakeDetail(question: JlptQuestion): JlptMistakeDetail = error("not used")
        override suspend fun recordOfflineReview(item: JlptReviewItem, rating: String) = Unit
    }

    @Test fun pasteQrShowsCodeThenPairsAfterWebConfirmation() {
        val repository = ScriptedRepository()
        val viewModel = AndroidSyncViewModel(repository)
        composeRule.setContent { N3SenseiTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JlptWebSyncSection(viewModel) } } }

        composeRule.onNodeWithTag(JlptWebSyncTags.SCAN).assertIsDisplayed()
        composeRule.onNodeWithTag(JlptWebSyncTags.DEVICE_NAME).performTextReplacement("Điện thoại của tôi")
        composeRule.onNodeWithTag(JlptWebSyncTags.PASTE_TOGGLE).performScrollTo().performClick()
        val qr = """{"version":1,"base_url":"https://jlpt.example.test","session_id":"${UUID.randomUUID()}","challenge":"${"k".repeat(43)}","expires_at":"${Instant.now().plusSeconds(300)}"}"""
        composeRule.onNodeWithTag(JlptWebSyncTags.PASTE_FIELD).performScrollTo().performTextInput(qr)
        composeRule.onNodeWithTag(JlptWebSyncTags.PASTE_SUBMIT).performScrollTo().performClick()

        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("042 731")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(JlptWebSyncTags.CODE).assertIsDisplayed()
        composeRule.onNodeWithText("Tên thiết bị: Điện thoại của tôi").assertIsDisplayed()
        assertEquals("Điện thoại của tôi", repository.receivedName)

        repository.confirmedOnWeb = true
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(JlptWebSyncTags.SYNC_NOW).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Đã ghép nối: https://jlpt.example.test").assertIsDisplayed()
        composeRule.onNodeWithText("Lịch sử ôn tập chờ gửi: 3 lượt · JLPT review chờ gửi: 0").assertIsDisplayed()

        composeRule.onNodeWithTag(JlptWebSyncTags.SYNC_NOW).performClick()
        composeRule.waitUntil(5_000) { repository.syncCalls == 1 }
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("Lịch sử ôn tập chờ gửi: 0 lượt · JLPT review chờ gửi: 0")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun invalidPastedQrShowsReasonAndStaysUnpaired() {
        val repository = ScriptedRepository()
        val viewModel = AndroidSyncViewModel(repository)
        composeRule.setContent { N3SenseiTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JlptWebSyncSection(viewModel) } } }
        composeRule.onNodeWithTag(JlptWebSyncTags.PASTE_TOGGLE).performScrollTo().performClick()
        val httpQr = """{"version":1,"base_url":"http://192.168.1.10:18080","session_id":"${UUID.randomUUID()}","challenge":"${"k".repeat(43)}","expires_at":"${Instant.now().plusSeconds(300)}"}"""
        composeRule.onNodeWithTag(JlptWebSyncTags.PASTE_FIELD).performScrollTo().performTextInput(httpQr)
        composeRule.onNodeWithTag(JlptWebSyncTags.PASTE_SUBMIT).performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("Máy chủ trong QR phải dùng HTTPS.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(JlptWebSyncTags.SCAN).assertIsDisplayed()
        assertEquals(false, repository.paired)
    }
}
