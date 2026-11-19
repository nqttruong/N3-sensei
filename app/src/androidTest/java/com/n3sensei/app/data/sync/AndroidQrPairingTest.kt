package com.n3sensei.app.data.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.repository.QrPairingStatus
import java.io.File
import com.n3sensei.app.domain.repository.SyncHttpException
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QR v1 client against an in-memory server that follows `backend/app/sync/qr.py` and `router.py`
 * (claim → ready → Web confirm → PAIRED → bearer pull/push). Not a substitute for the real server.
 */
@RunWith(AndroidJUnit4::class)
class AndroidQrPairingTest {
    private val base = "https://jlpt.example.test"
    private val sessionId = UUID.randomUUID().toString()
    private val challenge = "Q".repeat(20) + "_abc-0123456789xyzXYZ"
    private lateinit var context: Context
    private lateinit var db: N3Database
    private val dbName = "qr-pairing-test"

    private object NoBackgroundSync : BackgroundSyncScheduler {
        var scheduled = 0; var cancelled = 0
        override fun schedule() { scheduled++ }
        override fun cancel() { cancelled++ }
    }

    /** Minimal server: one QR session, devices by credential hash, push dedupe by request_id. */
    private inner class FakeServer : SyncTransport {
        var status = "WAITING_FOR_DEVICE"
        var credentialHash: String? = null
        var claimBodies = mutableListOf<JSONObject>()
        val devices = mutableSetOf<String>()
        val received = linkedMapOf<String, JSONObject>()
        val requests = mutableListOf<String>()
        var failNextClaimWithNetwork = false
        var dropAckFor: String? = null
        val ticket get() = sha256("$sessionId|$challenge|${claimBodies.last().getString("device_credential")}")

        override fun request(url: String, method: String, pairingKey: String?, bearer: String?, body: String?, claimTicket: String?): String {
            requests += "$method ${url.removePrefix(base)}"
            check(url.startsWith("$base/api/v1/sync/")) { "unexpected host $url" }
            val path = url.removePrefix("$base/api/v1/sync")
            fun ok(data: Any) = JSONObject().put("data", data).toString()
            fun session() = JSONObject().put("id", sessionId).put("status", status).put("device_name", claimBodies.lastOrNull()?.getString("device_name"))
                .put("device_id", if (status == "PAIRED") "dev-1" else JSONObject.NULL)
            return when {
                path == "/qr-sessions/$sessionId/claim" -> {
                    if (failNextClaimWithNetwork) { failNextClaimWithNetwork = false; throw IOException("offline") }
                    val input = JSONObject(body!!)
                    assertEquals(setOf("challenge", "device_name", "device_credential"), input.keys().asSequence().toSet())
                    if (input.getString("challenge") != challenge) throw SyncHttpException(401, "QR challenge không hợp lệ")
                    val hash = sha256(input.getString("device_credential"))
                    if (status in setOf("CLAIMED", "AWAITING_CONFIRMATION") && hash != credentialHash) throw SyncHttpException(409, "Phiên QR đã được thiết bị khác nhận")
                    if (status !in setOf("WAITING_FOR_DEVICE", "CLAIMED", "AWAITING_CONFIRMATION")) throw SyncHttpException(409, "Phiên QR không nhận claim: $status")
                    claimBodies += input
                    if (status == "WAITING_FOR_DEVICE") { status = "CLAIMED"; credentialHash = hash }
                    ok(JSONObject().put("status", status).put("claim_ticket", ticket).put("confirmation_code", "042731"))
                }
                path == "/qr-sessions/$sessionId/ready" -> {
                    if (claimTicket != ticket) throw SyncHttpException(401, "Claim ticket không hợp lệ")
                    if (status == "CLAIMED") status = "AWAITING_CONFIRMATION"
                    ok(session())
                }
                path == "/qr-sessions/$sessionId" && method == "GET" -> {
                    if (claimTicket != ticket) throw SyncHttpException(401, "Claim ticket không hợp lệ")
                    ok(session())
                }
                path.startsWith("/changes?") -> {
                    if (bearer == null || sha256(bearer) !in devices) throw SyncHttpException(401, "Invalid device credential")
                    ok(JSONObject().put("data", JSONArray()).put("next_cursor", 0).put("has_more", false))
                }
                path == "/push" -> {
                    if (bearer == null || sha256(bearer) !in devices) throw SyncHttpException(401, "Invalid device credential")
                    val events = JSONObject(body!!).getJSONArray("events")
                    val accepted = JSONArray(); val duplicates = JSONArray()
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i); val id = event.getString("request_id")
                        UUID.fromString(id) // the server rejects the whole request with 422 otherwise
                        assertTrue(event.getString("item_type") in setOf("VOCABULARY", "KANJI", "GRAMMAR", "JLPT_REVIEW_ITEM"))
                        if (id == dropAckFor) continue
                        if (received.put(id, event) == null) accepted.put(id) else duplicates.put(id)
                    }
                    ok(JSONObject().put("accepted", accepted).put("duplicates", duplicates))
                }
                path == "/disconnect" -> { devices.clear(); ok(JSONObject().put("revoked", true)) }
                else -> error("unexpected $method $path")
            }
        }

        /** What the Web admin does after the human compares name and code. */
        fun confirmOnWeb(code: String) {
            check(status == "AWAITING_CONFIRMATION" && code == "042731")
            devices += credentialHash!!
            status = "PAIRED"
        }
    }

    private fun qr(baseUrl: String = base, expires: Instant = Instant.now().plusSeconds(300)) = JSONObject()
        .put("version", 1).put("base_url", baseUrl).put("session_id", sessionId).put("challenge", challenge)
        .put("expires_at", expires.toString()).toString()

    private fun client(server: SyncTransport) = AndroidSyncClient(context, db, server, NoBackgroundSync)

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("android_sync", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase(dbName)
        db = N3Database.create(context, dbName)
        NoBackgroundSync.scheduled = 0; NoBackgroundSync.cancelled = 0
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
        context.getSharedPreferences("android_sync", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun fullPairingThenBearerSyncUploadsHistory() = runTest {
        val server = FakeServer()
        val sync = client(server)
        repeat(3) { i -> db.progressDao().insertRecord(record(i, UUID.randomUUID().toString())) }

        val started = sync.startQrPairing(qr(), "N3 Sensei test")
        assertEquals(QrPairingStatus.AWAITING_CONFIRMATION, started.status)
        assertEquals("042731", started.confirmationCode)
        assertEquals("N3 Sensei test", started.deviceName)
        val credential = server.claimBodies.single().getString("device_credential")
        assertTrue(credential.matches(Regex("^[A-Za-z0-9_-]{48,200}$")))
        assertFalse(sync.isPaired())

        assertEquals(QrPairingStatus.AWAITING_CONFIRMATION, sync.checkQrPairing().status) // not confirmed yet
        server.confirmOnWeb(started.confirmationCode!!)
        val paired = sync.checkQrPairing()
        assertEquals(QrPairingStatus.PAIRED, paired.status)
        assertNull(paired.confirmationCode)
        assertTrue(sync.isPaired())
        assertEquals(base, sync.serverUrl())
        assertNull(sync.pendingQrPairing())
        assertEquals(1, NoBackgroundSync.scheduled)
        assertEquals(3, sync.status().pendingHistory)

        sync.syncNow()
        assertEquals(3, server.received.size)
        assertEquals(0, sync.status().pendingHistory)
        val event = server.received.values.first()
        assertEquals("VOCABULARY", event.getString("item_type"))
        assertTrue(event.getString("reviewed_at").endsWith("Z"))
        // A second sync sends nothing again; the queue cursor advanced only after acknowledgement.
        val pushesBefore = server.requests.count { it == "POST /api/v1/sync/push" }
        sync.syncNow()
        assertEquals(pushesBefore, server.requests.count { it == "POST /api/v1/sync/push" })
        assertEquals(3, server.received.size)

        sync.disconnect()
        assertFalse(sync.isPaired())
        assertEquals(1, NoBackgroundSync.cancelled)
    }

    @Test fun networkFailureKeepsCredentialAndRetryReusesIt() = runTest {
        val server = FakeServer().apply { failNextClaimWithNetwork = true }
        val sync = client(server)
        runCatching { sync.startQrPairing(qr(), "Phone") }.onSuccess { error("expected offline failure") }
        val stored = context.getSharedPreferences("android_sync", Context.MODE_PRIVATE).getString("qr_pending", null)
        assertTrue(stored != null)
        sync.startQrPairing(qr(), "Phone") // rescanning the same QR
        sync.startQrPairing(qr(), "Phone") // identical retry is accepted by the server
        val credentials = server.claimBodies.map { it.getString("device_credential") }.toSet()
        assertEquals(1, credentials.size)
        assertEquals("AWAITING_CONFIRMATION", server.status)
    }

    @Test fun rejectedClaimOrSessionForgetsPendingState() = runTest {
        val wrong = qr().replace(challenge, "W".repeat(30))
        val server = FakeServer()
        val sync = client(server)
        val error = runCatching { sync.startQrPairing(wrong, "Phone") }.exceptionOrNull()
        assertTrue(error is SyncHttpException && error.code == 401)
        assertNull(context.getSharedPreferences("android_sync", Context.MODE_PRIVATE).getString("qr_pending", null))

        sync.startQrPairing(qr(), "Phone")
        server.status = "REJECTED"
        assertEquals(QrPairingStatus.REJECTED, sync.checkQrPairing().status)
        assertFalse(sync.isPaired())
        assertNull(sync.pendingQrPairing())
    }

    @Test fun invalidQrMakesNoRequest() = runTest {
        val server = FakeServer()
        val sync = client(server)
        listOf(qr(baseUrl = "http://jlpt.example.test"), qr(baseUrl = "https://127.0.0.1:18080"),
            qr(expires = Instant.now().minusSeconds(1)), "{\"version\":1}", "hello").forEach { text ->
            assertTrue(runCatching { sync.startQrPairing(text, "Phone") }.exceptionOrNull() is QrPayloadException)
        }
        assertTrue(server.requests.isEmpty())
    }

    @Test fun secretsAreNotStoredInPlainText() = runTest {
        val server = FakeServer()
        val sync = client(server)
        sync.startQrPairing(qr(), "Phone")
        val credential = server.claimBodies.single().getString("device_credential")
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/android_sync.xml").readText()
        assertFalse(prefsFile.contains(credential))
        assertFalse(prefsFile.contains(server.ticket))
        assertFalse(prefsFile.contains(challenge))
        assertFalse(prefsFile.contains("042731"))
        server.confirmOnWeb("042731")
        sync.checkQrPairing()
        assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/android_sync.xml").readText().contains(credential))
    }

    @Test fun historyWithoutUuidIsSkippedAndMissingAckKeepsQueue() = runTest {
        val server = FakeServer()
        val sync = client(server)
        val good = UUID.randomUUID().toString()
        val lost = UUID.randomUUID().toString()
        db.progressDao().insertRecord(record(0, "restored-from-old-backup"))
        db.progressDao().insertRecord(record(1, good))
        db.progressDao().insertRecord(record(2, lost))
        sync.startQrPairing(qr(), "Phone"); server.confirmOnWeb("042731"); sync.checkQrPairing()

        server.dropAckFor = lost
        assertTrue(runCatching { sync.syncNow() }.isFailure)
        assertEquals(3, sync.status().pendingHistory) // nothing acknowledged as a whole page: cursor kept

        server.dropAckFor = null
        sync.syncNow()
        assertEquals(setOf(good, lost), server.received.keys)
        val status = sync.status()
        assertEquals(0, status.pendingHistory)
        assertEquals(1, status.skippedHistory)
    }

    private fun record(i: Int, requestId: String) = ReviewRecordEntity(
        itemType = ItemType.entries[i % 3], itemId = "item-$i", rating = ReviewRating.GOOD,
        reviewedAt = 1_763_000_000_000L + i, intervalBeforeMinutes = 0, intervalAfterMinutes = 1_440,
        dueAtAfter = 1_763_086_400_000L + i, requestId = requestId,
    )

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
