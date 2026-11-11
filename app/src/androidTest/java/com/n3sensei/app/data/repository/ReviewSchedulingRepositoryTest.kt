package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.ReviewTime
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.domain.srs.TodayCounts
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Scheduler + Room together: idempotency, queues and daily counts on the real schema. */
@RunWith(AndroidJUnit4::class)
class ReviewSchedulingRepositoryTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private lateinit var db: N3Database
    private lateinit var repository: OfflineProgressRepository
    private lateinit var submit: SubmitReviewUseCase

    private val keiken = ItemRef(ItemType.VOCABULARY, SeedData.V_KEIKEN)
    private val kei = ItemRef(ItemType.KANJI, SeedData.K_KEI)
    private val settings = ReviewSettings()

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        db.contentPackDao().replacePack(SeedData.bundle())
        repository = OfflineProgressRepository(db.progressDao(), Clock.fixed(at("2026-10-11T08:00"), zone))
        submit = SubmitReviewUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun answer(item: ItemRef, rating: ReviewRating, time: String, requestId: String = "$item-$time") =
        submit(item, requestId, rating, settings, at(time), zone)

    @Test
    fun sameRequestId_isStoredOnce_evenWithDifferentRating() = runTest {
        assertEquals(SubmitOutcome.APPLIED, answer(keiken, ReviewRating.GOOD, "2026-10-11T08:00", requestId = "r1"))
        assertEquals(SubmitOutcome.DUPLICATE, answer(keiken, ReviewRating.EASY, "2026-10-11T08:00", requestId = "r1"))
        assertEquals(1, db.countRows("review_records"))
        val stored = repository.getProgress(keiken)!!
        assertEquals(1, stored.stepIndex)
        assertEquals(1, stored.reviewCount)
    }

    @Test
    fun consecutiveAnswers_areScheduledFromStoredProgress() = runTest {
        answer(keiken, ReviewRating.GOOD, "2026-10-11T08:00")
        answer(keiken, ReviewRating.GOOD, "2026-10-11T09:05")
        answer(keiken, ReviewRating.GOOD, "2026-10-12T09:30")

        val p = repository.getProgress(keiken)!!
        assertEquals(3, p.stepIndex)
        assertEquals(ProgressState.REVIEW, p.state)
        assertEquals(at("2026-10-14T09:30"), p.dueAt)
        val history = repository.getReviewHistory(keiken, 10)
        assertEquals(listOf(false, false, true), history.map { it.wasNew })
        assertEquals(3, history.mapNotNull { it.requestId }.toSet().size)
    }

    @Test
    fun itemState_exposesStatusDueAndLevel() = runTest {
        answer(kei, ReviewRating.EASY, "2026-10-11T08:00")
        val state = repository.observeItemState(kei).first()
        assertEquals(LearningStatus.REVIEW, state.status)
        assertEquals(2, state.stepIndex)
        assertEquals(at("2026-10-12T08:00"), state.dueAt)
    }

    @Test
    fun dueItems_areOldestFirst_acrossTypes_andLimited() = runTest {
        answer(kei, ReviewRating.AGAIN, "2026-10-11T08:00") // due 08:20
        answer(keiken, ReviewRating.AGAIN, "2026-10-11T07:50") // due 08:10
        answer(ItemRef(ItemType.GRAMMAR, SeedData.G_TABAKARI), ReviewRating.GOOD, "2026-10-11T08:00") // due 09:00

        val now = at("2026-10-11T08:30")
        assertEquals(listOf(keiken, kei), repository.getDueItems(now, limit = 10))
        assertEquals(listOf(keiken), repository.getDueItems(now, limit = 1))
        assertTrue(repository.getDueItems(at("2026-10-11T08:00"), limit = 10).isEmpty())
    }

    @Test
    fun newItems_followCourseOrder_vocabularyThenKanjiThenGrammar() = runTest {
        answer(keiken, ReviewRating.GOOD, "2026-10-11T08:00")
        val refs = repository.getNewItems(limit = 10)
        assertEquals(
            listOf(SeedData.V_FUERU, SeedData.V_TODOKERU, SeedData.V_SHIKKARI, SeedData.K_KEI, SeedData.K_ZOU, SeedData.G_TABAKARI),
            refs.map { it.id },
        )
        assertEquals(2, repository.getNewItems(limit = 2).size)
    }

    @Test
    fun overview_countsDueOverdueTodayAndNew_byLocalDay() = runTest {
        // Yesterday: two new items answered; one is now overdue (due before today's midnight).
        answer(keiken, ReviewRating.AGAIN, "2026-10-10T23:30") // due 23:50 yesterday → overdue
        answer(kei, ReviewRating.GOOD, "2026-10-10T23:50") // due 00:50 today → due
        // Today: one review answer.
        answer(keiken, ReviewRating.HARD, "2026-10-11T00:05", requestId = "today-1") // due 00:25 today

        val now = at("2026-10-11T01:00")
        val overview = repository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first()
        assertEquals(mapOf(ItemType.VOCABULARY to 1, ItemType.KANJI to 1), overview.dueByType)
        assertEquals(2, overview.due)
        assertEquals(0, overview.overdue) // keiken was answered again today, so it is no longer overdue
        assertEquals(TodayCounts(newAnswered = 0, reviewsAnswered = 1), overview.today)
        assertEquals(5, overview.newAvailable) // 7 seed items − 2 answered

        val yesterdayNight = at("2026-10-10T23:59")
        val yesterday = repository.observeReviewOverview(
            yesterdayNight, ReviewTime.startOfDay(yesterdayNight, zone), ReviewTime.startOfNextDay(yesterdayNight, zone),
        ).first()
        assertEquals(TodayCounts(newAnswered = 2, reviewsAnswered = 0), yesterday.today)
    }

    @Test
    fun overdue_isDueBeforeLocalMidnight() = runTest {
        answer(keiken, ReviewRating.AGAIN, "2026-10-10T23:30") // due 23:50 yesterday
        val now = at("2026-10-11T00:10")
        val overview = repository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first()
        assertEquals(1, overview.overdue)
    }

    @Test
    fun summary_countsMasteredItems() = runTest {
        // EASY ×3 from new: steps 2 → 4 → 6 (last step of the default ladder).
        var t = at("2026-10-11T08:00")
        repeat(3) { i ->
            submit(keiken, "m$i", ReviewRating.EASY, settings, t, zone)
            t = t.plus(Duration.ofDays(40))
        }
        assertEquals(ProgressState.MASTERED, repository.getProgress(keiken)!!.state)
        assertEquals(1, repository.observeSummary(t).first().mastered)
    }

    private val tabakari = ItemRef(ItemType.GRAMMAR, SeedData.G_TABAKARI)

    @Test
    fun eachType_hasItsOwnQueue_andAnswersTouchOnlyTheirOwnSchedule() = runTest {
        answer(keiken, ReviewRating.AGAIN, "2026-10-11T08:00") // due 08:20
        answer(kei, ReviewRating.AGAIN, "2026-10-11T08:00") // due 08:20
        answer(tabakari, ReviewRating.GOOD, "2026-10-11T08:00") // due 09:00
        val now = at("2026-10-11T09:30")
        assertEquals(listOf(keiken), repository.getDueItemsOfType(now, ItemType.VOCABULARY, 10))
        assertEquals(listOf(kei), repository.getDueItemsOfType(now, ItemType.KANJI, 10))
        assertEquals(listOf(tabakari), repository.getDueItemsOfType(now, ItemType.GRAMMAR, 10))
        assertEquals(listOf(SeedData.K_ZOU), repository.getNewItemsOfType(ItemType.KANJI, 10).map { it.id })
        assertEquals(listOf(SeedData.V_FUERU, SeedData.V_TODOKERU), repository.getNewItemsOfType(ItemType.VOCABULARY, 2).map { it.id })
        assertTrue(repository.getNewItemsOfType(ItemType.GRAMMAR, 10).isEmpty())

        // A kanji answer moves the kanji's schedule only.
        val wordBefore = repository.getProgress(keiken)
        val grammarBefore = repository.getProgress(tabakari)
        answer(kei, ReviewRating.GOOD, "2026-10-11T09:30")
        assertEquals(wordBefore, repository.getProgress(keiken))
        assertEquals(grammarBefore, repository.getProgress(tabakari))
        assertEquals(1, db.countRows("learning_progress WHERE item_type = 'KANJI'"))
        assertEquals(3, db.countRows("learning_progress"))
    }

    @Test
    fun forgotten_isTheLastAnswer_andGoesAwayOnceRemembered() = runTest {
        answer(kei, ReviewRating.AGAIN, "2026-10-11T08:00")
        answer(keiken, ReviewRating.AGAIN, "2026-10-11T08:00")
        answer(keiken, ReviewRating.GOOD, "2026-10-11T08:30") // remembered since
        answer(tabakari, ReviewRating.GOOD, "2026-10-11T08:00")
        assertEquals(listOf(kei), repository.getForgottenItemsOfType(ItemType.KANJI, 10))
        assertTrue(repository.getForgottenItemsOfType(ItemType.VOCABULARY, 10).isEmpty())
        assertTrue(repository.getForgottenItemsOfType(ItemType.GRAMMAR, 10).isEmpty())
        val now = at("2026-10-11T08:40")
        val overview = repository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first()
        assertEquals(mapOf(ItemType.KANJI to 1), overview.forgottenByType)
        answer(kei, ReviewRating.HARD, "2026-10-11T08:40")
        assertTrue(repository.getForgottenItemsOfType(ItemType.KANJI, 10).isEmpty())
    }

    @Test
    fun overview_breaksDueOverdueDoneAndNew_downByType() = runTest {
        answer(keiken, ReviewRating.AGAIN, "2026-10-10T23:30") // due 23:50 yesterday → overdue
        answer(kei, ReviewRating.GOOD, "2026-10-11T00:10") // due 01:10 today
        answer(kei, ReviewRating.AGAIN, "2026-10-11T01:20", requestId = "kei-2") // due 01:40 today
        answer(tabakari, ReviewRating.EASY, "2026-10-11T01:00") // due tomorrow

        val now = at("2026-10-11T02:00")
        val overview = repository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first()
        assertEquals(mapOf(ItemType.VOCABULARY to 1, ItemType.KANJI to 1), overview.dueByType)
        assertEquals(mapOf(ItemType.VOCABULARY to 1), overview.overdueByType)
        assertEquals(1, overview.overdue)
        // Distinct items answered today: kei twice counts once; keiken was yesterday.
        assertEquals(mapOf(ItemType.KANJI to 1, ItemType.GRAMMAR to 1), overview.doneTodayByType)
        assertEquals(mapOf(ItemType.VOCABULARY to 3, ItemType.KANJI to 1), overview.newByType)
        assertEquals(4, overview.newAvailable)
    }
}
