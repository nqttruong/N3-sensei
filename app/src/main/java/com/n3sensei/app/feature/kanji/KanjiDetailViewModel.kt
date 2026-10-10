package com.n3sensei.app.feature.kanji

import com.n3sensei.app.core.ui.state.TrackedDetailViewModel
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository

class KanjiDetailViewModel(
    private val itemId: String,
    private val contentRepository: ContentRepository,
    progressRepository: ProgressRepository,
) : TrackedDetailViewModel<KanjiDetail>(ItemRef(ItemType.KANJI, itemId), progressRepository) {
    init {
        refresh()
    }

    override suspend fun load(): KanjiDetail? = contentRepository.getKanjiDetail(itemId)
}
