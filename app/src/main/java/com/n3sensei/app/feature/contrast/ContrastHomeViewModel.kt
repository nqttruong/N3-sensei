package com.n3sensei.app.feature.contrast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.AnalyzeContrastMistakes
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.GetContrastProgress
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/** A directed confusion seen in the learner's answers: expected [expectedLabel], chose [chosenLabel]. */
data class ConfusionRow(val groupId: String, val groupTitle: String, val expectedLabel: String, val chosenLabel: String, val count: Int)

data class ContrastHomeUiState(
    /** Published groups. */
    val available: Int,
    /** Groups with a schedule that are not mastered (LEARNING or REVIEWING). */
    val learning: Int,
    val mastered: Int,
    /** Groups due now (Contrast's own schedule). */
    val due: Int,
    /** The published group answered most recently; null before any answer. */
    val continueGroup: ContrastGroupRow?,
    val dueGroups: List<ContrastGroupRow>,
    /** From real answers only; empty before any confusion. */
    val confusions: List<ConfusionRow>,
)

/**
 * Contrast Lab home. Everything here is read: opening it never creates a session, an attempt or
 * a schedule. Empty when no reviewed pack is installed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContrastHomeViewModel(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val clock: Clock,
    config: ContrastConfig = ContrastConfig(),
) : ViewModel() {

    private val getProgress = GetContrastProgress(content, practice, progress, config)
    private val analyze = AnalyzeContrastMistakes(content, practice, config)
    private val reload = MutableStateFlow(0)

    val state: StateFlow<UiState<ContrastHomeUiState>> = reload
        .flatMapLatest { content.observeGroups() }
        .mapLatest { groups -> if (groups.isEmpty()) UiState.Empty else UiState.Success(build(groups)) }
        .onStart<UiState<ContrastHomeUiState>> { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun retry() {
        reload.value++
    }

    private suspend fun build(groups: List<ContrastGroup>): ContrastHomeUiState {
        val now = clock.instant()
        val zone = clock.zone
        val rows = groups.map { ContrastGroupRow.of(it, getProgress.ofGroup(it.id, zone), now) }
        val byId = rows.associateBy { it.group.id }
        val lastAnswered = practice.attemptsBetween(null, null).asReversed().firstOrNull { it.groupId in byId }?.groupId
        val report = analyze()
        val confusions = report.confusions.filter { it.groupId in byId }.take(MAX_CONFUSIONS).map { c ->
            val labels = content.getDetail(c.groupId)?.items.orEmpty().associate { it.id to it.label }
            ConfusionRow(c.groupId, byId.getValue(c.groupId).group.title, labels[c.expectedItemId] ?: c.expectedItemId, labels[c.chosenItemId] ?: c.chosenItemId, c.count)
        }
        val due = rows.filter { it.isDue }
        return ContrastHomeUiState(
            available = rows.size,
            learning = rows.count { it.mastery == ContrastMastery.LEARNING || it.mastery == ContrastMastery.REVIEWING },
            mastered = rows.count { it.mastery == ContrastMastery.MASTERED },
            due = due.size,
            continueGroup = lastAnswered?.let(byId::get),
            dueGroups = due.take(MAX_DUE),
            confusions = confusions,
        )
    }

    private companion object {
        const val MAX_DUE = 5
        const val MAX_CONFUSIONS = 3
    }
}
