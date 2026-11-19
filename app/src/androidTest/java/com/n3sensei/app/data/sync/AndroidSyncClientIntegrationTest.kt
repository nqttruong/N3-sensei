package com.n3sensei.app.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.SyncCheckpointEntity
import com.n3sensei.app.data.local.entity.SyncedReviewEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidSyncClientIntegrationTest {
    private object NoBackgroundSync : BackgroundSyncScheduler { override fun schedule() = Unit; override fun cancel() = Unit }

    private class FakeTransport(private val pages: MutableMap<Long, String>) : SyncTransport {
        val requests = mutableListOf<Pair<String, String>>()
        override fun request(url: String, method: String, pairingKey: String?, bearer: String?, body: String?, claimTicket: String?): String {
            requests += method to url
            if (url.endsWith("/pair")) return """{"data":{"device_id":"device","device_name":"test","access_token":"internal-device-token"}}"""
            if (url.contains("/changes?")) {
                val cursor = Regex("cursor=(\\d+)").find(url)!!.groupValues[1].toLong()
                return pages[cursor] ?: error("unexpected cursor $cursor")
            }
            if (url.endsWith("/push")) {
                val ids = org.json.JSONObject(body!!).getJSONArray("events")
                val accepted = org.json.JSONArray()
                repeat(ids.length()) { accepted.put(ids.getJSONObject(it).getString("request_id")) }
                return org.json.JSONObject().put("data", org.json.JSONObject().put("accepted", accepted).put("duplicates", org.json.JSONArray())).toString()
            }
            error("unexpected URL $url")
        }
    }

    private fun examPage(cursor: Int, id: String, hasMore: Boolean) = """{"data":{"data":[{"cursor":$cursor,"entity_type":"exam","entity_id":"$id","operation":"insert","version":1,"changed_at":"2026-01-01T00:00:00Z","schema_version":"1.0","data":{"id":"$id","level":"N3","category":"grammar","title":"Test Exam","status":"ready"}}],"next_cursor":$cursor,"has_more":$hasMore}}"""
    private fun tombstonePage(id: String) = """{"data":{"data":[{"cursor":1,"entity_type":"exam","entity_id":"$id","operation":"delete","version":2,"changed_at":"2026-01-01T00:00:00Z","schema_version":"1.0","data":null}],"next_cursor":1,"has_more":false}}"""

    @Test fun paginatesAndCommitsContentAndCursorThenUploadsOutbox() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sync-client-integration"
        context.deleteDatabase(name)
        val db = N3Database.create(context, name)
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val fake = FakeTransport(mutableMapOf(
            0L to examPage(1, firstId, true),
            1L to examPage(2, secondId, false),
        ))
        try {
            val now = System.currentTimeMillis()
            db.syncDao().addEvent(SyncedReviewEventEntity(UUID.randomUUID().toString(), "JLPT_REVIEW_ITEM", firstId,
                "GOOD", now, 0, 4_320, now + 4_320 * 60_000L, now, null, 0, null))
            val client = AndroidSyncClient(context, db, fake, NoBackgroundSync)
            client.pair("https://sync.test", "pairing-key", "integration")
            client.syncNow()
            assertNotNull(db.syncDao().syncedExam("jlpt:$firstId"))
            assertNotNull(db.syncDao().syncedExam("jlpt:$secondId"))
            assertEquals(2L, db.syncDao().checkpoint(SyncCheckpointEntity.CHECKPOINT_ID)!!.cursor)
            assertEquals(0, db.syncDao().pendingCount())
            assertEquals(2, fake.requests.count { it.first == "GET" })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun malformedPageDoesNotAdvanceCheckpoint() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sync-client-atomicity"
        context.deleteDatabase(name)
        val db = N3Database.create(context, name)
        val id = UUID.randomUUID().toString()
        val invalid = """{"data":{"data":[{"cursor":1,"entity_type":"question","entity_id":"$id","operation":"insert","version":1,"changed_at":"2026-01-01T00:00:00Z","schema_version":"1.0","data":{"question_text":"missing fields"}}],"next_cursor":1,"has_more":false}}"""
        try {
            val client = AndroidSyncClient(context, db, backgroundSync = NoBackgroundSync, transport = FakeTransport(mutableMapOf(0L to invalid)))
            client.pair("https://sync.test", "pairing-key", "integration")
            runCatching { client.syncNow() }.onSuccess { error("Expected invalid content to fail") }
            assertEquals(0L, db.syncDao().checkpoint(SyncCheckpointEntity.CHECKPOINT_ID)?.cursor ?: 0L)
            assertNull(db.syncDao().syncedExam("jlpt:$id"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun tombstoneMarksMirrorUnavailableWithoutRemovingHistory() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sync-client-tombstone"
        context.deleteDatabase(name)
        val db = N3Database.create(context, name)
        val id = UUID.randomUUID().toString()
        try {
            db.syncDao().putExam(com.n3sensei.app.data.local.entity.SyncedExamEntity(
                "jlpt:$id", id, "Old exam", "grammar", "N3", null, null, "ready", false, 0,
            ))
            val client = AndroidSyncClient(context, db, backgroundSync = NoBackgroundSync, transport = FakeTransport(mutableMapOf(0L to tombstonePage(id))))
            client.pair("https://sync.test", "pairing-key", "integration")
            client.syncNow()
            assertEquals(true, db.syncDao().syncedExam("jlpt:$id")!!.isDeleted)
            assertEquals(1L, db.syncDao().checkpoint(SyncCheckpointEntity.CHECKPOINT_ID)!!.cursor)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
