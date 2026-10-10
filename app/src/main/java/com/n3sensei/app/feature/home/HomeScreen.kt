package com.n3sensei.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.ui.content.ContentTotalsRow
import com.n3sensei.app.core.ui.content.ProgressSummaryCard
import com.n3sensei.app.core.ui.state.UiStateContent

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenLessons: () -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state = state, modifier = modifier) { ui ->
        HomeContent(ui, onOpenLessons, onOpenReview)
    }
}

@Composable
private fun HomeContent(ui: HomeUiState, onOpenLessons: () -> Unit, onOpenReview: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (ui.totals.isEmpty) InfoBanner(stringResource(R.string.common_no_content))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_due_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (ui.progress.dueNow > 0) {
                        stringResource(R.string.home_due_count, ui.progress.dueNow)
                    } else {
                        stringResource(R.string.home_due_none)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                FilledTonalButton(onClick = onOpenReview) { Text(stringResource(R.string.home_open_review)) }
            }
        }

        ProgressSummaryCard(ui.progress)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_content_title), style = MaterialTheme.typography.titleMedium)
                ContentTotalsRow(ui.totals)
                FilledTonalButton(onClick = onOpenLessons) { Text(stringResource(R.string.home_open_lessons)) }
            }
        }
    }
}
