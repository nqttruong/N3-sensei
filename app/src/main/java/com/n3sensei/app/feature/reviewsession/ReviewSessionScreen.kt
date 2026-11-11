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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                // Said once, on the first card; repeating it on every card only takes room.
                banner = if (ui.index == 0) stringResource(R.string.review_session_saved_note) else null,
                position = ui.index,
                total = ui.cards.size,
                item = card.item,
                isAnswerRevealed = ui.isAnswerRevealed,
                onReveal = viewModel::revealAnswer,
                onRate = viewModel::rate,
                ratingHints = hints,
                ratingEnabled = !ui.isSubmitting,
                subtitle = reasonText(card.reason),
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
                    if (ui.skippedLimit > 0) {
                        Text(
                            stringResource(R.string.review_session_skipped_limit, ui.skippedLimit),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                },
            )
        }
    }
}

private val dueFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")

@Composable
private fun reasonText(reason: CardReason): String = when (reason) {
    CardReason.New -> stringResource(R.string.review_reason_new)
    is CardReason.Due -> {
        val time = reason.dueAt.atZone(ZoneId.systemDefault()).format(dueFormat)
        val base = stringResource(if (reason.overdue) R.string.review_reason_overdue else R.string.review_reason_due, time, reason.step, reason.steps)
        if (reason.lapses > 0) stringResource(R.string.review_reason_with_lapses, base, reason.lapses) else base
    }
    is CardReason.Forgotten -> stringResource(R.string.review_reason_forgotten, reason.lapses)
}
