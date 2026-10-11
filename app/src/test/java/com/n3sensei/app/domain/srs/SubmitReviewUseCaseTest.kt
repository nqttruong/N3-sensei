package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.testing.FakeProgressRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SubmitReviewUseCaseTest {

    private val repository = FakeProgressRepository()
    private val submit = SubmitReviewUseCase(repository)
    private val item = ItemRef(ItemType.KANJI, "k1")
    private val now = Instant.parse("2026-10-11T01:00:00Z")

    @Test
    fun sameRequestTwice_isRecordedOnce() = runTest {
        assertEquals(SubmitOutcome.APPLIED, submit(item, "req-1", ReviewRating.GOOD, ReviewSettings(), now, ZoneOffset.UTC))
        assertEquals(SubmitOutcome.DUPLICATE, submit(item, "req-1", ReviewRating.EASY, ReviewSettings(), now, ZoneOffset.UTC))
        assertEquals(1, repository.records.size)
        assertEquals(1, repository.progress.getValue(item).stepIndex)
    }

    @Test
    fun secondAnswer_isScheduledFromStoredProgress() = runTest {
        submit(item, "req-1", ReviewRating.GOOD, ReviewSettings(), now, ZoneOffset.UTC)
        submit(item, "req-2", ReviewRating.GOOD, ReviewSettings(), now.plusSeconds(3600), ZoneOffset.UTC)
        assertEquals(2, repository.progress.getValue(item).stepIndex)
        assertEquals(listOf(true, false), repository.records.map { it.wasNew })
    }
}
