package com.n3sensei.app.feature.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AnswerManager
import com.n3sensei.app.domain.exam.AttemptResult
import com.n3sensei.app.domain.exam.ExamAnswer
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSession
import com.n3sensei.app.domain.exam.ExamSessionManager
import com.n3sensei.app.domain.exam.ExamSessionStatus
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamSubmissionManager
import com.n3sensei.app.domain.exam.ExamTimer
import com.n3sensei.app.domain.exam.OpenedSession
import com.n3sensei.app.domain.exam.SubmitOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

data class ExamSessionUiState(
    val status: ExamSessionStatus,
    val set: ExamSet,
    /** Null until the attempt starts (NOT_STARTED). */
    val attempt: ExamAttempt? = null,
    val currentIndex: Int = 0,
    val answers: Map<String, ExamAnswer> = emptyMap(),
    val flagged: Set<String> = emptySet(),
    /** Time left; the full limit before the start; null without a limit. */
    val remainingMillis: Long? = null,
    /** NOT_STARTED: an unfinished attempt of this set that can be continued. */
    val unfinished: ExamAttempt? = null,
    /** Once submitted (or abandoned). */
    val result: AttemptResult? = null,
) {
    val questions: List<ExamQuestion> get() = set.questions
    val currentQuestion: ExamQuestion? get() = questions.getOrNull(currentIndex)
    val canAnswer: Boolean get() = status == ExamSessionStatus.IN_PROGRESS
    val answeredCount: Int get() = questions.count { it.id in answers }
}

/**
 * One sitting of an exam set. Only the attempt id is kept in [SavedStateHandle]; answers, flags
 * and the current question are saved as they change, so rotation, Activity recreation and
 * process death all come back to the same place, and time that passed meanwhile counts.
 * [ticks] only refreshes the countdown: the time left is always read from the clock.
 */
