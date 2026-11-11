package com.n3sensei.app.domain.srs

import com.n3sensei.app.domain.model.ItemType

/** Which cards a review session takes. */
enum class ReviewMode {
    /** Due items, then new items, within today's limits (the usual session). */
    SCHEDULED,

    /** Due items only, no new item. */
    DUE_ONLY,

    /**
     * Items whose last answer was "Quên", due or not yet, within today's review limit. Answers
     * go through the same scheduler as any review (the item is simply asked again early).
     */
    FORGOTTEN,
}

/**
 * A review session's content: one type ([type]), or all three mixed (null), in [mode].
 * Every type keeps its own schedule (`learning_progress` is keyed by type and id); the scope
 * only chooses which scheduled items are asked now.
 */
data class ReviewScope(val type: ItemType? = null, val mode: ReviewMode = ReviewMode.SCHEDULED) {
    fun includes(t: ItemType): Boolean = type == null || type == t

    companion object {
        val ALL = ReviewScope()
    }
}
