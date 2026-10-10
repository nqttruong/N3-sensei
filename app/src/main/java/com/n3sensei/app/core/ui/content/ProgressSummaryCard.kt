package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.model.ProgressSummary

/** Learner progress counts, all computed from stored progress and review history. */
@Composable
fun ProgressSummaryCard(
    summary: ProgressSummary,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(summary.dueNow.toString(), stringResource(R.string.progress_due_now), Modifier.weight(1f))
                StatItem(summary.learning.toString(), stringResource(R.string.progress_learning), Modifier.weight(1f))
                StatItem(summary.inReview.toString(), stringResource(R.string.progress_in_review), Modifier.weight(1f))
                StatItem(summary.totalReviews.toString(), stringResource(R.string.progress_total_reviews), Modifier.weight(1f))
            }
        }
    }
}

/** Big number with a label underneath, read as one item by TalkBack. */
@Composable
fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressSummaryCardPreview() = N3SenseiTheme {
    ProgressSummaryCard(ProgressSummary(trackedItems = 0, learning = 0, inReview = 0, dueNow = 0, totalReviews = 0))
}