class ExamSessionViewModel(
    private val examSetId: String,
    private val savedState: SavedStateHandle,
    private val sessions: ExamSessionManager,
    private val answerManager: AnswerManager,
    private val submission: ExamSubmissionManager,
    private val clock: Clock,
    private val ticks: Flow<Unit> = ExamTimer.ticks(),
    /** Opens this attempt (continuing it) instead of preparing the set. */
    private val resumeAttemptId: String? = null,
    /** Starts a new attempt as soon as the set is loaded (the learner tapped "Bắt đầu thi"). */
    private val autoStart: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ExamSessionUiState>>(UiState.Loading)
    val state: StateFlow<UiState<ExamSessionUiState>> = _state.asStateFlow()

    /** Saves happen one at a time and in order, and never race a submit. */
    private val writes = Mutex()
    private var ticker: Job? = null

    /** When the current question came on screen, for the time spent on it. */
    private var shownAt = clock.millis()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                // After a process death the saved id wins: the attempt started here is continued, not started again.
                val saved = savedState.get<String>(KEY_ATTEMPT) ?: resumeAttemptId
                if (saved != null) {
                    open(saved)
                } else {
                    prepare()
                    if (autoStart) start()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState.Error(e)
            }
        }
    }

    private suspend fun prepare() {
        val prepared = sessions.prepare(examSetId) ?: run { _state.value = UiState.NotFound; return }
        _state.value = UiState.Success(
            ExamSessionUiState(
                ExamSessionStatus.NOT_STARTED, prepared.set,
                remainingMillis = prepared.set.durationSeconds.takeIf { it > 0 }?.let { it * 1_000L },
                unfinished = prepared.unfinished,
            ),
        )
    }

    /** Starts a new attempt (only before one is running here). */
    fun start() {
        if (current()?.status != ExamSessionStatus.NOT_STARTED) return
        viewModelScope.launch {
            writes.withLock {
                if (current()?.status != ExamSessionStatus.NOT_STARTED) return@withLock
                val session = sessions.start(examSetId) ?: run { _state.value = UiState.NotFound; return@withLock }
                show(session)
            }
        }
    }

    /** Continues the unfinished attempt offered before the start. */
    fun resume() {
        val attemptId = current()?.takeIf { it.status == ExamSessionStatus.NOT_STARTED }?.unfinished?.id ?: return
        viewModelScope.launch { writes.withLock { open(attemptId) } }
    }

    private suspend fun open(attemptId: String) {
        when (val opened = sessions.open(attemptId)) {
            is OpenedSession.Active -> show(opened.session)
            is OpenedSession.Finished -> showResult(opened.result, current()?.set ?: sessions.prepare(examSetId)?.set)
            OpenedSession.Missing -> {
                savedState.remove<String>(KEY_ATTEMPT)
                prepare()
            }
        }
    }

    private fun show(session: ExamSession) {
        savedState[KEY_ATTEMPT] = session.attempt.id
        shownAt = clock.millis()
        _state.value = UiState.Success(
            ExamSessionUiState(
                ExamSessionStatus.IN_PROGRESS, session.set, session.attempt, session.currentIndex, session.answers, session.flagged,
                ExamTimer.remainingMillis(session.attempt, clock.millis()),
            ),
        )
        ticker?.cancel()
        ticker = viewModelScope.launch { ticks.collect { onTick() } }
    }

    private fun showResult(result: AttemptResult, set: ExamSet?) {
        ticker?.cancel()
        savedState[KEY_ATTEMPT] = result.attempt.id
        if (set == null) {
            _state.value = UiState.NotFound
            return
        }
        _state.value = UiState.Success(
            ExamSessionUiState(
                ExamSessionStatus.of(result.attempt), set, result.attempt,
                current()?.currentIndex ?: 0, result.answers.associateBy { it.questionId }, current()?.flagged.orEmpty(),
                remainingMillis = 0, result = result,
            ),
        )
    }

    private suspend fun onTick() {
        val s = current()?.takeIf { it.canAnswer } ?: return
        val attempt = s.attempt ?: return
        val remaining = ExamTimer.remainingMillis(attempt, clock.millis())
        update { it.copy(remainingMillis = remaining) }
        if (ExamTimer.isExpired(attempt, clock.millis())) submitNow()
    }

    fun goTo(index: Int) {
        val s = current()?.takeIf { it.canAnswer && index in it.questions.indices && index != it.currentIndex } ?: return
        val attemptId = s.attempt?.id ?: return
        update { it.copy(currentIndex = index) }
        shownAt = clock.millis()
        viewModelScope.launch { writes.withLock { sessions.moveTo(attemptId, s.questions[index].id) } }
    }

    fun next() {
        current()?.let { goTo(it.currentIndex + 1) }
    }

    fun previous() {
        current()?.let { goTo(it.currentIndex - 1) }
    }

    /** Answers (or changes the answer to) the current question. */
    fun answer(input: AnswerInput) {
        val s = current()?.takeIf { it.canAnswer } ?: return
        val attemptId = s.attempt?.id ?: return
        val question = s.currentQuestion ?: return
        viewModelScope.launch {
            writes.withLock {
                if (current()?.canAnswer != true) return@withLock
                val saved = answerManager.answer(attemptId, question, input, shownAt)
                if (saved != null) {
                    shownAt = clock.millis()
                    update { it.copy(answers = it.answers + (question.id to saved)) }
                } else {
                    // Refused: the time ran out (submitted now) or the attempt ended elsewhere.
                    open(attemptId)
                }
            }
        }
    }

    /** Flags the current question to look at again, or unflags it. */
    fun toggleFlag() {
        val s = current()?.takeIf { it.canAnswer } ?: return
        val attemptId = s.attempt?.id ?: return
        val questionId = s.currentQuestion?.id ?: return
        val flag = questionId !in s.flagged
        viewModelScope.launch {
            writes.withLock {
                if (current()?.canAnswer == true && answerManager.setFlag(attemptId, questionId, flag)) {
                    update { it.copy(flagged = if (flag) it.flagged + questionId else it.flagged - questionId) }
                }
            }
        }
    }

    /** Submits the attempt; a second call (or the timer at the same time) changes nothing. */
    fun submit() {
        if (current()?.canAnswer != true) return
        viewModelScope.launch { submitNow() }
    }

    private suspend fun submitNow() {
        writes.withLock {
            val attemptId = current()?.takeIf { it.canAnswer }?.attempt?.id ?: return@withLock
            submitLocked(attemptId)
        }
    }

    private suspend fun submitLocked(attemptId: String) {
        when (val outcome = submission.submit(attemptId)) {
            is SubmitOutcome.Submitted -> showResult(outcome.result, current()?.set)
            is SubmitOutcome.AlreadySubmitted -> showResult(outcome.result, current()?.set)
            SubmitOutcome.NotSubmittable -> open(attemptId)
        }
    }

    private fun current(): ExamSessionUiState? = (_state.value as? UiState.Success)?.data

    private fun update(change: (ExamSessionUiState) -> ExamSessionUiState) {
        _state.update { state -> if (state is UiState.Success) UiState.Success(change(state.data)) else state }
    }

    companion object {
        const val KEY_ATTEMPT = "exam_attempt_id"
    }
}
