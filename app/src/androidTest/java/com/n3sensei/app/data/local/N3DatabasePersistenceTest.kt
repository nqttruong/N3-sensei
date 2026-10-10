package com.n3sensei.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
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
}
