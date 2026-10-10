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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.ui.state.UiStateContent

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onOpenLessons: () -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val markedCount by viewModel.markedCount.collectAsStateWithLifecycle()
    UiStateContent(state = dueCount, modifier = modifier) { count ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.review_due_count, count), style = MaterialTheme.typography.headlineSmall)
            InfoBanner(stringResource(R.string.review_scheduler_pending))
            Button(onClick = onOpenLessons, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_open_practice))
            }
            FilledTonalButton(onClick = onPracticeMarked, enabled = markedCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.practice_marked_count, markedCount))
            }
        }
    }
}
