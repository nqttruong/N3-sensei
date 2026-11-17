package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastSourceResolver
import kotlinx.coroutines.flow.Flow

/** A group in the list, with nothing computed from learner data (mastery is read by [GetContrastProgress]). */
class ObserveContrastGroups(private val content: ContrastContentRepository) {
    /** List and search are one use case: a search is a [ContrastFilter.query]. */
    operator fun invoke(filter: ContrastFilter = ContrastFilter()): Flow<List<ContrastGroup>> = content.observeGroups(filter)
}

sealed interface GroupDetailResult {
    /** [detail] has [ContrastDetail.sources] resolved against the existing content now. */
    data class Found(val detail: ContrastDetail) : GroupDetailResult

    /** Retired by the installed pack; its history and progress are kept. */
    data class Retired(val retired: RetiredGroup) : GroupDetailResult

    data object NotFound : GroupDetailResult
}

/** The theory screen. Reads only: opening it never creates progress (rule R1). */
class GetContrastGroupDetail(
    private val content: ContrastContentRepository,
    private val sources: ContrastSourceResolver,
) {
    suspend operator fun invoke(groupId: String): GroupDetailResult {
        val detail = content.getDetail(groupId)
            ?: return content.retiredGroup(groupId)?.let { GroupDetailResult.Retired(it) } ?: GroupDetailResult.NotFound
        return GroupDetailResult.Found(detail.copy(sources = sources.resolve(detail.items)))
    }
}
