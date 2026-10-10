package com.n3sensei.app.feature.kanji

import com.n3sensei.app.core.ui.state.DetailViewModel
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.repository.ContentRepository

class KanjiDetailViewModel(
    private val itemId: String,
    private val contentRepository: ContentRepository,
) : DetailViewModel<KanjiDetail>() {
    init {
        refresh()
    }

    override suspend fun load(): KanjiDetail? = contentRepository.getKanjiDetail(itemId)
}
