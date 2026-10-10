package com.n3sensei.app.feature.vocabulary

import com.n3sensei.app.core.ui.state.LessonFilteredListViewModel
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow

class VocabularyListViewModel(
    private val contentRepository: ContentRepository,
) : LessonFilteredListViewModel<Vocabulary>(contentRepository) {
    override fun observeAll(lessonId: String?): Flow<List<Vocabulary>> =
        contentRepository.observeVocabulary(lessonId)

    override suspend fun search(query: String, lessonId: String?): List<Vocabulary> =
        contentRepository.searchVocabulary(query, lessonId, limit = ContentRepository.MAX_PAGE_SIZE)
}
