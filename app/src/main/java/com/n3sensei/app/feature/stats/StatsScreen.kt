package com.n3sensei.app.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
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
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.ui.content.ContentTotalsRow
import com.n3sensei.app.core.ui.content.ProgressSummaryCard
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ReviewRating

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state = state, modifier = modifier) { ui ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.stats_content))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                ContentTotalsRow(ui.totals, Modifier.padding(16.dp))
            }

            ProgressSummaryCard(ui.progress)

            SectionHeader(stringResource(R.string.stats_ratings))
            if (ui.ratingCounts.isEmpty()) {
                Text(stringResource(R.string.stats_no_reviews), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        listOf(
                            ReviewRating.AGAIN to R.string.rating_again,
                            ReviewRating.HARD to R.string.rating_hard,
                            ReviewRating.GOOD to R.string.rating_good,
                            ReviewRating.EASY to R.string.rating_easy,
                        ).forEach { (rating, label) ->
                            StatItem((ui.ratingCounts[rating] ?: 0).toString(), stringResource(label), Modifier.weight(1f))
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.stats_forecast))
            InfoBanner(stringResource(R.string.stats_forecast_pending))
        }
    }
}
