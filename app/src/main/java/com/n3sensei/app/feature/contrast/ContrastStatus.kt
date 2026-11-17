package com.n3sensei.app.feature.contrast

import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.GroupMastery

/**
 * Library status filters. They are not a new domain state: each one is a rule over the domain's
 * [ContrastMastery] (computed from answers, never from reading) and the group's due date.
 *
 * | filter | rule |
 * |--------|------|
 * | NOT_STARTED ("Chưa học") | mastery NEW (no completed block yet) |
 * | IN_PROGRESS ("Đang học") | mastery LEARNING or REVIEWING |
 * | NEEDS_REVIEW ("Cần củng cố") | has a schedule and is due now (any mastery but NEW) |
 * | MASTERED ("Đã nắm vững") | mastery MASTERED |
 *
 * A group can match two filters (e.g. in progress and due).
 */
enum class LibraryStatus { NOT_STARTED, IN_PROGRESS, NEEDS_REVIEW, MASTERED }

object ContrastStatusMapper {
    fun statuses(mastery: ContrastMastery, isDue: Boolean): Set<LibraryStatus> = buildSet {
        when (mastery) {
            ContrastMastery.NEW -> add(LibraryStatus.NOT_STARTED)
            ContrastMastery.LEARNING, ContrastMastery.REVIEWING -> add(LibraryStatus.IN_PROGRESS)
            ContrastMastery.MASTERED -> add(LibraryStatus.MASTERED)
        }
        if (isDue && mastery != ContrastMastery.NEW) add(LibraryStatus.NEEDS_REVIEW)
    }
}

/** A group with what the learner has done on it (read-only). */
data class ContrastGroupRow(
    val group: ContrastGroup,
    val mastery: ContrastMastery,
    val isDue: Boolean,
) {
    val statuses: Set<LibraryStatus> get() = ContrastStatusMapper.statuses(mastery, isDue)

    companion object {
        fun of(group: ContrastGroup, mastery: GroupMastery, now: java.time.Instant) =
            ContrastGroupRow(group, mastery.mastery, mastery.dueAt?.let { !it.isAfter(now) } ?: false)
    }
}
