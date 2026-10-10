package com.n3sensei.app.feature.grammar

import com.n3sensei.app.core.ui.state.ItemBrowserViewModel
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow

class GrammarListViewModel(
    private val contentRepository: ContentRepository,
) : ItemBrowserViewModel<GrammarPoint>(contentRepository) {
    override fun observe(query: ItemQuery): Flow<List<TrackedItem<GrammarPoint>>> = contentRepository.observeGrammarItems(query)
}
