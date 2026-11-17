package com.n3sensei.app.data.repository

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
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.domain.srs.ReviewTime
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.testing.InMemoryPreferencesRepository
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
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
import java.time.ZoneId

/**
 * Kanji N2 in the existing review, on the real schema with every bundled pack: new kanji come
 * only from the lessons chosen in Cài đặt ôn tập → Bài Kanji (N3: every lesson, N2: none by
 * default), with the same scheduler and the same daily kanji cap; kanji already studied stay
 * scheduled whatever is chosen.
 */
@RunWith(AndroidJUnit4::class)
class KanjiReviewLessonsTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val t0 = Instant.parse("2026-11-13T02:00:00Z")
    private lateinit var db: N3Database
    private lateinit var prefs: InMemoryPreferencesRepository
    private lateinit var repository: OfflineProgressRepository
    private lateinit var submit: SubmitReviewUseCase
    private val settings = ReviewSettings()

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidContentImportRepository.BUNDLED_PACK_ASSETS.forEach { asset ->
            val json = context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(asset, ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), Clock.systemUTC()).import(json).outcome)
        }
        prefs = InMemoryPreferencesRepository(UserPreferences(onboardingCompleted = true))
        val content = OfflineContentRepository(db.contentDao(), db.contentPackDao())
        // Wired as in the app (AppContainer).
        repository = OfflineProgressRepository(db.progressDao(), Clock.systemUTC(), KanjiReviewLessons.observeAllowedLessons(content, prefs))
        submit = SubmitReviewUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    private fun choose(level: String, selection: LessonSelection) = runBlocking {
        prefs.setKanjiReviewLessons(prefs.preferences.value.kanjiReviewLessons.with(level, selection))
    }

    private fun newKanji(limit: Int = 2000) = runBlocking { repository.getNewItemsOfType(ItemType.KANJI, limit).map { it.id } }
    private fun overview(now: Instant = t0) =
        runBlocking { repository.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first() }
    private fun n2Lesson(n: Int) = "n2k-l%02d".format(n)
    private fun idsOfLesson(lessonId: String) = db.openHelper.readableDatabase
        .query("SELECT id FROM kanji WHERE lesson_id = ? ORDER BY sort_order", arrayOf(lessonId)).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Test
    fun byDefault_onlyN3KanjiAreNew_asBeforeN2Existed() {
        assertEquals(420 + 448, db.countRows("kanji"))
        val queue = newKanji()
        assertEquals(420, queue.size)
        assertTrue(queue.none { it.startsWith("n2k-") })
        assertEquals(420, overview().newByType[ItemType.KANJI])
        // Words and grammar are not concerned.
        assertEquals(910, overview().newByType[ItemType.VOCABULARY])
        assertEquals(181, overview().newByType[ItemType.GRAMMAR])
    }

    @Test
    fun oneN2LessonChosen_itsKanjiInDeckOrder_andNothingElse() {
        choose("N3", LessonSelection.Only(emptySet()))
        choose("N2", LessonSelection.Only(setOf(n2Lesson(2))))
        assertEquals(idsOfLesson(n2Lesson(2)), newKanji())
        assertEquals(23, overview().newByType[ItemType.KANJI])
        // The daily kanji cap applies as to any kanji: 10 of them.
        assertEquals(idsOfLesson(n2Lesson(2)).take(10), newKanji(limit = 10))
    }

    @Test
    fun allN2_withAllN3_n3First_thenN2() {
        choose("N2", LessonSelection.All)
        val queue = newKanji()
        assertEquals(868, queue.size)
        assertTrue(queue.take(420).all { !it.startsWith("n2k-") })
        assertEquals(idsOfLesson(n2Lesson(1)), queue.drop(420).take(23))
        assertEquals(868, overview().newByType[ItemType.KANJI])
        choose("N2", LessonSelection.Only(emptySet()))
        assertEquals(420, newKanji().size)
    }

    @Test
    fun choosingN2_neverChangesN3_andNoLessonChosenMeansNoNewKanji() {
        choose("N2", LessonSelection.Only(setOf(n2Lesson(1))))
        assertEquals(LessonSelection.All, prefs.preferences.value.kanjiReviewLessons.of("N3"))
        assertEquals(420 + 23, newKanji().size)
        choose("N3", LessonSelection.Only(emptySet()))
        choose("N2", LessonSelection.Only(emptySet()))
        assertEquals(emptyList<String>(), newKanji())
        assertEquals(null, overview().newByType[ItemType.KANJI])
    }

    @Test
    fun leavingALessonOut_keepsItsKanjisScheduleAndHistory_theyStillFallDue() = runBlocking {
        choose("N3", LessonSelection.Only(emptySet()))
        choose("N2", LessonSelection.Only(setOf(n2Lesson(1))))
        val first = ItemRef(ItemType.KANJI, newKanji(limit = 1).single())
        assertTrue(first.id.startsWith("n2k-"))
        assertEquals(SubmitOutcome.APPLIED, submit(first, "r1", ReviewRating.GOOD, settings, t0, zone))
        val progress = db.progressDao().get(ItemType.KANJI, first.id)!!
        val history = repository.getReviewHistory(first, 10)

        choose("N2", LessonSelection.Only(emptySet()))
        // Not new any more, and nothing deleted or reset.
        assertTrue(first.id !in newKanji())
        assertEquals(progress, db.progressDao().get(ItemType.KANJI, first.id))
        assertEquals(history, repository.getReviewHistory(first, 10))
        // It falls due as scheduled and the review takes it like any due item.
        val later = t0 + Duration.ofDays(3)
        assertTrue(first in repository.getDueItems(later, 100))
        assertTrue(first in repository.getDueItemsOfType(later, ItemType.KANJI, 100))
        assertEquals(1, overview(later).dueByType[ItemType.KANJI])
        assertEquals(SubmitOutcome.APPLIED, submit(first, "r2", ReviewRating.GOOD, settings, later, zone))
        assertEquals(2, repository.getReviewHistory(first, 10).size)
    }
}
