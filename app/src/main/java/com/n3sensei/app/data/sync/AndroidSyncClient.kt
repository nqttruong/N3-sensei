package com.n3sensei.app.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.room.withTransaction
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.data.local.entity.*
import com.n3sensei.app.domain.repository.SyncRepository
import com.n3sensei.app.domain.repository.SyncStatus
import com.n3sensei.app.domain.repository.SyncHttpException
import com.n3sensei.app.domain.repository.QrPairingProgress
import com.n3sensei.app.domain.repository.QrPairingStatus
import java.security.SecureRandom
import com.n3sensei.app.domain.model.JlptKnowledge
import com.n3sensei.app.domain.model.JlptQuestion
import com.n3sensei.app.domain.model.JlptReviewItem
import com.n3sensei.app.domain.model.JlptMistakeDetail
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Explicit HTTPS sync. Remote state is confined to `synced_*`; legacy SRS tables are read-only. */
fun interface SyncTransport {
    /** [claimTicket] is sent as `X-Claim-Ticket` (QR pairing). Non-2xx answers throw [SyncHttpException]. */
    fun request(url: String, method: String, pairingKey: String?, bearer: String?, body: String?, claimTicket: String?): String
}

class HttpsSyncTransport : SyncTransport {
    override fun request(url: String, method: String, pairingKey: String?, bearer: String?, body: String?, claimTicket: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 20_000; doOutput = body != null
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json")
            pairingKey?.let { setRequestProperty("X-Pairing-Key", it) }
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            claimTicket?.let { setRequestProperty("X-Claim-Ticket", it) }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).opt("detail") as? String }.getOrNull()?.take(200)
                throw SyncHttpException(code, detail, connection.getHeaderField("Retry-After")?.toLongOrNull())
            }
            text
        } finally { connection.disconnect() }
    }
}

