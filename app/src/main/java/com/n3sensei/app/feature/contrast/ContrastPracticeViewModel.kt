package com.n3sensei.app.feature.contrast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastConfig
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.contrast.SubmitOutcome
import com.n3sensei.app.domain.contrast.SubmitRejection
import com.n3sensei.app.domain.contrast.SubmitResult
import com.n3sensei.app.domain.repository.ContrastContentRepository
import com.n3sensei.app.domain.repository.ContrastPracticeRepository
import com.n3sensei.app.domain.repository.ContrastProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The question being worked on, with what the screen needs around it. */
data class PracticeQuestion(
    val sessionId: String,
    val position: Int,
    val question: ContrastQuestion,
    val groupTitle: String,
    /** Item id → label, for "confused A with B". */
    val itemLabels: Map<String, String>,
    /** Positions done (answered, skipped or unavailable) and positions in the session. */
    val done: Int,
    val total: Int,
)

/** What the learner sees after an answer is stored (from the stored result, never guessed). */
data class PracticeFeedback(
    val outcome: AnswerOutcome,
    val chosenOptionId: String?,
    val correctOptionId: String,
    val explanationVi: String,
    /** The editor's note on the chosen option, when it was wrong. */
    val chosenFeedbackVi: String?,
    /** Expected item → chosen item, for a wrong answer that picked another item of the group. */
    val confusion: Pair<String, String>?,
    /** A different question of the group will be asked again at the end. */
    val retryAdded: Boolean,
    /** When the group's block completed and was scheduled. */
    val nextDueAt: Instant?,
    /** Nothing left in the session: the next step is the summary. */
    val sessionCompleted: Boolean,
)

sealed interface PracticeUiState {
    data object Loading : PracticeUiState

    /** No reviewed content, nothing due and no new group allowed today, or the group has no question. */
    data object NothingToPractice : PracticeUiState

    /** Another session is unfinished: continue it, or start this one (the other becomes ABANDONED, its answers kept). */
    data class ConfirmReplace(val activeTitles: List<String>, val remaining: Int) : PracticeUiState

    /** A question to answer. [saveFailed]: the last submit could not be stored (retry uses the same request id). */
    data class Ready(val item: PracticeQuestion, val selectedOptionId: String?, val saveFailed: Boolean = false, val contentChanged: Boolean = false) : PracticeUiState

    /** The answer is being stored; nothing can be submitted again meanwhile. */
    data class Submitting(val item: PracticeQuestion, val selectedOptionId: String?) : PracticeUiState

    data class Feedback(val item: PracticeQuestion, val feedback: PracticeFeedback) : PracticeUiState

    /** The question left the pack after the session was planned: not answered, never counted as wrong. */
    data class QuestionUnavailable(val sessionId: String, val position: Int) : PracticeUiState

    data class Completed(val sessionId: String) : PracticeUiState

    data class Failed(val cause: Throwable) : PracticeUiState
}

/**
 * One practice session (M6), on the M1 use cases only: StartOrResumePractice (create or resume,
 * never two ACTIVE sessions), SubmitContrastAnswer (grading, attempt, review and progress in one
 * transaction). The ViewModel keeps no score of its own.
 *
 * - The selected option is a draft (SavedStateHandle) until submitted: leaving or a process death
 *   writes nothing, and the position stays PENDING.
 * - Each position gets a request id when it is first submitted, kept in the SavedStateHandle until
 *   the result is known: a retry after a failure, a double tap or a process death reuses it, so the
 *   repository stores the answer at most once.
 */
