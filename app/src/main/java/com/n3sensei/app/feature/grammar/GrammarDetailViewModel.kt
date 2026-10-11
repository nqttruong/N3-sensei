package com.n3sensei.app.feature.grammar

import com.n3sensei.app.core.ui.state.TrackedDetailViewModel
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import java.time.Clock

class GrammarDetailViewModel(
    private val itemId: String,
    private val contentRepository: ContentRepository,
    progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    clock: Clock,
) : TrackedDetailViewModel<GrammarDetail>(ItemRef(ItemType.GRAMMAR, itemId), progressRepository, preferencesRepository, clock) {
    init {
        refresh()
    }

    override suspend fun load(): GrammarDetail? = contentRepository.getGrammarDetail(itemId)
}
