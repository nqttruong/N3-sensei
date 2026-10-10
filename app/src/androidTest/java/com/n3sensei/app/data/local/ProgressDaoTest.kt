package com.n3sensei.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDaoTest {

    private lateinit var db: N3Database
    private lateinit var dao: ProgressDao

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        dao = db.progressDao()
        db.contentPackDao().replacePack(SeedData.bundle())
    }

    @After
    fun tearDown() = db.close()

    private fun progress(type: ItemType, id: String, dueAt: Long?) = LearningProgressEntity(
        itemType = type, itemId = id, state = ProgressState.LEARNING, stepIndex = 0,
        intervalMinutes = 20, dueAt = dueAt, lastReviewedAt = null, reviewCount = 0,
        lapseCount = 0, createdAt = 0, updatedAt = 0,
    )

    private fun record(type: ItemType, id: String, at: Long, recordId: Long = 0) = ReviewRecordEntity(
        id = recordId, itemType = type, itemId = id, rating = ReviewRating.GOOD, reviewedAt = at,
        intervalBeforeMinutes = 20, intervalAfterMinutes = 60, dueAtAfter = at + 3_600_000,
    )

    @Test
    fun upsert_insertsThenUpdates() = runTest {
        dao.upsert(progress(ItemType.VOCABULARY, SeedData.V_KEIKEN, dueAt = 100))
        dao.upsert(progress(ItemType.VOCABULARY, SeedData.V_KEIKEN, dueAt = 200).copy(reviewCount = 1))

        val stored = dao.observe(ItemType.VOCABULARY, SeedData.V_KEIKEN).first()!!
        assertEquals(200L, stored.dueAt)
        assertEquals(1, stored.reviewCount)
        assertEquals(1, db.countRows("learning_progress"))
    }

    @Test
    fun sameIdDifferentType_areSeparateRows() = runTest {
        dao.upsert(progress(ItemType.VOCABULARY, "same", dueAt = 1))
        dao.upsert(progress(ItemType.KANJI, "same", dueAt = 2))
        assertEquals(2, db.countRows("learning_progress"))
    }

    @Test
    fun recordReview_writesProgressAndRecord() = runTest {
        dao.recordReview(progress(ItemType.KANJI, SeedData.K_KEI, dueAt = 500), record(ItemType.KANJI, SeedData.K_KEI, at = 400))

        assertEquals(500L, dao.get(ItemType.KANJI, SeedData.K_KEI)!!.dueAt)
        assertEquals(1, dao.getHistory(ItemType.KANJI, SeedData.K_KEI, 10).size)
    }

    @Test
    fun recordReview_rollsBackProgressWhenRecordFails() = runTest {
        dao.insertRecord(record(ItemType.KANJI, SeedData.K_KEI, at = 1, recordId = 7))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.recordReview(
                    progress(ItemType.KANJI, SeedData.K_ZOU, dueAt = 9),
                    record(ItemType.KANJI, SeedData.K_ZOU, at = 2, recordId = 7),
                )
            }
        }
        assertNull(dao.get(ItemType.KANJI, SeedData.K_ZOU))
    }

    @Test
    fun due_isOrderedLimitedAndExcludesFutureUnscheduledAndOrphans() = runTest {
        dao.upsert(progress(ItemType.VOCABULARY, SeedData.V_FUERU, dueAt = 300))
        dao.upsert(progress(ItemType.VOCABULARY, SeedData.V_KEIKEN, dueAt = 100))
        dao.upsert(progress(ItemType.GRAMMAR, SeedData.G_TABAKARI, dueAt = 200))
        dao.upsert(progress(ItemType.KANJI, SeedData.K_KEI, dueAt = 5_000)) // future
        dao.upsert(progress(ItemType.KANJI, SeedData.K_ZOU, dueAt = null)) // unscheduled
        dao.upsert(progress(ItemType.VOCABULARY, "removed-item", dueAt = 50)) // orphan

        val due = dao.observeDue(now = 1_000, limit = 10).first()
        assertEquals(listOf(SeedData.V_KEIKEN, SeedData.G_TABAKARI, SeedData.V_FUERU), due.map { it.itemId })
        assertEquals(3, dao.observeDueCount(now = 1_000).first())
        assertEquals(2, dao.observeDue(now = 1_000, limit = 2).first().size)
        assertEquals(0, dao.observeDueCount(now = 0).first())
    }

    @Test
    fun history_isNewestFirstAndLimited() = runTest {
        listOf(10L, 30L, 20L).forEach { dao.insertRecord(record(ItemType.VOCABULARY, SeedData.V_KEIKEN, at = it)) }
        dao.insertRecord(record(ItemType.VOCABULARY, SeedData.V_FUERU, at = 99))

        val history = dao.getHistory(ItemType.VOCABULARY, SeedData.V_KEIKEN, limit = 2)
        assertEquals(listOf(30L, 20L), history.map { it.reviewedAt })
    }

    @Test
    fun emptyDatabase_queriesReturnEmpty() = runTest {
        assertNull(dao.observe(ItemType.GRAMMAR, "none").first())
        assertEquals(emptyList<LearningProgressEntity>(), dao.observeDue(Long.MAX_VALUE, 10).first())
        assertEquals(emptyList<ReviewRecordEntity>(), dao.getHistory(ItemType.GRAMMAR, "none", 10))
    }
}
