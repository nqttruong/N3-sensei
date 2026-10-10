package com.n3sensei.app.feature.vocabulary

import com.n3sensei.app.core.ui.state.TrackedDetailViewModel
import com.n3sensei.app.domain.model.VocabularyDetail
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository

class VocabularyDetailViewModel(
    private val itemId: String,
    private val contentRepository: ContentRepository,
    progressRepository: ProgressRepository,
) : TrackedDetailViewModel<VocabularyDetail>(ItemRef(ItemType.VOCABULARY, itemId), progressRepository) {
    init {
        refresh()
    }

    override suspend fun load(): VocabularyDetail? = contentRepository.getVocabularyDetail(itemId)
}