class AndroidSyncClient(
    context: Context,
    private val database: N3Database,
    private val transport: SyncTransport = HttpsSyncTransport(),
    private val clock: () -> Instant = Instant::now,
    private val random: SecureRandom = SecureRandom(),
) : SyncRepository {
    private val progressDao: ProgressDao = database.progressDao()
    private val syncDao = database.syncDao()
    private val preferences = context.applicationContext.getSharedPreferences("android_sync", Context.MODE_PRIVATE)

    override fun isPaired(): Boolean = readToken() != null
    override fun serverUrl(): String = preferences.getString(KEY_SERVER, "").orEmpty()

    override suspend fun pair(serverUrl: String, pairingKey: String, deviceName: String): String = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val response = request("$base/api/v1/sync/pair", "POST", pairingKey = pairingKey,
            body = JSONObject().put("device_name", deviceName).toString())
        val data = JSONObject(response).getJSONObject("data")
        storeDeviceCredential(base, data.getString("access_token"))
        "Đã ghép nối thiết bị ${data.getString("device_name")}. Hãy đồng bộ để tải nội dung."
    }

    override suspend fun startQrPairing(qrText: String, deviceName: String): QrPairingProgress = withContext(Dispatchers.IO) {
        check(!isPaired()) { "Thiết bị đã ghép nối. Hãy ngắt kết nối trước khi ghép nối lại." }
        val payload = QrPairingPayload.parse(qrText, clock()).getOrThrow()
        val name = deviceName.trim().take(120)
        require(name.isNotEmpty()) { "Tên thiết bị không được để trống." }
        // The credential is generated and stored before the claim; a retry of the same session reuses it.
        val pending = readPendingQr()?.takeIf { it.sessionId == payload.sessionId && it.serverUrl == payload.baseUrl }
            ?: PendingQr(payload.sessionId, payload.baseUrl, name, newDeviceCredential(), null, null, payload.expiresAt.toEpochMilli())
        writePendingQr(pending)
        val claimed = try {
            val data = JSONObject(request("${pending.serverUrl}/api/v1/sync/qr-sessions/${pending.sessionId}/claim", "POST",
                body = JSONObject().put("challenge", payload.challenge).put("device_name", pending.deviceName)
                    .put("device_credential", pending.credential).toString())).getJSONObject("data")
            val ticket = data.getString("claim_ticket")
            val code = data.getString("confirmation_code")
            require(TICKET.matches(ticket) && CODE.matches(code)) { "Máy chủ trả dữ liệu ghép nối không hợp lệ." }
            pending.copy(claimTicket = ticket, confirmationCode = code).also(::writePendingQr)
        } catch (error: Exception) {
            // A rejected claim (bad/used QR, other device) cannot succeed with this credential; a network error can.
            if (error !is IOException || (error is SyncHttpException && !error.temporary)) clearPendingQr()
            throw error
        }
        val ready = JSONObject(request("${claimed.serverUrl}/api/v1/sync/qr-sessions/${claimed.sessionId}/ready", "POST",
            claimTicket = claimed.claimTicket)).getJSONObject("data")
        handleSessionStatus(claimed, ready.getString("status"), ready)
    }

    override suspend fun checkQrPairing(): QrPairingProgress = withContext(Dispatchers.IO) {
        val pending = readPendingQr() ?: error("Không có phiên ghép nối QR đang chờ.")
        val ticket = pending.claimTicket ?: run { clearPendingQr(); error("Phiên QR chưa được nhận. Hãy quét lại mã.") }
        val data = try {
            JSONObject(request("${pending.serverUrl}/api/v1/sync/qr-sessions/${pending.sessionId}", "GET", claimTicket = ticket)).getJSONObject("data")
        } catch (error: SyncHttpException) {
            if (error.code == 401 || error.code == 404) clearPendingQr()
            throw error
        }
        handleSessionStatus(pending, data.getString("status"), data)
    }

    override suspend fun cancelQrPairing() = withContext(Dispatchers.IO) { clearPendingQr() }

    override fun pendingQrPairing(): QrPairingProgress? = readPendingQr()?.takeIf { it.claimTicket != null }?.toProgress(QrPairingStatus.AWAITING_CONFIRMATION)

    private suspend fun handleSessionStatus(pending: PendingQr, rawStatus: String, data: JSONObject): QrPairingProgress {
        val status = runCatching { QrPairingStatus.valueOf(rawStatus) }.getOrNull()
            ?: error("Trạng thái phiên QR không mong đợi: ${rawStatus.take(40)}")
        when {
            status == QrPairingStatus.PAIRED -> {
                require(!data.isNull("device_id")) { "Máy chủ báo PAIRED nhưng không có thiết bị." }
                storeDeviceCredential(pending.serverUrl, pending.credential)
                clearPendingQr()
            }
            status.terminal -> clearPendingQr()
        }
        return pending.toProgress(status)
    }

    /** Stores the bearer credential; a different server than the one Room mirrors starts a clean pull and re-upload. */
    private suspend fun storeDeviceCredential(base: String, credential: String) {
        val dataServer = preferences.getString(KEY_DATA_SERVER, null) ?: preferences.getString(KEY_SERVER, null)
        if (dataServer != base) {
            database.withTransaction { syncDao.putCheckpoint(SyncCheckpointEntity()) }
            preferences.edit { putLong(KEY_LAST_REVIEW_ID, 0L) }
        }
        preferences.edit { putString(KEY_SERVER, base).putString(KEY_DATA_SERVER, base)
            .putString(KEY_TOKEN, encrypt(credential)) }
    }

    override suspend fun syncNow(): String = withContext(Dispatchers.IO) {
        val server = preferences.getString(KEY_SERVER, null) ?: error("Hãy ghép nối máy chủ trước.")
        val token = readToken() ?: error("Khóa thiết bị không còn dùng được. Hãy ghép nối lại.")
        try {
            val downloaded = pullChanges(server, token)
            val uploaded = uploadReviewEvents(server, token)
            val checkpoint = syncDao.checkpoint(SyncCheckpointEntity.CHECKPOINT_ID)?.copy(
                lastSuccessAtEpochMillis = System.currentTimeMillis(), lastError = null,
            ) ?: SyncCheckpointEntity(lastSuccessAtEpochMillis = System.currentTimeMillis())
            syncDao.putCheckpoint(checkpoint)
            "Đồng bộ hoàn tất: tải $downloaded thay đổi (cursor ${checkpoint?.cursor ?: 0}), gửi $uploaded review event."
        } catch (error: Exception) {
            runCatching {
                val current = syncDao.checkpoint(SyncCheckpointEntity.CHECKPOINT_ID) ?: SyncCheckpointEntity()
                syncDao.putCheckpoint(current.copy(lastError = error.message?.take(300)))
            }
            throw error
        }
    }

    /** Backward compatible Settings action now performs full pull + upload, not upload-only. */
    override suspend fun pushReviews(): String = syncNow()

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val server = preferences.getString(KEY_SERVER, null)
        val token = readToken()
        try {
            if (server != null && token != null) request("$server/api/v1/sync/disconnect", "POST", bearer = token)
        } finally {
            preferences.edit { remove(KEY_TOKEN).remove(KEY_SERVER) }
        }
    }

    override suspend fun status(): SyncStatus {
        val checkpoint = syncDao.checkpoint(SyncCheckpointEntity.CHECKPOINT_ID)
        return SyncStatus(checkpoint?.lastSuccessAtEpochMillis, checkpoint?.downloadedCount ?: 0L,
            syncDao.knowledgeCount(), syncDao.pendingCount(), checkpoint?.lastError)
    }

    override fun observeKnowledge(): Flow<List<JlptKnowledge>> = syncDao.observeKnowledgeItems().map { rows ->
        rows.map { row ->
            val links = JSONArray(row.linkedQuestionIdsJson).strings().map(::namespaceServerId)
            JlptKnowledge(row.id, row.knowledgeType, row.title, row.content, links)
        }
    }
    override fun observeMistakes(): Flow<List<JlptQuestion>> = syncDao.observeMistakes().map { rows -> rows.map { JlptQuestion(it.id, it.questionNumber, it.content, it.options, it.answerIndex) } }
    override fun observeReviewItems(): Flow<List<JlptReviewItem>> = syncDao.observeReviewItems().map { rows -> rows.map { JlptReviewItem(it.id, it.serverId, it.reviewType, it.sourceQuestionId, it.knowledgeId, it.localIntervalMinutes, it.localStepIndex) } }
    override fun observePendingEventCount(): Flow<Int> = syncDao.observePendingCount()
    override suspend fun mistakeDetail(question: JlptQuestion): JlptMistakeDetail {
        val explanation = syncDao.explanation(question.id)
        return JlptMistakeDetail(question, syncDao.latestAnswer(question.id)?.selectedIndex,
            explanation?.takeIf { it.available }?.explanationJson)
    }
    override suspend fun recordOfflineReview(item: JlptReviewItem, rating: String) = withContext(Dispatchers.IO) {
        require(rating in setOf("AGAIN", "HARD", "GOOD", "EASY"))
        val now = System.currentTimeMillis()
        val before = item.intervalMinutes
        val minutes = when (rating) {
            "AGAIN" -> 10L
            "HARD" -> maxOf(60L, before * 2)
            "GOOD" -> maxOf(1_440L, if (before == 0L) 4_320L else before * 3)
            else -> maxOf(4_320L, if (before == 0L) 10_080L else before * 5)
        }
        val due = now + minutes * 60_000L
        val event = SyncedReviewEventEntity(UUID.randomUUID().toString(), "JLPT_REVIEW_ITEM", item.serverId,
            rating, now, before, minutes, due, now, null, 0, null)
        syncDao.recordOfflineReview(item.id, if (rating == "AGAIN") 0 else (item.stepIndex ?: 0) + 1,
            minutes, due, now, event)
    }

    private suspend fun pullChanges(server: String, token: String): Int {
        var checkpoint = syncDao.checkpoint(SyncCheckpointEntity.CHECKPOINT_ID) ?: SyncCheckpointEntity()
        var cursor = checkpoint.cursor
        var applied = 0
        do {
            val text = requestWithRetry("$server/api/v1/sync/changes?cursor=$cursor&limit=200", "GET", token)
            val page = JSONObject(text).getJSONObject("data")
            val changes = page.getJSONArray("data")
            val next = page.getLong("next_cursor")
            val hasMore = page.getBoolean("has_more")
            require(next >= cursor && (changes.length() == 0 || next > cursor)) { "Sync API returned a non-advancing cursor." }
            val records = (0 until changes.length()).map { changes.getJSONObject(it) }
            var previous = cursor
            for (change in records) {
                val changeCursor = change.getLong("cursor")
                require(changeCursor > previous && changeCursor <= next) { "Sync response is duplicate or out of cursor order." }
                require(change.getString("schema_version") == SUPPORTED_SCHEMA) { "Schema sync không được hỗ trợ: ${change.optString("schema_version")}" }
                previous = changeCursor
            }
            require(records.isEmpty() || records.last().getLong("cursor") == next) { "Page cursor does not match its final change." }
            database.withTransaction {
                records.forEach { applyChange(it) }
                checkpoint = checkpoint.copy(cursor = next, schemaVersion = SUPPORTED_SCHEMA,
                    downloadedCount = checkpoint.downloadedCount + records.size)
                syncDao.putCheckpoint(checkpoint)
            }
            cursor = next
            applied += records.size
            if (hasMore) require(records.isNotEmpty()) { "Sync API says more pages exist but returned an empty page." }
        } while (hasMore)
        return applied
    }

    /** Invalid or unsupported records throw before the enclosing Room page transaction commits. */
    private suspend fun applyChange(change: JSONObject) {
        val cursor = change.getLong("cursor")
        val serverId = change.getString("entity_id")
        val operation = change.getString("operation")
        if (operation == "delete") {
            when (change.getString("entity_type")) {
                "exam" -> syncDao.deleteExam(serverId, cursor)
                "passage" -> syncDao.deletePassage(serverId, cursor)
                "question" -> { syncDao.deleteQuestion(serverId, cursor); syncDao.deleteAttemptAnswersForQuestion(serverId, cursor) }
                "ai_explanation" -> syncDao.deleteExplanation(serverId, cursor)
                "exam_attempt" -> syncDao.deleteAttempt(serverId, cursor)
                "attempt_answer" -> syncDao.deleteAttemptAnswer(serverId, cursor)
                "knowledge_item" -> syncDao.deleteKnowledge(serverId, cursor)
                "review_item" -> syncDao.deleteReviewItem(serverId, cursor)
                "android_review_event", "review_event", "question_knowledge" -> Unit
                else -> error("Unknown sync entity ${change.getString("entity_type")}")
            }
            return
        }
        val data = change.optJSONObject("data") ?: error("Missing payload for ${change.getString("entity_type")} $serverId")
        val id = namespaceServerId(serverId)
        when (change.getString("entity_type")) {
            "exam" -> syncDao.putExam(SyncedExamEntity(id, serverId, data.requiredString("title"), data.optString("category", ""), data.requiredString("level"), data.optNullableString("description"), data.optNullableString("source_url"), data.optString("status", ""), false, cursor))
            "passage" -> syncDao.putPassage(SyncedPassageEntity(id, serverId, namespaceServerId(data.requiredString("exam_id")), data.requiredString("text"), false, cursor))
            "question" -> {
                val verified = data.optBoolean("answer_verified", false)
                val answer = if (verified && !data.isNull("answer_index")) data.getInt("answer_index") else null
                val options = data.optJSONArray("options") ?: JSONArray()
                require(!verified || answer == null || answer in 0 until options.length()) { "Verified answer index is outside options." }
                syncDao.putQuestion(SyncedQuestionEntity(id, serverId, namespaceServerId(data.requiredString("exam_id")), data.optNullableString("passage_id")?.let(::namespaceServerId), data.getInt("question_number"), data.getString("question_type"), data.requiredString("question_text"), options.strings(), answer, verified && answer != null, false, cursor))
            }
            "ai_explanation" -> {
                require(data.optString("verification_status") == "approved") { "Unapproved explanation was included in feed." }
                val explanation = data.optJSONObject("explanation") ?: error("Approved explanation payload missing.")
                syncDao.putExplanation(SyncedExplanationEntity(id, serverId, namespaceServerId(data.requiredString("question_id")), data.requiredString("schema_version"), explanation.toString(), true, cursor))
            }
            "exam_attempt" -> syncDao.putAttempt(SyncedAttemptEntity(id, serverId, namespaceServerId(data.requiredString("exam_id")), data.optNullableString("started_at"), data.optNullableString("completed_at"), data.optInt("total_questions"), data.optNullableString("source"), cursor, false))
            "attempt_answer" -> syncDao.putAnswer(SyncedAttemptAnswerEntity(serverId, namespaceServerId(data.requiredString("attempt_id")), namespaceServerId(data.requiredString("question_id")), data.getInt("question_number"), data.optNullableInt("selected_index"), data.optNullableInt("correct_index"), if (data.isNull("is_correct")) null else data.getBoolean("is_correct"), cursor, false))
            "knowledge_item" -> syncDao.putKnowledge(SyncedKnowledgeEntity(id, serverId, data.requiredString("knowledge_type"), data.requiredString("jlpt_level"), data.requiredString("title"), data.requiredString("content"), data.optJSONObject("details")?.toString() ?: "{}", data.optJSONArray("linked_question_ids")?.toString() ?: "[]", data.requiredString("status"), data.optNullableString("merged_into_id")?.let(::namespaceServerId), data.optInt("version", 1), cursor, false))
            "review_item" -> syncDao.putRemoteReview(SyncedReviewItemEntity(id, serverId, data.requiredString("review_type"), data.requiredString("status"), data.optNullableString("source_question_id")?.let(::namespaceServerId), data.optJSONArray("source_question_ids")?.namespaced() ?: "[]", data.optNullableString("knowledge_id")?.let(::namespaceServerId), data.optNullableString("related_knowledge_id")?.let(::namespaceServerId), data.optNullableString("source_explanation_id")?.let(::namespaceServerId), data.optInt("version", 1), cursor, null, 0L, null, null, 0, false))
            "android_review_event", "review_event", "question_knowledge" -> Unit
            else -> error("Unknown sync entity ${change.getString("entity_type")}")
        }
    }

    private suspend fun uploadReviewEvents(server: String, token: String): Int {
        var uploaded = 0
        while (true) {
            val events = syncDao.pendingEvents(100)
            if (events.isEmpty()) break
            val ids = events.map { it.requestId }
            try {
                val response = requestWithRetry("$server/api/v1/sync/push", "POST", token,
                    JSONObject().put("events", JSONArray().apply { events.forEach { put(it.toApiJson()) } }).toString())
                val ack = JSONObject(response).getJSONObject("data")
                val acknowledged = (ack.getJSONArray("accepted").strings() + ack.getJSONArray("duplicates").strings()).toSet()
                require(acknowledged == ids.toSet()) { "Máy chủ chưa xác nhận toàn bộ review event; hàng đợi vẫn được giữ." }
                syncDao.acknowledge(ids, System.currentTimeMillis())
                uploaded += events.size
            } catch (error: Exception) {
                syncDao.failUpload(ids, error.message?.take(300) ?: "Upload failed")
                throw error
            }
        }
        // Existing vocabulary/kanji/grammar history is still uploaded idempotently via request_id.
        var last = preferences.getLong(KEY_LAST_REVIEW_ID, 0L)
        while (true) {
            val events = progressDao.syncEventsAfter(last, 100)
            if (events.isEmpty()) break
            val response = JSONObject(requestWithRetry("$server/api/v1/sync/push", "POST", token,
                JSONObject().put("events", JSONArray().apply { events.forEach { put(it.toApiJson()) } }).toString())).getJSONObject("data")
            require(response.getJSONArray("accepted").length() + response.getJSONArray("duplicates").length() == events.size) { "Server did not acknowledge all history events." }
            last = events.last().id
            preferences.edit { putLong(KEY_LAST_REVIEW_ID, last) }
            uploaded += events.size
        }
        return uploaded
    }

    private fun SyncedReviewEventEntity.toApiJson() = JSONObject().put("request_id", requestId).put("item_type", itemType)
        .put("item_id", itemId).put("rating", rating).put("reviewed_at", Instant.ofEpochMilli(reviewedAtEpochMillis).toString())
        .put("interval_before_minutes", intervalBeforeMinutes).put("interval_after_minutes", intervalAfterMinutes)
        .put("due_at_after", Instant.ofEpochMilli(dueAtAfterEpochMillis).toString())

    private fun com.n3sensei.app.data.local.entity.ReviewRecordEntity.toApiJson() = JSONObject()
        .put("request_id", requestId).put("item_type", itemType.name).put("item_id", itemId).put("rating", rating.name)
        .put("reviewed_at", Instant.ofEpochMilli(reviewedAt).toString()).put("interval_before_minutes", intervalBeforeMinutes)
        .put("interval_after_minutes", intervalAfterMinutes).put("due_at_after", Instant.ofEpochMilli(dueAtAfter).toString())

    private fun requestWithRetry(url: String, method: String, token: String, body: String? = null): String {
        var last: IOException? = null
        repeat(3) { attempt ->
            try { return request(url, method, bearer = token, body = body) }
            catch (error: IOException) {
                if (error is SyncHttpException && !error.temporary) throw error
                last = error; if (attempt < 2) Thread.sleep(300L shl attempt)
            }
        }
        throw last ?: IOException("Network request failed")
    }

    private fun request(url: String, method: String, pairingKey: String? = null, bearer: String? = null, body: String? = null, claimTicket: String? = null): String =
        transport.request(url, method, pairingKey, bearer, body, claimTicket)

    /** 48 random bytes, URL-safe Base64 without padding: 64 characters of `[A-Za-z0-9_-]`. */
    private fun newDeviceCredential(): String {
        val bytes = ByteArray(48).also(random::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Pending QR state, encrypted with the Keystore key: the credential, ticket and code never sit in plain text. */
    private data class PendingQr(
        val sessionId: String, val serverUrl: String, val deviceName: String, val credential: String,
        val claimTicket: String?, val confirmationCode: String?, val expiresAtMillis: Long,
    ) {
        fun toProgress(status: QrPairingStatus) = QrPairingProgress(sessionId, serverUrl, deviceName,
            confirmationCode.takeUnless { status.terminal }, status, expiresAtMillis)
        override fun toString() = "PendingQr(sessionId=$sessionId, serverUrl=$serverUrl)"
    }
    private fun writePendingQr(value: PendingQr) {
        val json = JSONObject().put("session_id", value.sessionId).put("server", value.serverUrl).put("device_name", value.deviceName)
            .put("credential", value.credential).put("ticket", value.claimTicket ?: JSONObject.NULL)
            .put("code", value.confirmationCode ?: JSONObject.NULL).put("expires_at", value.expiresAtMillis)
        preferences.edit(commit = true) { putString(KEY_QR_PENDING, encrypt(json.toString())) }
    }
    private fun readPendingQr(): PendingQr? = preferences.getString(KEY_QR_PENDING, null)?.let { stored ->
        runCatching {
            val json = JSONObject(decrypt(stored))
            PendingQr(json.getString("session_id"), json.getString("server"), json.getString("device_name"), json.getString("credential"),
                json.optNullableString("ticket"), json.optNullableString("code"), json.getLong("expires_at"))
        }.getOrNull()
    }
    private fun clearPendingQr() { preferences.edit(commit = true) { remove(KEY_QR_PENDING) } }

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        require(normalized.startsWith("https://")) { "Đồng bộ yêu cầu URL HTTPS để bảo vệ token thiết bị." }
        return normalized
    }
    private fun JSONObject.requiredString(key: String): String = getString(key).also { require(it.isNotBlank()) }
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.namespaced(): String = JSONArray().apply { strings().forEach { put(namespaceServerId(it)) } }.toString()
    private fun readToken(): String? = preferences.getString(KEY_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(0, GCM_IV_LENGTH)))
        return String(cipher.doFinal(bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)), Charsets.UTF_8)
    }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    private companion object {
        const val SUPPORTED_SCHEMA = "1.0"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"; const val KEY_ALIAS = "n3_sensei_sync_device_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"; const val GCM_IV_LENGTH = 12; const val GCM_TAG_BITS = 128
        const val KEY_SERVER = "server_url"; const val KEY_TOKEN = "device_token"; const val KEY_LAST_REVIEW_ID = "last_review_record_id"
        /** The server whose data the checkpoint and history cursor refer to; kept after disconnect. */
        const val KEY_DATA_SERVER = "data_server_url"
        const val KEY_QR_PENDING = "qr_pending"
        val TICKET = Regex("^[a-f0-9]{64}$")
        val CODE = Regex("^[0-9]{6}$")
    }
}
