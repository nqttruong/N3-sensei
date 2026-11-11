package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemType

/**
 * Daily caps, counted per local calendar day. Every answer to an item that already had
 * progress counts as one review (including repeats after "Quên"); the first answer to a new
 * item counts as one new item of its type.
 *
 * New items have one independent cap per type ([newVocabulary], [newKanji], [newGrammar]): no
 * shared total, no cap on their sum, and what one type leaves unused is never given to another.
 * Reviews keep one cap for all types ([reviewsPerDay]).
 */
data class DailyLimits(
    val newVocabulary: Int = DEFAULT_NEW_VOCABULARY,
    val newKanji: Int = DEFAULT_NEW_KANJI,
    val newGrammar: Int = DEFAULT_NEW_GRAMMAR,
    val reviewsPerDay: Int = DEFAULT_REVIEWS,
) {
    init {
        require(newVocabulary in 0..MAX_NEW_PER_TYPE) { "newVocabulary out of range" }
        require(newKanji in 0..MAX_NEW_PER_TYPE) { "newKanji out of range" }
        require(newGrammar in 0..MAX_NEW_PER_TYPE) { "newGrammar out of range" }
        require(reviewsPerDay in 1..MAX_REVIEWS) { "reviewsPerDay out of range" }
    }

    /** New items of [type] that may be introduced per day (0: none). */
    fun newPerDay(type: ItemType): Int = when (type) {
        ItemType.VOCABULARY -> newVocabulary
        ItemType.KANJI -> newKanji
        ItemType.GRAMMAR -> newGrammar
    }

    /**
     * The three caps added up: for display and forecasts only (what a day can bring at most),
     * never a limit itself.
     */
    val newPerDayTotal: Int get() = newVocabulary + newKanji + newGrammar

    companion object {
        const val DEFAULT_NEW_VOCABULARY = 20
        const val DEFAULT_NEW_KANJI = 10
        const val DEFAULT_NEW_GRAMMAR = 10
        const val DEFAULT_REVIEWS = 200
        const val MAX_NEW_PER_TYPE = 100
        const val MAX_REVIEWS = 2000

        /** Range of the single shared cap stored before per-type caps (read from old settings and backups). */
        const val LEGACY_MAX_NEW = 200
    }
}

data class ReviewSettings(
    val ladder: ReviewLadder = ReviewLadder.DEFAULT,
    val limits: DailyLimits = DailyLimits(),
)
