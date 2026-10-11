package com.n3sensei.app.testing

import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * TEST DATA ONLY: a small review history whose statistics were counted by hand. Day D is
 * [today] in [zone]; the seed pack must be stored as real (non-seed) content.
 *
 * Records (local time):
 *  r1 経験 (v01)  first  GOOD   D-1 22:00
 *  r2 経験        review AGAIN  D   08:00
 *  r3 経験        review GOOD   D   09:00
 *  r4 増える(v02) first  HARD   D   08:30
 *  r5 経 (k01)    first  AGAIN  D   23:59
 *  r8 〜たばかり   first  EASY   D-3 10:00
 *  r9 〜たばかり   review HARD   D   11:00
 *  rX removed item "gone" review GOOD D 10:00   → excluded everywhere
 *
 * Expected for TODAY: answers 5 (r2 r3 r4 r5 r9), distinct items 4, new items 2 (r4 r5),
 * review answers 3 (r2 r3 r9), distinct reviewed 2 (経験, 〜たばかり), review ratings
 * AGAIN 1 / GOOD 1 / HARD 1 → recall 2/3 = 67 %.
 * Expected for LAST 7 DAYS: answers 7, distinct 4, new 4, review answers 3, distinct reviewed 2.
 * Trouble TODAY (Quên+Khó, then Quên, then latest): 経, 経験, 〜たばかり, 増える.
 * Study days: D-3, D-1, D → current streak 2, longest 2.
 *
 * Progress at [now] = D 12:00: 経験 REVIEW due D 00:00:01 (due), 増える LEARNING due D-1 20:00
 * (overdue), 経 MASTERED due D+5, 〜たばかり LEARNING due D+1 10:00, "gone" due D 06:00 (excluded).
 * → due 2, overdue 1; vocabulary new 2 / learning 1 / review 1; kanji new 1 / mastered 1;
 * grammar learning 1; forecast (D..D+6) = 2, 1, 0, 0, 0, 1, 0.
 */
class AnalyticsFixture(private val zone: ZoneId, private val today: LocalDate) {

    fun at(dayOffset: Long, hour: Int, minute: Int, second: Int = 0): Instant =
        today.plusDays(dayOffset).atTime(hour, minute, second).atZone(zone).toInstant()

    val now: Instant get() = at(0, 12, 0)

    suspend fun insert(db: N3Database) {
        val dao = db.progressDao()
        fun record(type: ItemType, id: String, rating: ReviewRating, at: Instant, wasNew: Boolean) =
            ReviewRecordEntity(
                itemType = type, itemId = id, rating = rating, reviewedAt = at.toEpochMilli(),
                intervalBeforeMinutes = 0, intervalAfterMinutes = 60, dueAtAfter = at.plus(Duration.ofHours(1)).toEpochMilli(),
                requestId = "fixture-$id-${at.toEpochMilli()}", wasNew = wasNew,
            )
        val v = ItemType.VOCABULARY
        listOf(
            record(v, SeedData.V_KEIKEN, ReviewRating.GOOD, at(-1, 22, 0), true),
            record(v, SeedData.V_KEIKEN, ReviewRating.AGAIN, at(0, 8, 0), false),
            record(v, SeedData.V_KEIKEN, ReviewRating.GOOD, at(0, 9, 0), false),
            record(v, SeedData.V_FUERU, ReviewRating.HARD, at(0, 8, 30), true),
            record(ItemType.KANJI, SeedData.K_KEI, ReviewRating.AGAIN, at(0, 23, 59), true),
            record(ItemType.GRAMMAR, SeedData.G_TABAKARI, ReviewRating.EASY, at(-3, 10, 0), true),
            record(ItemType.GRAMMAR, SeedData.G_TABAKARI, ReviewRating.HARD, at(0, 11, 0), false),
            record(v, "gone", ReviewRating.GOOD, at(0, 10, 0), false),
        ).forEach { dao.insertRecord(it) }

        fun progress(type: ItemType, id: String, state: ProgressState, due: Instant) =
            LearningProgressEntity(type, id, state, 1, 60, due.toEpochMilli(), 0, 1, 0, 0, 0)
        listOf(
            progress(v, SeedData.V_KEIKEN, ProgressState.REVIEW, at(0, 0, 0, 1)),
            progress(v, SeedData.V_FUERU, ProgressState.LEARNING, at(-1, 20, 0)),
            progress(ItemType.KANJI, SeedData.K_KEI, ProgressState.MASTERED, at(5, 9, 0)),
            progress(ItemType.GRAMMAR, SeedData.G_TABAKARI, ProgressState.LEARNING, at(1, 10, 0)),
            progress(v, "gone", ProgressState.REVIEW, at(0, 6, 0)),
        ).forEach { dao.upsert(it) }
    }
}
