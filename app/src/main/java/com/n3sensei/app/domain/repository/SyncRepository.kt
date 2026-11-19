package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.model.JlptKnowledge
import com.n3sensei.app.domain.model.JlptQuestion
import com.n3sensei.app.domain.model.JlptReviewItem
import com.n3sensei.app.domain.model.JlptMistakeDetail
import java.io.IOException
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun isPaired(): Boolean
    fun serverUrl(): String
    /** Legacy manual pairing (`POST /sync/pair` + one-time key); kept for old Web versions. */
    suspend fun pair(serverUrl: String, pairingKey: String, deviceName: String): String
    /** QR v1: validates the scanned text, claims the session and marks it ready. */
    suspend fun startQrPairing(qrText: String, deviceName: String): QrPairingProgress
    /** Asks the server whether Web confirmed the code; stores the device credential once PAIRED. */
    suspend fun checkQrPairing(): QrPairingProgress
    /** Forgets the local pending QR session (the Web operator cancels or rejects it there). */
    suspend fun cancelQrPairing()
    /** A pending QR pairing that survived a process restart, if any. */
    fun pendingQrPairing(): QrPairingProgress?
    suspend fun pushReviews(): String
    suspend fun syncNow(): String
    suspend fun disconnect()
    suspend fun status(): SyncStatus
    fun observeKnowledge(): Flow<List<JlptKnowledge>>
    fun observeMistakes(): Flow<List<JlptQuestion>>
    fun observeReviewItems(): Flow<List<JlptReviewItem>>
    fun observePendingEventCount(): Flow<Int>
    suspend fun mistakeDetail(question: JlptQuestion): JlptMistakeDetail
    suspend fun recordOfflineReview(item: JlptReviewItem, rating: String)
}

data class SyncStatus(
    val lastSuccessAtMillis: Long?,
    val downloadedCount: Long,
    val knowledgeCount: Int,
    val pendingEvents: Int,
    val lastError: String?,
    /** Android review history (vocabulary/kanji/grammar) waiting to be uploaded. */
    val pendingHistory: Int = 0,
    /** History rows the server cannot accept (e.g. restored without a UUID request id); never sent. */
    val skippedHistory: Int = 0,
)

/** Server session states of QR v1 (`sync_qr_status_valid`). */
enum class QrPairingStatus { CLAIMED, AWAITING_CONFIRMATION, PAIRED, EXPIRED, REJECTED, CANCELLED, FAILED;
    val terminal: Boolean get() = this != CLAIMED && this != AWAITING_CONFIRMATION
}

data class QrPairingProgress(
    val sessionId: String,
    val serverUrl: String,
    val deviceName: String,
    /** Six digits the human compares and types on Web; null once the session ended. */
    val confirmationCode: String?,
    val status: QrPairingStatus,
    val expiresAtMillis: Long,
)

/** A non-2xx answer. [detail] is FastAPI's `detail` text when it is a plain string (never a secret). */
class SyncHttpException(val code: Int, val detail: String?, val retryAfterSeconds: Long? = null) :
    IOException("Sync API trả HTTP $code" + (detail?.let { ": $it" } ?: "") + ".") {
    /** Rate limit and server errors may succeed later; anything else will not. */
    val temporary: Boolean get() = code == 429 || code >= 500
}
