package com.n3sensei.app.feature.grammar

import com.n3sensei.app.core.ui.state.LessonFilteredListViewModel
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow

class GrammarListViewModel(
    private val contentRepository: ContentRepository,
) : LessonFilteredListViewModel<GrammarPoint>(contentRepository) {
    override fun observeAll(lessonId: String?): Flow<List<GrammarPoint>> =
        contentRepository.observeGrammar(lessonId)

    override suspend fun search(query: String, lessonId: String?): List<GrammarPoint> =
        contentRepository.searchGrammar(query, lessonId, limit = ContentRepository.MAX_PAGE_SIZE)
}
