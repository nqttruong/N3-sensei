package com.n3sensei.app.domain.srs

/**
 * Daily caps, counted per local calendar day. Every answer to an item that already had
 * progress counts as one review (including repeats after "Quên"); the first answer to a new
 * item counts as one new item.
 */
data class DailyLimits(val newPerDay: Int = DEFAULT_NEW, val reviewsPerDay: Int = DEFAULT_REVIEWS) {
    init {
        require(newPerDay in 0..MAX_NEW) { "newPerDay out of range" }
        require(reviewsPerDay in 1..MAX_REVIEWS) { "reviewsPerDay out of range" }
    }

    companion object {
        const val DEFAULT_NEW = 20
        const val DEFAULT_REVIEWS = 200
        const val MAX_NEW = 200
        const val MAX_REVIEWS = 2000
    }
}

data class ReviewSettings(
    val ladder: ReviewLadder = ReviewLadder.DEFAULT,
    val limits: DailyLimits = DailyLimits(),
)
