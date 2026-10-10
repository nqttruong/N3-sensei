package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.repository.ProgressRepository
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
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class OfflineProgressRepositoryTest {

    private lateinit var db: N3Database
    private lateinit var repository: ProgressRepository

    private val now = Instant.parse("2026-10-11T08:00:00Z")
    private val keiken = ItemRef(ItemType.VOCABULARY, SeedData.V_KEIKEN)

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        db.contentPackDao().replacePack(SeedData.bundle())
        repository = OfflineProgressRepository(db.progressDao())
    }

    @After
    fun tearDown() = db.close()

    private fun progress(item: ItemRef, dueAt: Instant?) = LearningProgress(
        item = item, state = ProgressState.REVIEW, stepIndex = 2, intervalMinutes = 1_440,
        dueAt = dueAt, lastReviewedAt = now, reviewCount = 3, lapseCount = 1,
        createdAt = now.minus(Duration.ofDays(3)), updatedAt = now,
    )

    private fun record(item: ItemRef, at: Instant) = ReviewRecord(
        item = item, rating = ReviewRating.HARD, reviewedAt = at,
        intervalBeforeMinutes = 60, intervalAfterMinutes = 1_440, dueAtAfter = at.plus(Duration.ofDays(1)),
    )

    @Test
    fun recordReview_roundTripsAllFields() = runTest {
        val p = progress(keiken, dueAt = now.plus(Duration.ofDays(1)))
        repository.recordReview(p, record(keiken, now))

        assertEquals(p, repository.observeProgress(keiken).first())
        val history = repository.getReviewHistory(keiken, limit = 5)
        assertEquals(1, history.size)
        assertEquals(record(keiken, now).copy(id = history.single().id), history.single())
    }

    @Test
    fun recordReview_rejectsMismatchedItems() = runTest {
        val other = ItemRef(ItemType.KANJI, SeedData.K_KEI)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.recordReview(progress(keiken, now), record(other, now)) }
        }
        assertEquals(0, db.countRows("learning_progress"))
    }

    @Test
    fun dueItemsAndCount_useInstantBoundaryInclusively() = runTest {
        repository.recordReview(progress(keiken, dueAt = now), record(keiken, now))
        assertEquals(listOf(keiken), repository.observeDue(now, limit = 10).first().map { it.item })
        assertEquals(1, repository.observeDueCount(now).first())
        assertEquals(0, repository.observeDueCount(now.minusMillis(1)).first())
    }

    @Test
    fun progress_survivesContentPackReplacement() = runTest {
        repository.recordReview(progress(keiken, dueAt = now), record(keiken, now))

        db.contentPackDao().replacePack(SeedData.bundle(version = 2))

        assertEquals(progress(keiken, dueAt = now), repository.observeProgress(keiken).first())
        assertEquals(1, repository.observeDueCount(now).first())
    }

    @Test
    fun progressForRemovedContent_isKeptButNotDue() = runTest {
        repository.recordReview(progress(keiken, dueAt = now), record(keiken, now))

        db.contentPackDao().deletePack(SeedData.PACK_ID)

        assertEquals(0, repository.observeDueCount(now).first())
        assertEquals(1, db.countRows("learning_progress"))
        assertEquals(1, repository.getReviewHistory(keiken, 10).size)
    }

    @Test
    fun unknownItem_hasNoProgressOrHistory() = runTest {
        val unknown = ItemRef(ItemType.GRAMMAR, "unknown")
        assertNull(repository.observeProgress(unknown).first())
        assertEquals(emptyList<ReviewRecord>(), repository.getReviewHistory(unknown, 10))
    }
}
