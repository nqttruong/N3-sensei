package com.n3sensei.app.feature.kanji

import com.n3sensei.app.core.ui.state.LessonFilteredListViewModel
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow

class KanjiListViewModel(
    private val contentRepository: ContentRepository,
) : LessonFilteredListViewModel<Kanji>(contentRepository) {
    override fun observeAll(lessonId: String?): Flow<List<Kanji>> =
        contentRepository.observeKanji(lessonId)

    override suspend fun search(query: String, lessonId: String?): List<Kanji> =
        contentRepository.searchKanji(query, lessonId, limit = ContentRepository.MAX_PAGE_SIZE)
}
