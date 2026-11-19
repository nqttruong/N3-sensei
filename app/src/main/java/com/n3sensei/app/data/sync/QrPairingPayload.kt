package com.n3sensei.app.data.sync

import java.net.InetAddress
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * QR bootstrap v1 of JLPT AI Review (`fastapi/docs/android-qr-sync-protocol.md`, `backend/app/sync/qr.py`).
 *
 * The QR is `JSON.stringify(qr_payload)` of `POST /api/v1/sync/admin/qr-sessions`. It holds a short-lived
 * pairing challenge only: no admin key, Caddy password, device token or learning data. Everything is
 * checked here, before the phone contacts any server, as the protocol requires.
 */
data class QrPairingPayload(
    /** HTTPS origin without `/api/v1`. */
    val baseUrl: String,
    val sessionId: String,
    val challenge: String,
    val expiresAt: Instant,
) {
    /** The challenge is a secret: never printed. */
    override fun toString(): String = "QrPairingPayload(baseUrl=$baseUrl, sessionId=$sessionId, expiresAt=$expiresAt)"

    companion object {
        const val VERSION = 1
        private val KEYS = setOf("version", "base_url", "session_id", "challenge", "expires_at")
        private val CHALLENGE = Regex("^[A-Za-z0-9_-]{24,100}$")
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** Parses and validates; the failure message is safe to show (it never contains the challenge). */
        fun parse(text: String, now: Instant = Instant.now()): Result<QrPairingPayload> = runCatching {
            val root = runCatching { Json.parseToJsonElement(text.trim()) }.getOrNull() as? JsonObject
                ?: throw QrPayloadException("Mã QR không phải mã ghép nối JLPT AI Review.")
            if (root.keys != KEYS) throw QrPayloadException("Mã QR có trường không mong đợi hoặc thiếu trường bắt buộc.")
            val fields = root.mapValues { (_, value) -> value as? JsonPrimitive ?: throw QrPayloadException("Mã QR sai định dạng.") }
            val version = fields.getValue("version").takeUnless { it.isString }?.intOrNull
            if (version != VERSION) throw QrPayloadException("Phiên bản QR không được hỗ trợ (chỉ hỗ trợ v$VERSION).")
            val baseUrl = validateBaseUrl(fields.string("base_url"))
            val sessionId = fields.string("session_id")
            if (!UUID_PATTERN.matches(sessionId)) throw QrPayloadException("Mã phiên QR không hợp lệ.")
            UUID.fromString(sessionId)
            val challenge = fields.string("challenge")
            if (!CHALLENGE.matches(challenge)) throw QrPayloadException("Mã QR không hợp lệ.")
            val expiresAt = try {
                OffsetDateTime.parse(fields.string("expires_at")).toInstant()
            } catch (_: DateTimeParseException) {
                throw QrPayloadException("Thời hạn trong mã QR không hợp lệ.")
            }
            if (!expiresAt.isAfter(now)) throw QrPayloadException("Mã QR đã hết hạn. Hãy tạo mã mới trên Web.")
            QrPairingPayload(baseUrl, sessionId.lowercase(), challenge, expiresAt)
        }

        private fun Map<String, JsonPrimitive>.string(key: String): String =
            getValue(key).takeIf { it.isString }?.content ?: throw QrPayloadException("Mã QR sai định dạng ($key).")

        /** HTTPS origin only: no credentials, path, query, fragment, localhost or loopback (as the server). */
        internal fun validateBaseUrl(value: String): String {
            val uri = runCatching { URI(value) }.getOrNull() ?: throw QrPayloadException("Địa chỉ máy chủ trong QR không hợp lệ.")
            if (!"https".equals(uri.scheme, ignoreCase = true)) throw QrPayloadException("Máy chủ trong QR phải dùng HTTPS.")
            val host = uri.host?.takeIf { it.isNotBlank() } ?: throw QrPayloadException("Địa chỉ máy chủ trong QR không hợp lệ.")
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in setOf(null, "", "/")) {
                throw QrPayloadException("Địa chỉ máy chủ trong QR phải là một origin HTTPS, không kèm đường dẫn hay thông tin đăng nhập.")
            }
            if (host.equals("localhost", ignoreCase = true) || isLoopbackLiteral(host)) {
                throw QrPayloadException("QR trỏ tới localhost/loopback; điện thoại không thể dùng địa chỉ này.")
            }
            return value.trimEnd('/')
        }

        /** Only IP literals are checked, so no DNS lookup happens here. */
        private fun isLoopbackLiteral(host: String): Boolean {
            val bare = host.removePrefix("[").removeSuffix("]")
            val literal = bare.matches(Regex("^[0-9.]+$")) || bare.contains(':')
            return literal && runCatching { InetAddress.getByName(bare).isLoopbackAddress }.getOrDefault(false)
        }
    }
}

class QrPayloadException(message: String) : IllegalArgumentException(message)
