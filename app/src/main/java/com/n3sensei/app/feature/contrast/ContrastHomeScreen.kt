package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent

@Composable
fun ContrastHomeScreen(
    viewModel: ContrastHomeViewModel,
    onOpenGroup: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartPractice: () -> Unit,
    onResumePractice: () -> Unit,
    onOpenErrors: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastHomeContent(state, onOpenGroup, onOpenLibrary, viewModel::retry, modifier, onStartPractice, onResumePractice, InsightLinks(onOpenErrors, onOpenReview, onOpenProgress))
}

/** M7 entries from Home. */
data class InsightLinks(val errors: () -> Unit = {}, val review: () -> Unit = {}, val progress: () -> Unit = {})

@Composable
fun ContrastHomeContent(
    state: UiState<ContrastHomeUiState>,
    onOpenGroup: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onStartPractice: () -> Unit = {},
    onResumePractice: () -> Unit = {},
    insights: InsightLinks = InsightLinks(),
) {
    Column(modifier.fillMaxSize().testTag(ContrastTags.HOME)) {
        Text(
            stringResource(R.string.contrast_home_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s),
        )
        UiStateContent(state, emptyMessage = stringResource(R.string.contrast_empty), onRetry = onRetry) { data ->
            HomeSections(data, onOpenGroup, onOpenLibrary, onStartPractice, onResumePractice, insights)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeSections(
    data: ContrastHomeUiState,
    onOpenGroup: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartPractice: () -> Unit,
    onResumePractice: () -> Unit,
    insights: InsightLinks,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            Figure(R.string.contrast_overview_available, data.available)
            Figure(R.string.contrast_overview_learning, data.learning)
            Figure(R.string.contrast_overview_mastered, data.mastered)
            Figure(R.string.contrast_overview_due, data.due)
        }
        data.activeRemaining?.let { remaining ->
            Button(onClick = onResumePractice, modifier = Modifier.fillMaxWidth().testTag(ContrastTags.RESUME)) {
                Text(stringResource(R.string.contrast_continue_session, remaining))
            }
        }
        OutlinedButton(onClick = onStartPractice, modifier = Modifier.fillMaxWidth().testTag(ContrastTags.START)) {
            Text(stringResource(R.string.contrast_start_practice))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            OutlinedButton(onClick = insights.review, modifier = Modifier.testTag(InsightTags.OPEN_REVIEW)) { Text(stringResource(R.string.contrast_open_review)) }
            OutlinedButton(onClick = insights.errors, modifier = Modifier.testTag(InsightTags.OPEN_ERRORS)) { Text(stringResource(R.string.contrast_open_errors)) }
            OutlinedButton(onClick = insights.progress, modifier = Modifier.testTag(InsightTags.OPEN_PROGRESS)) { Text(stringResource(R.string.contrast_open_progress)) }
        }
        data.continueGroup?.let { row ->
            SectionHeader(stringResource(R.string.contrast_continue_title))
            ContrastGroupCard(row, onClick = { onOpenGroup(row.group.id) })
        }
        if (data.dueGroups.isNotEmpty()) {
            SectionHeader(stringResource(R.string.contrast_due_title))
            Text(stringResource(R.string.contrast_due_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            data.dueGroups.forEach { row -> ContrastGroupCard(row, onClick = { onOpenGroup(row.group.id) }) }
        }
        SectionHeader(stringResource(R.string.contrast_confused_title))
        if (data.confusions.isEmpty()) {
            Text(stringResource(R.string.contrast_confused_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            data.confusions.forEach { c ->
                N3Card(onClick = { onOpenGroup(c.groupId) }) {
                    Text(c.groupTitle, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.contrast_confused_row, c.expectedLabel, c.chosenLabel, c.count), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth().testTag(ContrastTags.EXPLORE)) {
            Text(stringResource(R.string.contrast_explore))
        }
    }
}

@Composable
private fun Figure(labelRes: Int, value: Int) {
    val label = stringResource(labelRes)
    N3Card(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
