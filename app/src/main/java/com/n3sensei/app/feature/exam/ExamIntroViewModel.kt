package com.n3sensei.app.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.AttemptStatus
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamSessionManager
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamTimer
import com.n3sensei.app.domain.repository.ExamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

data class ExamIntroUiState(
    val set: ExamSet,
    /** The attempt to continue, while it still has time. */
    val unfinished: ExamAttempt?,
    /** Time left on [unfinished]; null without a limit. */
    val unfinishedRemainingMillis: Long?,
    val submittedAttempts: Int,
    /** Latest submitted attempt (score), null before the first. */
    val lastResult: ExamAttempt?,
)

/** A set before it is taken. Follows the set's attempts, so coming back from the exam updates it. */
class ExamIntroViewModel(
    examSetId: String,
    exams: ExamRepository,
    private val sessions: ExamSessionManager,
    private val clock: Clock,
    ticks: Flow<Unit> = ExamTimer.ticks(),
) : ViewModel() {

    // prepare() also submits attempts whose time ran out while the app was closed.
    private val set = flow { emit(sessions.prepare(examSetId)?.set) }

    val state: StateFlow<UiState<ExamIntroUiState>> =
        combine(set, exams.observeAttempts(examSetId), ticks) { set, attempts, _ ->
            if (set == null) return@combine UiState.NotFound
            val now = clock.millis()
            val unfinished = attempts.firstOrNull { it.status == AttemptStatus.IN_PROGRESS && !ExamTimer.isExpired(it, now) }
            val submitted = attempts.filter { it.status.isSubmitted }
            UiState.Success(
                ExamIntroUiState(
                    set, unfinished, unfinished?.let { ExamTimer.remainingMillis(it, now) },
                    submitted.size, submitted.maxByOrNull { it.submittedAt ?: 0 },
                ),
            ) as UiState<ExamIntroUiState>
        }
            .catch { emit(UiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun abandon(attemptId: String) {
        viewModelScope.launch { sessions.abandon(attemptId) }
    }
}
