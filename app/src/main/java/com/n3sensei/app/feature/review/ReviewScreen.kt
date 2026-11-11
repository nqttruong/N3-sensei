package com.n3sensei.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.srs.ReviewMode
import com.n3sensei.app.domain.srs.ReviewScope

object ReviewTags {
    const val START = "review_start"
    const val DUE_ONLY = "review_due_only"
    const val FORGOTTEN = "review_forgotten"
    fun typeCard(type: ItemType) = "review_type_${type.name}"
    fun startType(type: ItemType) = "review_start_${type.name}"
}

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    /** Starts a session over the given scope (one type or all, scheduled, due only or forgotten). */
    onStartSession: (ReviewScope) -> Unit,
    onOpenLessons: () -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
    onStartErrorReview: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val markedCount by viewModel.markedCount.collectAsStateWithLifecycle()
    val errorsDue by viewModel.errorsDue.collectAsStateWithLifecycle()
    UiStateContent(state = state, modifier = modifier) { ui ->
        val overview = ui.overview
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (errorsDue > 0) {
                SectionCard(stringResource(R.string.review_error_card_title)) {
                    Text(stringResource(R.string.review_error_card_body, errorsDue), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onStartErrorReview, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.review_error_card_action))
                    }
                }
            }
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
            // All three types mixed, taken in turns.
            Button(
                onClick = { onStartSession(ReviewScope.ALL) },
                enabled = ui.sessionSize > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReviewTags.START),
            ) {
                Text(stringResource(R.string.review_start, ui.sessionSize))
            }
            when {
                ui.limitReached -> InfoBanner(stringResource(R.string.review_limit_reached))
                ui.sessionSize == 0 -> InfoBanner(stringResource(R.string.review_nothing_due))
                else -> Text(stringResource(R.string.review_mixed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.types.forEach { TypeCard(it, onStart = { onStartSession(ReviewScope(type = it.type)) }) }
            SectionCard(stringResource(R.string.review_more_title)) {
                OutlinedButton(
                    onClick = { onStartSession(ReviewScope(mode = ReviewMode.DUE_ONLY)) },
                    enabled = ui.dueOnlySize > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ReviewTags.DUE_ONLY),
                ) {
                    Text(stringResource(R.string.review_due_only, ui.dueOnlySize))
                }
                OutlinedButton(
                    onClick = { onStartSession(ReviewScope(mode = ReviewMode.FORGOTTEN)) },
                    enabled = ui.forgottenSize > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ReviewTags.FORGOTTEN),
                ) {
                    Text(stringResource(R.string.review_forgotten, ui.forgottenSize))
                }
                Text(stringResource(R.string.review_forgotten_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** One content type: its due, overdue and done-today counts, and a session of that type alone. */
@Composable
private fun TypeCard(review: TypeReview, onStart: () -> Unit) {
    val name = stringResource(
        when (review.type) {
            ItemType.VOCABULARY -> R.string.totals_vocabulary
            ItemType.KANJI -> R.string.totals_kanji
            ItemType.GRAMMAR -> R.string.totals_grammar
        },
    )
    N3Card(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(ReviewTags.typeCard(review.type)),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatItem(review.due.toString(), stringResource(R.string.review_type_due), Modifier.weight(1f))
            StatItem(review.overdue.toString(), stringResource(R.string.review_type_overdue), Modifier.weight(1f))
            StatItem(review.doneToday.toString(), stringResource(R.string.review_type_done_today), Modifier.weight(1f))
        }
        Text(stringResource(R.string.review_type_new, review.newAvailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(
            onClick = onStart,
            enabled = review.sessionSize > 0,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReviewTags.startType(review.type)),
        ) {
            Text(stringResource(R.string.review_type_start, name, review.sessionSize))
        }
    }
}
