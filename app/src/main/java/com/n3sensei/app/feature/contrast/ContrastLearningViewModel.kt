package com.n3sensei.app.feature.contrast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.GetContrastGroupDetail
import com.n3sensei.app.domain.contrast.GetContrastProgress
import com.n3sensei.app.domain.contrast.GroupDetailResult
import com.n3sensei.app.domain.contrast.GroupMastery
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import com.n3sensei.app.domain.repository.ContrastSourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

sealed interface ContrastLearningContent {
    /** The group to study; [detail] has its sources resolved against Room now, [mastery] is read-only. */
    data class Group(val detail: ContrastDetail, val mastery: GroupMastery, val isDue: Boolean) : ContrastLearningContent

    /** Retired by the installed pack: its history and progress are kept. */
    data class Retired(val retired: RetiredGroup) : ContrastLearningContent
}

/**
 * The compare / learning screen of one group. Only reads (GetContrastGroupDetail,
 * GetContrastProgress): reading the theory never creates an attempt, a session or a schedule (R1).
 */
class ContrastLearningViewModel(
    private val groupId: String,
    content: ContrastContentRepository,
    sources: ContrastSourceResolver,
    practice: ContrastPracticeRepository,
    progress: ContrastProgressRepository,
    private val clock: Clock,
    config: ContrastConfig = ContrastConfig(),
) : ViewModel() {

    private val getDetail = GetContrastGroupDetail(content, sources)
    private val getProgress = GetContrastProgress(content, practice, progress, config)
    private val _state = MutableStateFlow<UiState<ContrastLearningContent>>(UiState.Loading)
    val state: StateFlow<UiState<ContrastLearningContent>> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                when (val result = getDetail(groupId)) {
                    GroupDetailResult.NotFound -> UiState.NotFound
                    is GroupDetailResult.Retired -> UiState.Success(ContrastLearningContent.Retired(result.retired))
                    is GroupDetailResult.Found -> {
                        val mastery = getProgress.ofGroup(groupId, clock.zone)
                        val isDue = mastery.dueAt?.let { !it.isAfter(clock.instant()) } ?: false
                        UiState.Success(ContrastLearningContent.Group(result.detail, mastery, isDue))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }
}
