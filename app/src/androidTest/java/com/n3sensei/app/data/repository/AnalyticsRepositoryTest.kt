package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.domain.analytics.LearningAnalytics
import com.n3sensei.app.domain.analytics.StatsPeriod
import com.n3sensei.app.domain.analytics.StatusCounts
import com.n3sensei.app.domain.analytics.TypeActivity
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.srs.ReviewTime
import com.n3sensei.app.testing.AnalyticsFixture
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Every expected number below comes from the hand count in [AnalyticsFixture]. */
@RunWith(AndroidJUnit4::class)
class AnalyticsRepositoryTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.parse("2026-10-11")
    private val fixture = AnalyticsFixture(zone, today)
    private lateinit var db: N3Database
    private lateinit var repository: OfflineAnalyticsRepository

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        db.contentPackDao().replacePack(SeedData.bundle(isSeed = false))
        fixture.insert(db)
        repository = OfflineAnalyticsRepository(db.analyticsDao())
    }

    @After
    fun tearDown() = db.close()

    private fun range(period: StatsPeriod) = period.range(fixture.now, zone)

    @Test
    fun today_countsAnswersAndDistinctItemsSeparately() = runTest {
        val (from, to) = range(StatsPeriod.TODAY)
        val a = repository.observeActivity(from, to).first()
        assertEquals(5, a.answers)
        assertEquals(4, a.distinctItems)
        assertEquals(2, a.newItems)
        assertEquals(3, a.reviewAnswers)
        assertEquals(2, a.distinctReviewedItems)
        assertEquals(mapOf(ReviewRating.AGAIN to 1, ReviewRating.GOOD to 1, ReviewRating.HARD to 1), a.reviewRatings)
        assertEquals(2.0 / 3, LearningAnalytics.recallRate(a.reviewRatings)!!, 1e-9)
    }

    @Test
    fun last7Days_includesEarlierFirstAnswers() = runTest {
        val (from, to) = range(StatsPeriod.LAST_7_DAYS)
        val a = repository.observeActivity(from, to).first()
        assertEquals(7, a.answers)
        assertEquals(4, a.distinctItems)
        assertEquals(4, a.newItems)
        assertEquals(3, a.reviewAnswers)
        assertEquals(2, a.distinctReviewedItems)
        // All time equals 7 days here; the removed item is never counted.
        assertEquals(a, repository.observeActivity(null, to).first())
    }

    @Test
    fun dailyBuckets_andStreak_useLocalDays() = runTest {
        val (_, to) = range(StatsPeriod.TODAY)
        val from = today.minusDays(6).atStartOfDay(zone).toInstant()
        val series = LearningAnalytics.dailySeries(repository.observeTimeBuckets(from, to).first(), zone, today, 7)
        assertEquals(listOf(0, 0, 0, 1, 0, 1, 5), series.map { it.answers }) // D-6 … D; 23:59 stays on D
        assertEquals(listOf(0, 0, 0, 1, 0, 1, 2), series.map { it.newItems })
        val streak = LearningAnalytics.streak(LearningAnalytics.studyDays(repository.observeStudyBuckets().first(), zone), today)
        assertEquals(2, streak.current)
        assertEquals(2, streak.longest)
    }

    @Test
    fun troubleItems_rankedByForgottenOrHard() = runTest {
        val (from, to) = range(StatsPeriod.TODAY)
        val items = repository.observeTroubleItems(from, to, 10).first()
        assertEquals(listOf("経", "経験", "〜たばかり", "増える"), items.map { it.label })
        assertEquals(listOf(1, 1, 0, 0), items.map { it.again })
        assertEquals(1, repository.observeTroubleItems(from, to, 1).first().size)
    }

    @Test
    fun statusDueAndForecast() = runTest {
        val status = repository.observeStatusByType().first()
        assertEquals(StatusCounts(new = 2, learning = 1, review = 1, mastered = 0), status[ItemType.VOCABULARY])
        assertEquals(StatusCounts(new = 1, learning = 0, review = 0, mastered = 1), status[ItemType.KANJI])
        assertEquals(StatusCounts(new = 0, learning = 1, review = 0, mastered = 0), status[ItemType.GRAMMAR])

        val due = repository.observeDue(fixture.now, ReviewTime.startOfDay(fixture.now, zone)).first()
        assertEquals(2, due.due)
        assertEquals(1, due.overdue)

        val end = today.plusDays(7).atStartOfDay(zone).toInstant()
        val forecast = LearningAnalytics.forecast(repository.observeDueBuckets(fixture.now.plusMillis(1), end).first(), due.due, zone, today, 7)
        assertEquals(listOf(2, 1, 0, 0, 0, 1, 0), forecast.map { it.answers })
    }

    @Test
    fun seedContent_isExcludedFromEveryStatistic() = runTest {
        db.openHelper.writableDatabase.execSQL("UPDATE content_packs SET is_seed = 1")
        val (from, to) = range(StatsPeriod.ALL_TIME)
        assertEquals(0, repository.observeActivity(from, to).first().answers)
        assertTrue(repository.observeStudyBuckets().first().isEmpty())
        assertTrue(repository.observeTroubleItems(from, to, 10).first().isEmpty())
        assertTrue(repository.observeStatusByType().first().values.all { it.total == 0 })
        assertEquals(0, repository.observeDue(fixture.now, ReviewTime.startOfDay(fixture.now, zone)).first().due)
    }

    @Test
    fun emptyDatabase_givesZerosAndNoRecallRate() = runTest {
        val empty = inMemoryDatabase()
        try {
            val repo = OfflineAnalyticsRepository(empty.analyticsDao())
            val (from, to) = range(StatsPeriod.ALL_TIME)
            val a = repo.observeActivity(from, to).first()
            assertEquals(0, a.answers + a.distinctItems + a.newItems + a.reviewAnswers)
            assertNull(LearningAnalytics.recallRate(a.reviewRatings))
            assertTrue(repo.observeStatusByType().first().values.all { it == StatusCounts.EMPTY })
            assertTrue(repo.observeTroubleItems(from, to, 10).first().isEmpty())
        } finally {
            empty.close()
        }
    }

    @Test
    fun answersByType_scheduleAndPriority() = runTest {
        val (from, to) = range(StatsPeriod.TODAY)
        // Today: 経験 ×2 + 増える (vocabulary), 経 (kanji), 〜たばかり (grammar); "gone" excluded.
        assertEquals(mapOf(ItemType.VOCABULARY to 3, ItemType.KANJI to 1, ItemType.GRAMMAR to 1), repository.observeAnswersByType(from, to).first())

        // Every fixture item is at step 1; "gone" is excluded.
        val schedule = repository.observeSchedule().first()
        assertEquals(4, schedule.size)
        assertEquals(
            listOf(fixture.at(-1, 20, 0), fixture.at(0, 0, 0, 1), fixture.at(1, 10, 0), fixture.at(5, 9, 0)),
            schedule.map { it.dueAt }.sorted(),
        )
        assertTrue(schedule.all { it.stepIndex == 1 })

        // Due at D 12:00: overdue 増える first, then 経験; future items are not listed.
        val priority = repository.observePriorityItems(fixture.now, 5).first()
        assertEquals(listOf("増える", "経験"), priority.map { it.label })
        assertEquals(listOf(ItemType.VOCABULARY, ItemType.VOCABULARY), priority.map { it.ref.type })
        assertEquals(1, repository.observePriorityItems(fixture.now, 1).first().size)
    }

    @Test
    fun priority_sameDueTime_moreLapsesFirst() = runTest {
        val due = fixture.at(-1, 20, 0).toEpochMilli()
        db.progressDao().upsert(LearningProgressEntity(ItemType.VOCABULARY, SeedData.V_KEIKEN, ProgressState.REVIEW, 2, 1440, due, 0, 3, 2, 0, 0))
        assertEquals(listOf("経験", "増える"), repository.observePriorityItems(fixture.now, 5).first().map { it.label })
    }

    @Test
    fun seedContent_isExcludedFromDashboardQueries() = runTest {
        db.openHelper.writableDatabase.execSQL("UPDATE content_packs SET is_seed = 1")
        val (from, to) = range(StatsPeriod.ALL_TIME)
        assertTrue(repository.observeAnswersByType(from, to).first().values.all { it == 0 })
        assertTrue(repository.observeSchedule().first().isEmpty())
        assertTrue(repository.observePriorityItems(fixture.now, 5).first().isEmpty())
    }

    @Test
    fun activityAndDailyHistory_areSplitByType() = runTest {
        val (from, to) = range(StatsPeriod.TODAY)
        val byType = repository.observeActivityByType(from, to).first()
        assertEquals(TypeActivity(answers = 3, distinctItems = 2, distinctReviewedItems = 1, reviewRatings = mapOf(ReviewRating.AGAIN to 1, ReviewRating.GOOD to 1)), byType[ItemType.VOCABULARY])
        assertEquals(TypeActivity(answers = 1, distinctItems = 1, distinctReviewedItems = 0, reviewRatings = emptyMap()), byType[ItemType.KANJI])
        assertEquals(TypeActivity(answers = 1, distinctItems = 1, distinctReviewedItems = 1, reviewRatings = mapOf(ReviewRating.HARD to 1)), byType[ItemType.GRAMMAR])
        // The same totals as the whole, the removed item never counted.
        assertEquals(repository.observeActivity(from, to).first().answers, byType.values.sumOf { it.answers })
        assertEquals(0.5, LearningAnalytics.recallRate(byType.getValue(ItemType.VOCABULARY).reviewRatings)!!, 1e-9)
        assertEquals(null, LearningAnalytics.recallRate(byType.getValue(ItemType.KANJI).reviewRatings))

        val weekFrom = today.minusDays(6).atStartOfDay(zone).toInstant()
        val buckets = repository.observeTimeBucketsByType(weekFrom, to).first()
        fun days(type: ItemType) = LearningAnalytics.dailySeries(buckets[type].orEmpty(), zone, today, 7).map { it.answers }
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 3), days(ItemType.VOCABULARY))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1), days(ItemType.KANJI)) // 23:59 stays on today
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 1), days(ItemType.GRAMMAR))
    }
}
