package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.ExamSetFilter
import com.n3sensei.app.domain.exam.ExamSetSummary
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamSubmissionManager
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Loại đề" filter: any, full exams, or exams training one skill. */
enum class ExamKindFilter(val type: ExamType?, val skill: ExamSkill?) {
    ALL(null, null),
    FULL(ExamType.FULL, null),
    VOCABULARY(null, ExamSkill.VOCABULARY),
    KANJI(null, ExamSkill.KANJI),
    GRAMMAR(null, ExamSkill.GRAMMAR),
    READING(null, ExamSkill.READING),
    LISTENING(null, ExamSkill.LISTENING),
}

data class ExamListUiState(
    /** "N5" … "N1", or null for every level. */
    val level: String?,
    val kind: ExamKindFilter,
    val sets: List<ExamSetSummary>,
    /** Sets in the app, before filtering: tells "no exams" from "none match". */
    val totalSets: Int,
)

class ExamListViewModel(exams: ExamRepository, submission: ExamSubmissionManager) : ViewModel() {

    private val level = MutableStateFlow<String?>(null)
    private val kind = MutableStateFlow(ExamKindFilter.ALL)

    val state: StateFlow<UiState<ExamListUiState>> =
        combine(exams.observeExamSets(), level, kind) { all, level, kind ->
            val filter = ExamSetFilter(jlptLevel = level, skill = kind.skill, type = kind.type)
            UiState.Success(ExamListUiState(level, kind, all.filter(filter::matches), all.size)) as UiState<ExamListUiState>
        }
            .catch { emit(UiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        // An attempt whose time ran out while the app was closed shows as done, not "in progress".
        viewModelScope.launch { submission.submitExpired() }
    }

    fun selectLevel(value: String?) = level.update { value }

    fun selectKind(value: ExamKindFilter) = kind.update { value }

    companion object {
        val LEVELS = listOf("N5", "N4", "N3", "N2", "N1")
    }
}
