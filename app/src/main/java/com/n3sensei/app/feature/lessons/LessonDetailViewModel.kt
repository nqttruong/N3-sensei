package com.n3sensei.app.feature.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class LessonDetail(
    val lesson: LessonSummary,
    val vocabulary: List<Vocabulary>,
    val kanji: List<Kanji>,
    val grammar: List<GrammarPoint>,
) {
    val hasItems: Boolean get() = vocabulary.isNotEmpty() || kanji.isNotEmpty() || grammar.isNotEmpty()
}

enum class LessonTab { VOCABULARY, KANJI, GRAMMAR }

class LessonDetailViewModel(
    lessonId: String,
    contentRepository: ContentRepository,
) : ViewModel() {

    val state: StateFlow<UiState<LessonDetail>> = combine(
        contentRepository.observeLesson(lessonId),
        contentRepository.observeVocabulary(lessonId),
        contentRepository.observeKanji(lessonId),
        contentRepository.observeGrammar(lessonId),
    ) { lesson, vocabulary, kanji, grammar ->
        if (lesson == null) UiState.NotFound else UiState.Success(LessonDetail(lesson, vocabulary, kanji, grammar))
    }
        .onStart<UiState<LessonDetail>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _selectedTab = MutableStateFlow(LessonTab.VOCABULARY)
    val selectedTab: StateFlow<LessonTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: LessonTab) {
        _selectedTab.value = tab
    }
}
