package com.n3sensei.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Concurrency and consistency on a real file database (WAL, several connections), as in the app:
 * an answer is never applied twice, none is lost, and SQLite's own checks stay clean.
 */
@RunWith(AndroidJUnit4::class)
class DataIntegrityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "integrity-test.db"
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val now = Instant.parse("2026-10-13T01:00:00Z")
    private lateinit var db: N3Database
    private lateinit var submit: SubmitReviewUseCase
    private val keiken = ItemRef(ItemType.VOCABULARY, SeedData.V_KEIKEN)

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, N3Database::class.java, name).build()
        db.contentPackDao().replacePack(SeedData.bundle(isSeed = false))
        submit = SubmitReviewUseCase(OfflineProgressRepository(db.progressDao(), Clock.fixed(now, zone)))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun sqliteChecksAreClean() {
        val sql = db.openHelper.writableDatabase
        sql.query("PRAGMA integrity_check").use { it.moveToFirst(); assertEquals("ok", it.getString(0)) }
        sql.query("PRAGMA foreign_key_check").use { assertEquals("foreign key violations", 0, it.count) }
    }

    @Test
    fun sameAnswerSentConcurrently_isAppliedExactlyOnce() = runBlocking {
        val outcomes = (1..20).map {
            async(Dispatchers.IO) { submit(keiken, "same-request", ReviewRating.GOOD, ReviewSettings(), now, zone) }
        }.awaitAll()
        assertEquals(1, outcomes.count { it == SubmitOutcome.APPLIED })
        assertEquals(19, outcomes.count { it == SubmitOutcome.DUPLICATE })
        assertEquals(1, db.countRows("review_records"))
        assertEquals(1, db.progressDao().get(ItemType.VOCABULARY, SeedData.V_KEIKEN)!!.reviewCount)
        sqliteChecksAreClean()
    }

    @Test
    fun differentAnswersConcurrently_noneIsLost() = runBlocking {
        (1..20).map { n ->
            async(Dispatchers.IO) { submit(keiken, "request-$n", ReviewRating.GOOD, ReviewSettings(), now.plusSeconds(n.toLong()), zone) }
        }.awaitAll()
        // Each answer read the progress written by the previous one (no lost update).
        assertEquals(20, db.countRows("review_records"))
        val progress = db.progressDao().get(ItemType.VOCABULARY, SeedData.V_KEIKEN)!!
        assertEquals(20, progress.reviewCount)
        assertEquals(6, progress.stepIndex) // GOOD climbs one step each time, capped at the last of 7
        // Exactly one answer is the item's first answer.
        db.openHelper.readableDatabase.query("SELECT SUM(was_new) FROM review_records").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        sqliteChecksAreClean()
    }

    @Test
    fun contentReplacement_doesNotTouchLearnerData_andKeepsDatabaseConsistent() = runBlocking {
        submit(keiken, "r1", ReviewRating.GOOD, ReviewSettings(), now, zone)
        repeat(3) { db.contentPackDao().replacePack(SeedData.bundle(version = it + 2, isSeed = false)) }
        assertEquals(1, db.countRows("review_records"))
        assertEquals(1, db.countRows("learning_progress"))
        assertTrue(db.countRows("vocabulary") > 0)
        sqliteChecksAreClean()
    }
}
