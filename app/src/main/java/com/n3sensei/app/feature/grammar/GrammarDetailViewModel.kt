package com.n3sensei.app.feature.grammar

import com.n3sensei.app.core.ui.state.DetailViewModel
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.repository.ContentRepository

class GrammarDetailViewModel(
    private val itemId: String,
    private val contentRepository: ContentRepository,
) : DetailViewModel<GrammarDetail>() {
    init {
        refresh()
    }

    override suspend fun load(): GrammarDetail? = contentRepository.getGrammarDetail(itemId)
}
