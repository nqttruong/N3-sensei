package com.n3sensei.app.domain.model

/** Where the learner stands with an item, derived from stored progress. */
enum class LearningStatus {
    /** No progress record yet. */
    NEW,
    LEARNING,
    REVIEW,
    MASTERED,
    ;

    companion object {
        fun from(state: ProgressState?): LearningStatus = when (state) {
            null -> NEW
            ProgressState.LEARNING -> LEARNING
            ProgressState.REVIEW -> REVIEW
            ProgressState.MASTERED -> MASTERED
        }
    }
}

/** A content item together with the learner's own state for it. */
data class TrackedItem<out T>(
    val item: T,
    val status: LearningStatus,
    val isMarked: Boolean,
    /** One of the learner's "Dịch nhanh" bookmarks, saved but not studied yet. */
    val isSaved: Boolean = false,
)

/** Learner state of one item, shown on detail screens. */
data class ItemUserState(
    val status: LearningStatus,
    val isMarked: Boolean,
    /** Next scheduled review; null for new items. */
    val dueAt: java.time.Instant? = null,
    /** Zero-based ladder step ("memory level"); null for new items. */
    val stepIndex: Int? = null,
)

/**
 * Filters for browsing items. Blank [text] means no text search; [tagId] only applies to
 * vocabulary.
 */
data class ItemQuery(
    val text: String = "",
    val lessonId: String? = null,
    val status: LearningStatus? = null,
    val markedOnly: Boolean = false,
    val tagId: String? = null,
    /**
     * Only words the learner added with "Thêm từ của tôi" (vocabulary only). Such a word is in
     * the "Từ của tôi" lesson, has an id the add action generated (`user-w-…`, a prefix content
     * packs may not use) and a row in `user_words`, written only by that action.
     */
    val userWordsOnly: Boolean = false,
    /**
     * Also words saved from "Dịch nhanh" but not studied yet (vocabulary only). Only for lists
     * the learner browses ("Từ của tôi", Học → Từ vựng, where an item is); never for what builds
     * a study session, a reminder or the widget, which leave saved words out.
     */
    val includeSaved: Boolean = false,
)
