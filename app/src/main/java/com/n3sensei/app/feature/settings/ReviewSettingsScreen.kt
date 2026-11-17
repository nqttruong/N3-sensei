package com.n3sensei.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewLadder

@Composable
fun ReviewSettingsScreen(
    viewModel: ReviewSettingsViewModel,
    modifier: Modifier = Modifier,
    onChooseKanjiLessons: () -> Unit = {},
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val current = draft ?: return LoadingContent(modifier)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(R.string.review_settings_intervals))
        current.steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.review_settings_step, index + 1), modifier = Modifier.width(56.dp))
                OutlinedTextField(
                    value = step.amount,
                    onValueChange = { viewModel.setAmount(index, it) },
                    label = { Text(stringResource(R.string.review_settings_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
                UnitPicker(step.unit, onSelect = { viewModel.setUnit(index, it) }, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.removeStep(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.review_settings_remove_step, index + 1))
                }
            }
        }
        if (current.steps.size < ReviewLadder.MAX_STEPS) {
            TextButton(onClick = viewModel::addStep) { Text(stringResource(R.string.review_settings_add_step)) }
        }

        // One independent cap per type: no total, no sharing between them.
        SectionHeader(stringResource(R.string.review_settings_new_items))
        Text(
            stringResource(R.string.review_settings_new_items_description, DailyLimits.MAX_NEW_PER_TYPE),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ItemType.entries.forEach { type ->
            OutlinedTextField(
                value = current.newPerDay[type].orEmpty(),
                onValueChange = { viewModel.setNewPerDay(type, it) },
                label = { Text(stringResource(R.string.review_settings_new_per_day_of, typeName(type))) },
                supportingText = { Text(stringResource(R.string.review_settings_new_per_day_range, DailyLimits.MAX_NEW_PER_TYPE)) },
                isError = current.problems.any { it is SettingsProblem.InvalidNewPerDay && it.type == type },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReviewSettingsTags.newPerDay(type)),
            )
        }
        TextButton(onClick = viewModel::resetNewPerDay, modifier = Modifier.testTag(ReviewSettingsTags.RESET_NEW)) {
            Text(stringResource(R.string.review_settings_new_items_reset, DailyLimits.DEFAULT_NEW_VOCABULARY, DailyLimits.DEFAULT_NEW_KANJI, DailyLimits.DEFAULT_NEW_GRAMMAR))
        }

        SectionHeader(stringResource(R.string.review_settings_kanji_lessons))
        Text(
            stringResource(R.string.review_settings_kanji_lessons_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onChooseKanjiLessons, modifier = Modifier.testTag(ReviewSettingsTags.KANJI_LESSONS)) {
            Text(stringResource(R.string.review_settings_kanji_lessons_choose))
        }

        SectionHeader(stringResource(R.string.review_settings_daily_goal))
        Text(
            stringResource(R.string.review_settings_daily_goal_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = current.reviewsPerDay,
            onValueChange = viewModel::setReviewsPerDay,
            label = { Text(stringResource(R.string.review_settings_reviews_per_day)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            current.problems.forEach {
                Text(problemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (current.saved) {
                Text(stringResource(R.string.review_settings_saved), color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::save) { Text(stringResource(R.string.review_settings_save)) }
            OutlinedButton(onClick = viewModel::resetToDefault) { Text(stringResource(R.string.review_settings_reset)) }
        }

        InfoBanner(stringResource(R.string.review_settings_rule_time))
        InfoBanner(stringResource(R.string.review_settings_rule_change))
        InfoBanner(stringResource(R.string.review_settings_rule_day))
        InfoBanner(stringResource(R.string.review_rules))
    }
}

@Composable
private fun UnitPicker(unit: IntervalUnit, onSelect: (IntervalUnit) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(unitName(unit))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IntervalUnit.entries.forEach { option ->
                DropdownMenuItem(text = { Text(unitName(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun unitName(unit: IntervalUnit): String = stringResource(
    when (unit) {
        IntervalUnit.MINUTES -> R.string.unit_minutes
        IntervalUnit.HOURS -> R.string.unit_hours
        IntervalUnit.DAYS -> R.string.unit_days
        IntervalUnit.WEEKS -> R.string.unit_weeks
        IntervalUnit.MONTHS -> R.string.unit_months
    },
)

@Composable
private fun problemText(problem: SettingsProblem): String = when (problem) {
    SettingsProblem.Empty -> stringResource(R.string.review_settings_error_empty)
    SettingsProblem.TooManySteps -> stringResource(R.string.review_settings_error_too_many, ReviewLadder.MAX_STEPS)
    is SettingsProblem.InvalidAmount -> stringResource(R.string.review_settings_error_amount, problem.step + 1)
    is SettingsProblem.NotIncreasing -> stringResource(R.string.review_settings_error_order, problem.step + 1)
    is SettingsProblem.TooLong -> stringResource(R.string.review_settings_error_too_long, problem.step + 1)
    SettingsProblem.InvalidLimits -> stringResource(R.string.review_settings_error_limits)
    is SettingsProblem.InvalidNewPerDay -> stringResource(R.string.review_settings_error_new_per_day, typeName(problem.type), DailyLimits.MAX_NEW_PER_TYPE)
}

@Composable
private fun typeName(type: ItemType): String = stringResource(
    when (type) {
        ItemType.VOCABULARY -> R.string.totals_vocabulary
        ItemType.KANJI -> R.string.totals_kanji
        ItemType.GRAMMAR -> R.string.totals_grammar
    },
)

object ReviewSettingsTags {
    const val RESET_NEW = "review_settings_reset_new"
    const val KANJI_LESSONS = "review_settings_kanji_lessons"
    fun newPerDay(type: ItemType) = "review_settings_new_${type.name}"
}
