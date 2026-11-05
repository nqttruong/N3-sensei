package com.n3sensei.app.domain.proverb

import java.time.LocalDate

/**
 * A Japanese proverb for the home screen. [furigana] is `{base|reading}` markup of the
 * original text; the Vietnamese translation and note are written for the app.
 */
data class Proverb(
    val id: String,
    val furigana: String,
    val translationVi: String,
    val noteVi: String?,
)

data class ProverbSet(
    val proverbs: List<Proverb>,
    /** Where the set comes from, as stated in the bundled file; never an invented citation. */
    val source: String,
)

/** The bundled proverb set (offline; nothing is downloaded). */
interface ProverbRepository {
    suspend fun load(): ProverbSet
}

object ProverbOfTheDay {
    /**
     * The proverb for [date]: one per day, in turn through the set, the same all day.
     * [skip] moves to the following ones ("Câu khác").
     */
    fun pick(proverbs: List<Proverb>, date: LocalDate, skip: Int = 0): Proverb? {
        if (proverbs.isEmpty()) return null
        return proverbs[Math.floorMod(date.toEpochDay() + skip, proverbs.size.toLong()).toInt()]
    }
}
