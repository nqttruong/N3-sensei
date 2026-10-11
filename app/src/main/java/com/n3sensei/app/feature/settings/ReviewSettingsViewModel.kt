package com.n3sensei.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable ladder step; [amount] is raw text so the field can be cleared while typing. */
data class StepDraft(val amount: String, val unit: IntervalUnit)

sealed interface SettingsProblem {
    data object Empty : SettingsProblem
    data object TooManySteps : SettingsProblem
    data class InvalidAmount(val step: Int) : SettingsProblem
    data class NotIncreasing(val step: Int) : SettingsProblem
    data class TooLong(val step: Int) : SettingsProblem
    data object InvalidLimits : SettingsProblem
}

data class ReviewSettingsDraft(
    val steps: List<StepDraft>,
    val newPerDay: String,
    val reviewsPerDay: String,
    val problems: List<SettingsProblem> = emptyList(),
    val saved: Boolean = false,
)

/** Edits the review ladder and daily limits; saves only a valid draft. */
class ReviewSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow<ReviewSettingsDraft?>(null)

    /** Null until the stored settings are loaded. */
    val draft: StateFlow<ReviewSettingsDraft?> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            _draft.value = preferencesRepository.preferences.first().reviewSettings.toDraft()
        }
    }

    fun setAmount(index: Int, amount: String) = edit { d ->
        d.copy(steps = d.steps.mapIndexed { i, s -> if (i == index) s.copy(amount = amount.filter(Char::isDigit).take(3)) else s })
    }

    fun setUnit(index: Int, unit: IntervalUnit) = edit { d ->
        d.copy(steps = d.steps.mapIndexed { i, s -> if (i == index) s.copy(unit = unit) else s })
    }

    fun addStep() = edit { d ->
        val last = d.steps.lastOrNull()
        d.copy(steps = d.steps + StepDraft(last?.amount?.toIntOrNull()?.times(2)?.toString() ?: "1", last?.unit ?: IntervalUnit.DAYS))
    }

    fun removeStep(index: Int) = edit { d -> d.copy(steps = d.steps.filterIndexed { i, _ -> i != index }) }

    fun setNewPerDay(text: String) = edit { it.copy(newPerDay = text.filter(Char::isDigit).take(4)) }

    fun setReviewsPerDay(text: String) = edit { it.copy(reviewsPerDay = text.filter(Char::isDigit).take(4)) }

    fun resetToDefault() = edit { ReviewSettings().toDraft() }

    fun save() {
        val current = _draft.value ?: return
        val (settings, problems) = parse(current)
        if (settings == null) {
            _draft.value = current.copy(problems = problems, saved = false)
            return
        }
        viewModelScope.launch {
            preferencesRepository.setReviewSettings(settings)
            _draft.update { it?.copy(problems = emptyList(), saved = true) }
        }
    }

    private fun edit(transform: (ReviewSettingsDraft) -> ReviewSettingsDraft) {
        _draft.update { it?.let { d -> transform(d).copy(problems = emptyList(), saved = false) } }
    }

    companion object {
        /** Validates a draft; returns settings when it is valid, otherwise the problems found. */
        fun parse(draft: ReviewSettingsDraft): Pair<ReviewSettings?, List<SettingsProblem>> {
            val problems = mutableListOf<SettingsProblem>()
            val intervals = draft.steps.mapIndexedNotNull { i, step ->
                val amount = step.amount.toIntOrNull()?.takeIf { it in 1..MAX_AMOUNT }
                if (amount == null) {
                    problems += SettingsProblem.InvalidAmount(i)
                    null
                } else {
                    ReviewInterval(amount, step.unit)
                }
            }
            if (problems.isEmpty()) {
                ReviewLadder.validate(intervals).forEach { v ->
                    problems += when (v.problem) {
                        ReviewLadder.Problem.EMPTY -> SettingsProblem.Empty
                        ReviewLadder.Problem.TOO_MANY_STEPS -> SettingsProblem.TooManySteps
                        ReviewLadder.Problem.NOT_INCREASING -> SettingsProblem.NotIncreasing(v.index!!)
                        ReviewLadder.Problem.TOO_LONG -> SettingsProblem.TooLong(v.index!!)
                    }
                }
            }
            val newPerDay = draft.newPerDay.toIntOrNull()?.takeIf { it in 0..DailyLimits.MAX_NEW }
            val reviewsPerDay = draft.reviewsPerDay.toIntOrNull()?.takeIf { it in 1..DailyLimits.MAX_REVIEWS }
            if (newPerDay == null || reviewsPerDay == null) problems += SettingsProblem.InvalidLimits
            if (problems.isNotEmpty()) return null to problems
            return ReviewSettings(ReviewLadder.of(intervals), DailyLimits(newPerDay!!, reviewsPerDay!!)) to emptyList()
        }

        const val MAX_AMOUNT = 999

        private fun ReviewSettings.toDraft() = ReviewSettingsDraft(
            steps = ladder.intervals.map { StepDraft(it.amount.toString(), it.unit) },
            newPerDay = limits.newPerDay.toString(),
            reviewsPerDay = limits.reviewsPerDay.toString(),
        )
    }
}
