package com.n3sensei.app.data.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QR v1 rules from fastapi/docs/android-qr-sync-protocol.md, checked before any network call. */
class QrPairingPayloadTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val challenge = "Abc_def-0123456789ABCDEFGHIJKLMNOPQRSTUVWXY" // 43 chars, like token_urlsafe(32)
    private val session = "6f1c2a4e-9b7d-4c3e-8a21-0f5e6d7c8b9a"

    private fun payload(
        version: String = "1",
        baseUrl: String = "\"https://jlpt.example.test\"",
        sessionId: String = "\"$session\"",
        challengeValue: String = "\"$challenge\"",
        expiresAt: String = "\"2026-11-18T12:05:00.123456Z\"",
        extra: String = "",
    ) = """{"version":$version,"base_url":$baseUrl,"session_id":$sessionId,"challenge":$challengeValue,"expires_at":$expiresAt$extra}"""

    private fun fails(text: String) = assertTrue(QrPairingPayload.parse(text, now).isFailure)

    @Test fun acceptsServerShape() {
        val parsed = QrPairingPayload.parse(payload(), now).getOrThrow()
        assertEquals("https://jlpt.example.test", parsed.baseUrl)
        assertEquals(session, parsed.sessionId)
        assertEquals(challenge, parsed.challenge)
        assertEquals(Instant.parse("2026-11-18T12:05:00.123456Z"), parsed.expiresAt)
    }

    @Test fun acceptsOffsetTimestampsAndPortAndTrailingSlash() {
        val parsed = QrPairingPayload.parse(payload(baseUrl = "\"https://jlpt.example.test:8443/\"", expiresAt = "\"2026-11-18T19:05:00+07:00\""), now).getOrThrow()
        assertEquals("https://jlpt.example.test:8443", parsed.baseUrl)
        assertEquals(Instant.parse("2026-11-18T12:05:00Z"), parsed.expiresAt)
    }

    @Test fun rejectsUnknownVersionAndTypes() {
        fails(payload(version = "2"))
        fails(payload(version = "\"1\""))
        fails(payload(version = "1.5"))
    }

    @Test fun rejectsUnexpectedOrMissingKeys() {
        fails(payload(extra = ",\"access_token\":\"x\""))
        fails("""{"version":1,"base_url":"https://jlpt.example.test","session_id":"$session","expires_at":"2026-11-18T12:05:00Z"}""")
        fails("not json")
        fails("[1,2,3]")
        fails("https://jlpt.example.test")
    }

    @Test fun rejectsNonHttpsLoopbackAndNonOriginUrls() {
        listOf(
            "http://jlpt.example.test", "https://localhost", "https://127.0.0.1:18080", "https://[::1]",
            "https://user:pass@jlpt.example.test", "https://jlpt.example.test/api/v1", "https://jlpt.example.test?x=1",
            "https://jlpt.example.test#f", "ftp://jlpt.example.test", "https://",
        ).forEach { url -> fails(payload(baseUrl = "\"$url\"")) }
    }

    @Test fun rejectsBadSessionChallengeAndExpiry() {
        fails(payload(sessionId = "\"not-a-uuid\""))
        fails(payload(challengeValue = "\"short\""))
        fails(payload(challengeValue = "\"${"a".repeat(101)}\""))
        fails(payload(challengeValue = "\"contains spaces and symbols !!!!!!!!\""))
        fails(payload(expiresAt = "\"2026-11-18T11:59:59Z\""))
        fails(payload(expiresAt = "\"2026-11-18T12:00:00Z\""))
        fails(payload(expiresAt = "\"tomorrow\""))
        fails(payload(expiresAt = "\"2026-11-18T12:05:00\"")) // no timezone
    }

    @Test fun errorMessagesAndToStringNeverContainTheChallenge() {
        val expired = QrPairingPayload.parse(payload(expiresAt = "\"2026-11-18T11:00:00Z\""), now).exceptionOrNull()!!
        assertFalse(expired.message!!.contains(challenge))
        val parsed = QrPairingPayload.parse(payload(), now).getOrThrow()
        assertFalse(parsed.toString().contains(challenge))
    }
}