class ContrastPracticeViewModel(
    private val content: ContrastContentRepository,
    private val practice: ContrastPracticeRepository,
    progress: ContrastProgressRepository,
    private val clock: Clock,
    private val saved: SavedStateHandle,
    /** null: due groups then new ones (PracticeScope.Due). */
    private val groupId: String?,
    /** Continue the ACTIVE session whatever its scope. */
    private val resume: Boolean = false,
    private val config: ContrastConfig = ContrastConfig(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** M7 "Ôn tập ngay": due groups only (PracticeScope.Review), when [groupId] is null. */
    private val review: Boolean = false,
) : ViewModel() {

    private val start = StartOrResumePractice(content, practice, progress, config, newId)
    private val submitAnswer = SubmitContrastAnswer(content, practice, progress, config)
    private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()
    private val labels = mutableMapOf<String, Pair<String, Map<String, String>>>()

    private val requestedScope: PracticeScope get() = groupId?.let { PracticeScope.Group(it) } ?: if (review) PracticeScope.Review else PracticeScope.Due

    init {
        launchSafely { open() }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PracticeUiState.Failed(e)
            }
        }
    }

    private suspend fun open() {
        val active = practice.activeSession()
        val resumable = active != null && Duration.between(active.lastActivityAt, clock.instant()) <= config.resumeWindow && remaining(active) > 0
        when {
            resume && active != null -> begin(active.scope)
            !resume && resumable && active!!.scope != requestedScope ->
                _state.value = PracticeUiState.ConfirmReplace(active.items.map { it.groupId }.distinct().map { title(it) }, remaining(active))
            else -> begin(requestedScope)
        }
    }

    /** Continue the unfinished session instead of starting the requested one. */
    fun continueActive() = launchSafely {
        val active = practice.activeSession() ?: return@launchSafely begin(requestedScope)
        begin(active.scope)
    }

    /** Start the requested session; the unfinished one becomes ABANDONED (its answers stay). */
    fun startRequested() = launchSafely { begin(requestedScope) }

    fun retryLoading() = launchSafely { open() }

    private suspend fun begin(scope: PracticeScope) {
        _state.value = PracticeUiState.Loading
        when (val result = start(scope, clock.instant(), clock.zone)) {
            StartPracticeResult.NothingToPractice -> _state.value = PracticeUiState.NothingToPractice
            is StartPracticeResult.Started -> showNext(result.session.id)
            is StartPracticeResult.Resumed -> showNext(result.session.id)
        }
    }

    private suspend fun remaining(session: ContrastSession): Int {
        val answered = practice.attempts(session.id).map { it.position }.toSet()
        return session.items.count { it.position !in answered && content.getQuestion(it.questionId) != null }
    }

    /** The first position with no answer whose question still exists; the summary when none is left. */
    private suspend fun showNext(sessionId: String) {
        val session = practice.session(sessionId) ?: return run { _state.value = PracticeUiState.NothingToPractice }
        val answered = practice.attempts(sessionId).map { it.position }.toSet()
        var unavailable = 0
        for (item in session.items) {
            if (item.position in answered) continue
            val question = content.getQuestion(item.questionId)
            if (question == null) {
                unavailable++
                continue
            }
            val (title, itemLabels) = groupLabels(item.groupId)
            val view = PracticeQuestion(sessionId, item.position, question, title, itemLabels, answered.size + unavailable, session.items.size)
            val draft = saved.get<String>(draftKey(sessionId, item.position))?.takeIf { question.option(it) != null }
            _state.value = PracticeUiState.Ready(view, draft)
            return
        }
        // Only answered or unavailable positions are left: the session is done.
        if (session.status == SessionStatus.ACTIVE) practice.finishSession(sessionId, SessionStatus.COMPLETED, clock.instant())
        _state.value = PracticeUiState.Completed(sessionId)
    }

    private suspend fun groupLabels(groupId: String): Pair<String, Map<String, String>> = labels.getOrPut(groupId) {
        val detail = content.getDetail(groupId)
        (detail?.group?.title ?: groupId) to detail?.items.orEmpty().associate { it.id to it.label }
    }

    private suspend fun title(groupId: String) = groupLabels(groupId).first

    fun select(optionId: String) {
        val ready = _state.value as? PracticeUiState.Ready ?: return
        if (ready.item.question.option(optionId) == null) return
        saved[draftKey(ready.item.sessionId, ready.item.position)] = optionId
        _state.value = ready.copy(selectedOptionId = optionId, saveFailed = false, contentChanged = false)
    }

    fun submit() {
        val ready = _state.value as? PracticeUiState.Ready ?: return
        val option = ready.selectedOptionId ?: return
        send(ready, AnswerInput.Chosen(option))
    }

    /** "Bỏ qua": stored as SKIPPED (never INCORRECT); leaving the screen is not a skip. */
    fun skip() {
        val ready = _state.value as? PracticeUiState.Ready ?: return
        send(ready, AnswerInput.Skipped)
    }

    private fun send(ready: PracticeUiState.Ready, input: AnswerInput) {
        val item = ready.item
        _state.value = PracticeUiState.Submitting(item, ready.selectedOptionId)
        val key = requestKey(item.sessionId, item.position)
        val requestId = saved.get<String>(key) ?: newId().also { saved[key] = it }
        viewModelScope.launch {
            val outcome = try {
                submitAnswer(item.sessionId, item.position, input, requestId, clock.instant(), clock.zone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing was stored (the transaction rolled back): same question, same request id.
                _state.value = ready.copy(saveFailed = true)
                return@launch
            }
            try {
                handle(ready, outcome, requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PracticeUiState.Failed(e)
            }
        }
    }

    private suspend fun handle(ready: PracticeUiState.Ready, outcome: SubmitOutcome, requestId: String) {
        val item = ready.item
        when (outcome) {
            is SubmitOutcome.Recorded -> {
                clearKeys(item)
                // Built from the stored attempt (graded by the domain), plus what the submit returned.
                val attempt = practice.attempts(item.sessionId).first { it.requestId == requestId }
                _state.value = PracticeUiState.Feedback(item, feedbackOf(item.question, attempt, outcome.result.sessionCompleted, outcome.result))
            }
            // Stored before (e.g. the app died after saving): show what was stored.
            SubmitOutcome.Duplicate -> showStored(item) { it.requestId == requestId }
            is SubmitOutcome.Rejected -> when (outcome.reason) {
                SubmitRejection.ALREADY_ANSWERED -> showStored(item) { it.position == item.position }
                SubmitRejection.QUESTION_UNAVAILABLE -> {
                    clearKeys(item)
                    _state.value = PracticeUiState.QuestionUnavailable(item.sessionId, item.position)
                }
                SubmitRejection.INVALID_OPTION -> {
                    // The pack changed the options since this question was shown: show the current one.
                    saved.remove<String>(draftKey(item.sessionId, item.position))
                    val current = content.getQuestion(item.question.id)
                    _state.value = if (current == null) {
                        PracticeUiState.QuestionUnavailable(item.sessionId, item.position)
                    } else {
                        PracticeUiState.Ready(item.copy(question = current), null, contentChanged = true)
                    }
                }
                SubmitRejection.NO_ACTIVE_SESSION, SubmitRejection.UNKNOWN_POSITION -> {
                    clearKeys(item)
                    open()
                }
            }
        }
    }

    private suspend fun showStored(item: PracticeQuestion, which: (ContrastAttempt) -> Boolean) {
        val attempt = practice.attempts(item.sessionId).firstOrNull(which) ?: return showNext(item.sessionId)
        clearKeys(item)
        val session = practice.session(item.sessionId)
        val answered = practice.attempts(item.sessionId).map { it.position }.toSet()
        val completed = session == null || session.status != SessionStatus.ACTIVE || session.items.all { it.position in answered }
        _state.value = PracticeUiState.Feedback(item, feedbackOf(item.question, attempt, completed))
    }

    private fun feedbackOf(question: ContrastQuestion, attempt: ContrastAttempt, completed: Boolean, result: SubmitResult? = null) = PracticeFeedback(
        outcome = attempt.outcome,
        chosenOptionId = attempt.chosenOptionId,
        correctOptionId = attempt.correctOptionId,
        explanationVi = result?.explanationVi ?: question.explanationVi,
        chosenFeedbackVi = attempt.chosenOptionId?.takeIf { it != attempt.correctOptionId }?.let(question::option)?.feedbackVi,
        confusion = attempt.confusion?.let { it.expectedItemId to it.chosenItemId },
        retryAdded = result?.retryAdded ?: false,
        nextDueAt = result?.review?.dueAtAfter,
        sessionCompleted = completed,
    )

    /** After the feedback: the next question, or the summary. */
    fun next() {
        val sessionId = when (val s = _state.value) {
            is PracticeUiState.Feedback -> s.item.sessionId
            is PracticeUiState.QuestionUnavailable -> s.sessionId
            else -> return
        }
        launchSafely { showNext(sessionId) }
    }

    private fun clearKeys(item: PracticeQuestion) {
        saved.remove<String>(requestKey(item.sessionId, item.position))
        saved.remove<String>(draftKey(item.sessionId, item.position))
    }

    private fun draftKey(sessionId: String, position: Int) = "contrast_draft_${sessionId}_$position"
    private fun requestKey(sessionId: String, position: Int) = "contrast_request_${sessionId}_$position"
}
