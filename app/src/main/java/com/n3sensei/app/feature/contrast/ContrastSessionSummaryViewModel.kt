package com.n3sensei.app.feature.contrast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.contrast.Accuracy
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastMastery
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.GetContrastProgress
import com.n3sensei.app.domain.contrast.GetPracticeResult
import com.n3sensei.app.domain.contrast.PositionState
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class SummaryGroup(val groupId: String, val title: String, val mastery: ContrastMastery, val nextDueAt: Instant?)

/** One answer of the session, read from the attempt's own snapshots (still readable after a pack update). */
data class SummaryAnswer(
    val position: Int,
    val groupTitle: String,
    val type: ContrastQuestionType,
    val outcome: AnswerOutcome,
    val chosenText: String?,
    val correctText: String,
)

data class ContrastSessionSummary(
    val groups: List<SummaryGroup>,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    /** Not answered (left or still open): never counted as wrong. */
    val pending: Int,
    /** Question gone from the pack: never counted as wrong. */
    val unavailable: Int,
    /** correct / (correct + incorrect); [Accuracy.value] is null when nothing was answered. */
    val accuracy: Accuracy,
    val answers: List<SummaryAnswer>,
) {
    val handled: Int get() = correct + incorrect + skipped
}

/** The summary of one session: counts from GetPracticeResult, state and next review per group. Reads only. */
class ContrastSessionSummaryViewModel(
    private val sessionId: String,
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    private val progress: ContrastProgressRepository,
    private val clock: Clock,
    config: ContrastConfig = ContrastConfig(),
) : ViewModel() {

    private val result = GetPracticeResult(content, practice, progress)
    private val getProgress = GetContrastProgress(content, practice, progress, config)
    private val _state = MutableStateFlow<UiState<ContrastSessionSummary>>(UiState.Loading)
    val state: StateFlow<UiState<ContrastSessionSummary>> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                build()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }
    }

    private suspend fun build(): UiState<ContrastSessionSummary> {
        val practiceResult = result(sessionId) ?: return UiState.NotFound
        val groupIds = practiceResult.session.items.map { it.groupId }.distinct()
        val titles = groupIds.associateWith { id -> content.getDetail(id)?.group?.title ?: id }
        val groups = groupIds.map { id ->
            SummaryGroup(id, titles.getValue(id), getProgress.ofGroup(id, clock.zone).mastery, progress.progress(id)?.dueAt)
        }
        val attempts = practice.attempts(sessionId)
        return UiState.Success(
            ContrastSessionSummary(
                groups = groups,
                correct = practiceResult.count(PositionState.CORRECT),
                incorrect = practiceResult.count(PositionState.INCORRECT),
                skipped = practiceResult.count(PositionState.SKIPPED),
                pending = practiceResult.count(PositionState.PENDING),
                unavailable = practiceResult.count(PositionState.UNAVAILABLE),
                accuracy = Accuracy.of(attempts),
                answers = attempts.map { SummaryAnswer(it.position, titles[it.groupId] ?: it.groupId, it.questionType, it.outcome, it.chosenText, it.correctText) },
            ),
        )
    }
}
