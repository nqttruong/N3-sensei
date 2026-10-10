package com.n3sensei.app.domain.model

/** Where the learner stands with an item, derived from stored progress. */
enum class LearningStatus {
    /** No progress record yet. */
    NEW,
    LEARNING,
    REVIEW,
    ;

    companion object {
        fun from(state: ProgressState?): LearningStatus = when (state) {
            null -> NEW
            ProgressState.LEARNING -> LEARNING
            ProgressState.REVIEW -> REVIEW
        }
    }
}

/** A content item together with the learner's own state for it. */
data class TrackedItem<out T>(
    val item: T,
    val status: LearningStatus,
    val isMarked: Boolean,
)

/** Learner state of one item, shown on detail screens. */
data class ItemUserState(
    val status: LearningStatus,
    val isMarked: Boolean,
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
)
