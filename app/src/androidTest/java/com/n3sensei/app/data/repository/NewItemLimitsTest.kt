package com.n3sensei.app.data.repository

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.SubmitOutcome
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.srs.ReviewQueuePlanner
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.ReviewTime
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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

/**
 * Independent daily new-item caps on the real schema and scheduler: each type counted on its own,
 * enforced inside the answer's transaction (concurrent answers included), day by local midnight.
 * The simulation runs on the packs shipped with the app (910 words, 420 N3 kanji, 181 patterns;
 * the 448 N2 kanji stay out, no N2 lesson being chosen for review by default).
 */
@RunWith(AndroidJUnit4::class)
class NewItemLimitsTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private lateinit var db: N3Database
    private lateinit var repository: OfflineProgressRepository
    private lateinit var submit: SubmitReviewUseCase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = OfflineProgressRepository(db.progressDao(), Clock.systemUTC())
        submit = SubmitReviewUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    private fun settings(vocabulary: Int, kanji: Int, grammar: Int) =
        ReviewSettings(limits = DailyLimits(newVocabulary = vocabulary, newKanji = kanji, newGrammar = grammar, reviewsPerDay = 2000))

    private fun ref(type: ItemType, n: Int) = ItemRef(type, "${type.name.lowercase()}-$n")

    private suspend fun answer(item: ItemRef, s: ReviewSettings, time: Instant, rating: ReviewRating = ReviewRating.GOOD) =
        submit(item, "$item@${time.toEpochMilli()}", rating, s, time, zone)

    private fun newToday(type: ItemType, time: Instant) =
        runBlocking { db.progressDao().countNewOfType(type, ReviewTime.startOfDay(time, zone).toEpochMilli(), ReviewTime.startOfNextDay(time, zone).toEpochMilli()) }

    @Test
    fun eachTypeStopsAtItsOwnCap_theOthersGoOn() = runBlocking {
        val s = settings(vocabulary = 3, kanji = 2, grammar = 0)
        val t = at("2026-11-11T09:00")
        val kanji = (1..4).map { answer(ref(ItemType.KANJI, it), s, t.plusSeconds(it.toLong())) }
        assertEquals(listOf(SubmitOutcome.APPLIED, SubmitOutcome.APPLIED, SubmitOutcome.NEW_LIMIT_REACHED, SubmitOutcome.NEW_LIMIT_REACHED), kanji)
        // Kanji full: vocabulary still takes its 3, grammar (0) none.
        val words = (1..4).map { answer(ref(ItemType.VOCABULARY, it), s, t.plusSeconds(10L + it)) }
        assertEquals(3, words.count { it == SubmitOutcome.APPLIED })
        assertEquals(SubmitOutcome.NEW_LIMIT_REACHED, answer(ref(ItemType.GRAMMAR, 1), s, t.plusSeconds(30)))
        // Refused answers wrote nothing.
        assertEquals(5, db.countRows("learning_progress"))
        assertEquals(5, db.countRows("review_records"))
        assertEquals(listOf(3, 2, 0), ItemType.entries.map { newToday(it, t) })
    }

    @Test
    fun noSharedTotal_100OfEachInOneDay() = runBlocking {
        val s = settings(100, 100, 100)
        val t = at("2026-11-11T08:00")
        ItemType.entries.forEach { type -> (1..100).forEach { n -> assertEquals(SubmitOutcome.APPLIED, answer(ref(type, n), s, t.plusSeconds(n.toLong()))) } }
        assertEquals(300, db.countRows("learning_progress"))
        assertEquals(SubmitOutcome.NEW_LIMIT_REACHED, answer(ref(ItemType.KANJI, 101), s, t.plusSeconds(500)))
    }

    @Test
    fun dueAnswers_andItemsAlreadyScheduled_useNoNewAllowance() = runBlocking {
        val s = settings(vocabulary = 1, kanji = 1, grammar = 1)
        val t = at("2026-11-11T08:00")
        val word = ref(ItemType.VOCABULARY, 1)
        assertEquals(SubmitOutcome.APPLIED, answer(word, s, t, ReviewRating.AGAIN)) // introduced: the 1 of today
        // The same word again (due after "Quên"), several times: reviews, not new items.
        repeat(3) { assertEquals(SubmitOutcome.APPLIED, answer(word, s, t.plus(Duration.ofMinutes(30L * (it + 1))))) }
        assertEquals(1, newToday(ItemType.VOCABULARY, t))
        assertEquals(1, db.countRows("review_records WHERE was_new = 1"))
        assertEquals(3, db.countRows("review_records WHERE was_new = 0"))
        // Vocabulary is full for new words, but the scheduled one keeps being reviewed.
        assertEquals(SubmitOutcome.NEW_LIMIT_REACHED, answer(ref(ItemType.VOCABULARY, 2), s, t.plus(Duration.ofHours(3))))
        assertEquals(SubmitOutcome.APPLIED, answer(word, s, t.plus(Duration.ofHours(4))))
    }

    @Test
    fun theCount_startsAgainAtLocalMidnight() = runBlocking {
        val s = settings(vocabulary = 0, kanji = 2, grammar = 0)
        assertEquals(SubmitOutcome.APPLIED, answer(ref(ItemType.KANJI, 1), s, at("2026-11-11T23:50")))
        assertEquals(SubmitOutcome.APPLIED, answer(ref(ItemType.KANJI, 2), s, at("2026-11-11T23:55")))
        assertEquals(SubmitOutcome.NEW_LIMIT_REACHED, answer(ref(ItemType.KANJI, 3), s, at("2026-11-11T23:59:59")))
        // A new local day (Asia/Ho_Chi_Minh): a new allowance.
        assertEquals(SubmitOutcome.APPLIED, answer(ref(ItemType.KANJI, 3), s, at("2026-11-12T00:00:01")))
        assertEquals(listOf(2, 1), listOf(newToday(ItemType.KANJI, at("2026-11-11T12:00")), newToday(ItemType.KANJI, at("2026-11-12T12:00"))))
    }

    @Test
    fun concurrentAnswers_neverGoOverAType_sCap() = runBlocking {
        val s = settings(vocabulary = 4, kanji = 5, grammar = 3)
        val t = at("2026-11-11T10:00")
        // 20 new items of each type answered at once from parallel workers (two sessions, many taps).
        val outcomes = ItemType.entries.flatMap { type -> (1..20).map { n -> type to n } }
            .map { (type, n) -> async(Dispatchers.IO) { type to answer(ref(type, n), s, t.plusMillis(n.toLong())) } }
            .awaitAll()
        val applied = outcomes.filter { it.second == SubmitOutcome.APPLIED }.groupingBy { it.first }.eachCount()
        assertEquals(mapOf(ItemType.VOCABULARY to 4, ItemType.KANJI to 5, ItemType.GRAMMAR to 3), applied)
        assertEquals(listOf(4, 5, 3), ItemType.entries.map { newToday(it, t) })
        assertEquals(12, db.countRows("learning_progress"))
    }

    @Test
    fun refusedAnswers_leaveHistoryAndSchedulesAsTheyWere() = runBlocking {
        val s = settings(vocabulary = 2, kanji = 0, grammar = 0)
        val t = at("2026-11-11T08:00")
        answer(ref(ItemType.VOCABULARY, 1), s, t)
        answer(ref(ItemType.VOCABULARY, 2), s, t.plusSeconds(1))
        val progressBefore = db.progressDao().get(ItemType.VOCABULARY, "vocabulary-1")
        val recordsBefore = repository.getReviewHistory(ref(ItemType.VOCABULARY, 1), 10)
        repeat(5) { answer(ref(ItemType.VOCABULARY, 10 + it), s, t.plusSeconds(10L + it)) }
        answer(ref(ItemType.KANJI, 1), s, t.plusSeconds(20))
        assertEquals(progressBefore, db.progressDao().get(ItemType.VOCABULARY, "vocabulary-1"))
        assertEquals(recordsBefore, repository.getReviewHistory(ref(ItemType.VOCABULARY, 1), 10))
        assertEquals(2, db.countRows("review_records"))
    }

    // --- Simulation on the shipped content ---------------------------------------------------

    private fun importShippedPacks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidContentImportRepository.BUNDLED_PACK_ASSETS.forEach { asset ->
            val json = context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(asset, ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), Clock.systemUTC()).import(json).outcome)
        }
        // 420 N3 kanji + the 448 of Kanji N2, which review leaves out until N2 lessons are chosen.
        assertEquals(listOf(910, 420 + 448, 181), listOf(db.countRows("vocabulary"), db.countRows("kanji"), db.countRows("grammar")))
        // As in the app: new kanji only from the lessons chosen for review (defaults: every N3 lesson, no N2 lesson).
        val lessons = OfflineContentRepository(db.contentDao(), db.contentPackDao()).observeLessons()
        repository = OfflineProgressRepository(db.progressDao(), Clock.systemUTC(), lessons.map { KanjiReviewLessons().allowedLessons(it) })
        submit = SubmitReviewUseCase(repository)
    }

    private data class Day(val day: Int, val new: Map<ItemType, Int>, val reviews: Int, val remaining: Map<ItemType, Int>)

    /**
     * One mixed session per day at 09:00 and a second one at 20:00, built as the review session
     * builds its queue (due items, then each type's new items within its own allowance left),
     * every card answered "Tốt" through the real use case.
     */
    private fun simulate(s: ReviewSettings, days: Int): List<Day> = runBlocking {
        (1..days).map { day ->
            var reviews = 0
            val introduced = ItemType.entries.associateWith { 0 }.toMutableMap()
            listOf("09:00", "20:00").forEach { hour ->
                val now = at("2026-11-${10 + day}T$hour")
                val overview = repository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first()
                val due = repository.getDueItems(now, ReviewQueuePlanner.remainingReviews(s.limits, overview.today))
                val new = ItemType.entries.flatMap { t ->
                    val left = ReviewQueuePlanner.remainingNew(s.limits, overview.today, t)
                    if (left == 0) emptyList() else repository.getNewItemsOfType(t, left)
                }
                ReviewQueuePlanner.plan(due, new, s.limits, overview.today).forEachIndexed { i, item ->
                    val wasNew = repository.getProgress(item) == null
                    assertEquals(SubmitOutcome.APPLIED, answer(item, s, now.plusSeconds(i.toLong())))
                    if (wasNew) introduced[item.type] = introduced.getValue(item.type) + 1 else reviews++
                }
            }
            val left = repository.observeReviewOverview(at("2026-11-${10 + day}T23:00"), at("2026-11-${10 + day}T00:00"), at("2026-11-${11 + day}T00:00")).first().newByType
            Day(day, introduced, reviews, ItemType.entries.associateWith { left[it] ?: 0 })
        }
    }

    private fun log(title: String, days: List<Day>) {
        Log.i("N3NewLimits", title)
        Log.i("N3NewLimits", "day | new V | new K | new G | total | reviews | left V | left K | left G")
        days.forEach { d ->
            val v = d.new.getValue(ItemType.VOCABULARY); val k = d.new.getValue(ItemType.KANJI); val g = d.new.getValue(ItemType.GRAMMAR)
            Log.i("N3NewLimits", "${d.day} | $v | $k | $g | ${v + k + g} | ${d.reviews} | ${d.remaining[ItemType.VOCABULARY]} | ${d.remaining[ItemType.KANJI]} | ${d.remaining[ItemType.GRAMMAR]}")
        }
    }

    @Test
    fun sevenDaysOnTheShippedContent_20_10_10() {
        importShippedPacks()
        val days = simulate(settings(20, 10, 10), 7)
        log("20/10/10", days)
        days.forEach { d ->
            // Every day: each type exactly its own cap, 40 in all, whatever the reviews.
            assertEquals(mapOf(ItemType.VOCABULARY to 20, ItemType.KANJI to 10, ItemType.GRAMMAR to 10), d.new)
            assertEquals(mapOf(ItemType.VOCABULARY to 910 - 20 * d.day, ItemType.KANJI to 420 - 10 * d.day, ItemType.GRAMMAR to 181 - 10 * d.day), d.remaining)
        }
        assertTrue("due reviews came back during the week", days.drop(1).all { it.reviews > 0 })
        // History intact: one first answer per introduced item, nothing counted twice.
        assertEquals(280, db.countRows("review_records WHERE was_new = 1"))
        assertEquals(280, db.countRows("learning_progress"))
    }

    @Test
    fun sevenDaysOnTheShippedContent_otherCaps_5_15_0() {
        importShippedPacks()
        val days = simulate(settings(5, 15, 0), 7)
        log("5/15/0", days)
        days.forEach { d -> assertEquals(mapOf(ItemType.VOCABULARY to 5, ItemType.KANJI to 15, ItemType.GRAMMAR to 0), d.new) }
        assertEquals(181, days.last().remaining.getValue(ItemType.GRAMMAR)) // grammar off: none started
    }

    @Test
    fun aTypeRunningOutOfContent_leavesItsAllowanceUnused() {
        importShippedPacks()
        // 181 patterns at 100 a day: 100, 81, then nothing; words and kanji keep their own pace.
        val days = simulate(settings(10, 10, 100), 3)
        log("10/10/100", days)
        assertEquals(listOf(100, 81, 0), days.map { it.new.getValue(ItemType.GRAMMAR) })
        assertTrue(days.all { it.new.getValue(ItemType.VOCABULARY) == 10 && it.new.getValue(ItemType.KANJI) == 10 })
    }
}
