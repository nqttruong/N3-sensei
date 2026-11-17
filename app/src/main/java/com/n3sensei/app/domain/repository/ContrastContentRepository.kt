package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.ContrastFilter
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastGroupLink
import com.n3sensei.app.domain.contrast.ContrastSourceIds
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.InstalledContrastPack
import com.n3sensei.app.domain.contrast.RetiredGroup
import kotlinx.coroutines.flow.Flow

/**
 * READ-ONLY access to the installed Contrast pack (published content only). Never touches
 * learner data and never reads the existing grammar/vocabulary/kanji tables (that is
 * [ContrastSourceResolver]).
 */
interface ContrastContentRepository {
    /** Published groups matching [filter], in pack order; [ContrastFilter.query] matched accent-insensitively. */
    fun observeGroups(filter: ContrastFilter = ContrastFilter()): Flow<List<ContrastGroup>>

    /** The group's content; [ContrastDetail.sources] is left empty (resolved by the use case). Null when not published. */
    suspend fun getDetail(groupId: String): ContrastDetail?

    /** Questions of a published group, in pack order. */
    suspend fun getQuestions(groupId: String): List<ContrastQuestion>

    /** A question of the installed pack; null when it is no longer there. */
    suspend fun getQuestion(questionId: String): ContrastQuestion?

    suspend fun installedPack(): InstalledContrastPack?

    /** Present when the group was retired by the installed pack. */
    suspend fun retiredGroup(groupId: String): RetiredGroup?

    /**
     * Published groups with at least one item whose source is one of [sources], in pack order.
     * Empty when no reviewed pack is installed (only published groups are in the database).
     */
    fun observeGroupsForSources(sources: ContrastSourceIds): Flow<List<ContrastGroupLink>>
}
