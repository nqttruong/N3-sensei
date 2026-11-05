package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.dashboard.ExamDashboard
import com.n3sensei.app.domain.dashboard.ExamDashboardManager
import com.n3sensei.app.domain.dashboard.SkillDetail
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.model.ItemRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads [fetch] into a [UiState]; a reload keeps showing the last data until the new one is ready. */
abstract class DashboardScreenViewModel<T>(protected val dashboard: ExamDashboardManager) : ViewModel() {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    protected abstract suspend fun fetch(): T

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    /** Marks the items, then [then] (opening the practice of marked items). */
    fun practice(refs: List<ItemRef>, then: () -> Unit) {
        viewModelScope.launch {
            dashboard.markForPractice(refs)
            then()
        }
    }
}

/** "Phân tích JLPT": overview, today's plan, skills, errors, progress. Reloaded when the screen comes back. */
class ExamDashboardViewModel(dashboard: ExamDashboardManager) : DashboardScreenViewModel<ExamDashboard>(dashboard) {
    init {
        load()
    }

    override suspend fun fetch() = dashboard.load()

    fun setBudget(minutes: Int) {
        viewModelScope.launch {
            dashboard.setBudget(minutes)
            load()
        }
    }
}

class SkillDetailViewModel(private val skill: ExamSkill, dashboard: ExamDashboardManager) : DashboardScreenViewModel<SkillDetail>(dashboard) {
    init {
        load()
    }

    override suspend fun fetch() = dashboard.skillDetail(skill)
}
