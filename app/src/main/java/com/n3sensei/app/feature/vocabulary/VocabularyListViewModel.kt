package com.n3sensei.app.feature.vocabulary

import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.ItemBrowserViewModel
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.Tag
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class VocabularyListViewModel(
    private val contentRepository: ContentRepository,
    initialStatus: LearningStatus? = null,
    initialLessonId: String? = null,
) : ItemBrowserViewModel<Vocabulary>(contentRepository, ItemType.VOCABULARY, initialStatus, initialLessonId) {
    override val tags: StateFlow<List<Tag>> = contentRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    override fun observe(query: ItemQuery): Flow<List<TrackedItem<Vocabulary>>> = contentRepository.observeVocabularyItems(query)
}
