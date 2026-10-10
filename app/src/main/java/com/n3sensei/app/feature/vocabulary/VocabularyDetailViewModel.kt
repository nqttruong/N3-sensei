package com.n3sensei.app.feature.vocabulary

import com.n3sensei.app.core.ui.state.DetailViewModel
import com.n3sensei.app.domain.model.VocabularyDetail
import com.n3sensei.app.domain.repository.ContentRepository

class VocabularyDetailViewModel(
    private val itemId: String,
    private val contentRepository: ContentRepository,
) : DetailViewModel<VocabularyDetail>() {
    init {
        refresh()
    }

    override suspend fun load(): VocabularyDetail? = contentRepository.getVocabularyDetail(itemId)
}
