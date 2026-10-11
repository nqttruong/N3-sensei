package com.n3sensei.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.testing.SeedData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a real on-disk database, created the same way as in production. */
@RunWith(AndroidJUnit4::class)
class N3DatabasePersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "persistence-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun contentAndProgress_surviveCloseAndReopen() = runTest {
        N3Database.create(context, name).apply {
            contentPackDao().replacePack(SeedData.bundle())
            progressDao().upsert(
                LearningProgressEntity(
                    ItemType.VOCABULARY, SeedData.V_KEIKEN, ProgressState.REVIEW, 1, 60,
                    dueAt = 123, lastReviewedAt = 100, reviewCount = 2, lapseCount = 0, createdAt = 1, updatedAt = 100,
                ),
            )
            close()
        }

        val reopened = N3Database.create(context, name)
        try {
            assertEquals("経験", reopened.contentDao().getVocabulary(SeedData.V_KEIKEN)!!.expression)
            assertEquals(listOf("ゾウ"), reopened.contentDao().getKanji(SeedData.K_ZOU)!!.onReadings)
            assertEquals(123L, reopened.progressDao().get(ItemType.VOCABULARY, SeedData.V_KEIKEN)!!.dueAt)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun reviewProgress_isReadBackAfterReopen_andTheNextAnswerContinuesFromIt() = runTest {
        val zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh")
        val t0 = java.time.Instant.parse("2026-10-11T01:00:00Z")
        val item = ItemRef(ItemType.VOCABULARY, SeedData.V_KEIKEN)
        N3Database.create(context, name).apply {
            contentPackDao().replacePack(SeedData.bundle())
            val submit = SubmitReviewUseCase(OfflineProgressRepository(progressDao(), java.time.Clock.fixed(t0, zone)))
            submit(item, "a1", ReviewRating.GOOD, ReviewSettings(), t0, zone)
            submit(item, "a2", ReviewRating.GOOD, ReviewSettings(), t0.plusSeconds(3_900), zone)
            close()
        }

        val reopened = N3Database.create(context, name)
        try {
            val repository = OfflineProgressRepository(reopened.progressDao(), java.time.Clock.fixed(t0, zone))
            val stored = repository.getProgress(item)!!
            assertEquals(2, stored.stepIndex)
            assertEquals(t0.plusSeconds(3_900).atZone(zone).plusDays(1).toInstant(), stored.dueAt)
            assertEquals(2, repository.getReviewHistory(item, 10).size)

            // Retrying an answer saved before the restart is ignored; a new answer continues.
            val submit = SubmitReviewUseCase(repository)
            assertEquals(SubmitOutcome.DUPLICATE, submit(item, "a2", ReviewRating.GOOD, ReviewSettings(), t0, zone))
            submit(item, "a3", ReviewRating.GOOD, ReviewSettings(), stored.dueAt!!, zone)
            assertEquals(3, repository.getProgress(item)!!.stepIndex)
        } finally {
            reopened.close()
        }
    }
}
