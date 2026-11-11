package com.n3sensei.app.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.domain.repository.SyncRepository
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Explicit, user-triggered sync. Room's learning_progress is read-only to this client. */
class AndroidSyncClient(context: Context, private val progressDao: ProgressDao) : SyncRepository {
    private val preferences = context.applicationContext.getSharedPreferences("android_sync", Context.MODE_PRIVATE)

    override fun isPaired(): Boolean = readToken() != null

    override fun serverUrl(): String = preferences.getString(KEY_SERVER, "").orEmpty()

    override suspend fun pair(serverUrl: String, pairingKey: String, deviceName: String): String = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val response = request(
            url = "$base/api/v1/sync/pair",
            method = "POST",
            pairingKey = pairingKey,
            body = JSONObject().put("device_name", deviceName).toString(),
        )
        val data = JSONObject(response).getJSONObject("data")
        preferences.edit().putString(KEY_SERVER, base).putString(KEY_TOKEN, encrypt(data.getString("access_token")))
            .putLong(KEY_LAST_REVIEW_ID, 0L).apply()
        "Đã ghép nối thiết bị ${data.getString("device_name")}."
    }

    override suspend fun pushReviews(): String = withContext(Dispatchers.IO) {
        val server = preferences.getString(KEY_SERVER, null) ?: error("Hãy ghép nối máy chủ trước.")
        val token = readToken() ?: error("Không đọc được khóa thiết bị. Hãy ghép nối lại.")
        var lastId = preferences.getLong(KEY_LAST_REVIEW_ID, 0L)
        var uploaded = 0
        while (true) {
            val events = progressDao.syncEventsAfter(lastId, 100)
            if (events.isEmpty()) break
            val body = JSONObject().put("events", JSONArray().apply { events.forEach { put(it.toApiJson()) } })
            val response = JSONObject(request("$server/api/v1/sync/push", "POST", bearer = token, body = body.toString()))
                .getJSONObject("data")
            val acknowledged = response.getJSONArray("accepted").length() + response.getJSONArray("duplicates").length()
            check(acknowledged == events.size) { "Máy chủ chưa xác nhận toàn bộ sự kiện; tiến độ cục bộ vẫn được giữ." }
            lastId = events.last().id
            preferences.edit().putLong(KEY_LAST_REVIEW_ID, lastId).apply()
            uploaded += acknowledged
        }
        "Đã đồng bộ $uploaded sự kiện ôn tập. Tiến độ SRS trên máy vẫn là nguồn sự thật."
    }

    private fun ReviewRecordEntity.toApiJson(): JSONObject = JSONObject()
        .put("request_id", requestId)
        .put("item_type", itemType.name)
        .put("item_id", itemId)
        .put("rating", rating.name)
        .put("reviewed_at", Instant.ofEpochMilli(reviewedAt).toString())
        .put("interval_before_minutes", intervalBeforeMinutes)
        .put("interval_after_minutes", intervalAfterMinutes)
        .put("due_at_after", Instant.ofEpochMilli(dueAtAfter).toString())

    private fun request(
        url: String,
        method: String,
        pairingKey: String? = null,
        bearer: String? = null,
        body: String,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            pairingKey?.let { setRequestProperty("X-Pairing-Key", it) }
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "Sync API trả HTTP $code: $text" }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        require(normalized.startsWith("https://")) {
            "Đồng bộ yêu cầu URL HTTPS để bảo vệ token thiết bị."
        }
        return normalized
    }

    private fun readToken(): String? = preferences.getString(KEY_TOKEN, null)?.let { encrypted ->
        runCatching { decrypt(encrypted) }.getOrNull()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "n3_sensei_sync_device_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val KEY_SERVER = "server_url"
        const val KEY_TOKEN = "device_token"
        const val KEY_LAST_REVIEW_ID = "last_review_record_id"
    }
}
