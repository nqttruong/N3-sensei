package com.n3sensei.app.feature.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.STOP_TIMEOUT_MILLIS
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.course.CourseBuilder
import com.n3sensei.app.domain.course.CourseLesson
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three parts of a lesson; the learner moves between them freely, nothing is locked. */
enum class LessonTab { KANJI, VOCABULARY, GRAMMAR }

/** Loading state of an expanded kanji's words or grammar point's details. */
sealed interface Expansion<out T> {
    data object Loading : Expansion<Nothing>
    data class Loaded<T>(val value: T) : Expansion<T>
    data object Failed : Expansion<Nothing>
}

/**
 * One course lesson ([number], 1-based). Switching tabs or expanding a row only changes what
 * is shown: no learning record is written here.
 */
class LessonDetailViewModel(
    val number: Int,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    val state: StateFlow<UiState<CourseLesson>> = CourseBuilder(contentRepository).observe()
        .map<List<CourseLesson>, UiState<CourseLesson>> { all -> all.getOrNull(number - 1)?.let { UiState.Success(it) } ?: UiState.NotFound }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _selectedTab = MutableStateFlow(LessonTab.KANJI)
    val selectedTab: StateFlow<LessonTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: LessonTab) {
        _selectedTab.value = tab
    }

    private val _kanjiWords = MutableStateFlow<Map<String, Expansion<List<KanjiWord>>>>(emptyMap())

    /** Words of the kanji the learner expanded, by kanji id (kept while the screen lives). */
    val kanjiWords: StateFlow<Map<String, Expansion<List<KanjiWord>>>> = _kanjiWords.asStateFlow()

    private val _grammar = MutableStateFlow<Map<String, Expansion<GrammarDetail>>>(emptyMap())
    val grammarDetails: StateFlow<Map<String, Expansion<GrammarDetail>>> = _grammar.asStateFlow()

    fun toggleKanji(id: String) = toggle(_kanjiWords, id) { contentRepository.getKanjiWords(id) }

    fun toggleGrammar(id: String) = toggle(_grammar, id) { contentRepository.getGrammarDetail(id) }

    private fun <T> toggle(map: MutableStateFlow<Map<String, Expansion<T>>>, id: String, load: suspend () -> T?) {
        if (id in map.value) {
            map.update { it - id }
            return
        }
        map.update { it + (id to Expansion.Loading) }
        viewModelScope.launch {
            val result = try {
                load()?.let { Expansion.Loaded(it) } ?: Expansion.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Expansion.Failed
            }
            map.update { if (id in it) it + (id to result) else it }
        }
    }
}
