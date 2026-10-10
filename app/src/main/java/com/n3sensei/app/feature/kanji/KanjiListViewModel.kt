package com.n3sensei.app.feature.kanji

import com.n3sensei.app.core.ui.state.ItemBrowserViewModel
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow

class KanjiListViewModel(
    private val contentRepository: ContentRepository,
) : ItemBrowserViewModel<Kanji>(contentRepository) {
    override fun observe(query: ItemQuery): Flow<List<TrackedItem<Kanji>>> = contentRepository.observeKanjiItems(query)
}
