package com.n3sensei.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ItemType

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onStartSession: () -> Unit,
    onOpenLessons: () -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val markedCount by viewModel.markedCount.collectAsStateWithLifecycle()
    UiStateContent(state = state, modifier = modifier) { ui ->
        val overview = ui.overview
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            N3Card(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.review_overview_due, overview.due), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(
                        R.string.review_overview_breakdown,
                        overview.dueByType[ItemType.VOCABULARY] ?: 0,
                        overview.dueByType[ItemType.KANJI] ?: 0,
                        overview.dueByType[ItemType.GRAMMAR] ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (overview.overdue > 0) {
                    Text(
                        stringResource(R.string.review_overview_overdue, overview.overdue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.review_overview_new, ui.newToday, overview.newAvailable), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(
                        R.string.review_overview_today,
                        overview.today.reviewsAnswered,
                        ui.limits.reviewsPerDay,
                        overview.today.newAnswered,
                        ui.limits.newPerDay,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onStartSession, enabled = ui.sessionSize > 0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_start, ui.sessionSize))
            }
            when {
                ui.limitReached -> InfoBanner(stringResource(R.string.review_limit_reached))
                ui.sessionSize == 0 -> InfoBanner(stringResource(R.string.review_nothing_due))
            }
            InfoBanner(stringResource(R.string.review_rules))
            OutlinedButton(onClick = onOpenLessons, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_open_practice))
            }
            FilledTonalButton(onClick = onPracticeMarked, enabled = markedCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.practice_marked_count, markedCount))
            }
        }
    }
}
