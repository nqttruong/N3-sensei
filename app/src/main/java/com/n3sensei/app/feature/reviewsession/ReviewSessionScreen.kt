package com.n3sensei.app.feature.reviewsession

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.FlashcardSession
import com.n3sensei.app.core.ui.content.intervalLabel
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.practice.PracticeResult

@Composable
fun ReviewSessionScreen(
    viewModel: ReviewSessionViewModel,
    onFinished: (PracticeResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(
        state = state,
        emptyMessage = stringResource(R.string.review_session_empty),
        onRetry = viewModel::load,
        modifier = modifier,
    ) { ui ->
        val result = ui.result
        val card = ui.current
        if (result != null) {
            LaunchedEffect(result) { onFinished(result) }
        } else if (card != null) {
            val hints = card.hints.mapValues { (_, interval) -> intervalLabel(interval) }
            FlashcardSession(
                banner = stringResource(R.string.review_session_saved_note),
                position = ui.index,
                total = ui.cards.size,
                item = card.item,
                isAnswerRevealed = ui.isAnswerRevealed,
                onReveal = viewModel::revealAnswer,
                onRate = viewModel::rate,
                ratingHints = hints,
                ratingEnabled = !ui.isSubmitting,
                footer = {
                    if (ui.saveFailed) {
                        Text(
                            stringResource(R.string.review_session_save_failed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else if (ui.skippedMissing > 0 && ui.index == 0) {
                        Text(stringResource(R.string.review_session_skipped), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
        }
    }
}
