package com.n3sensei.app.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.FlashcardSession
import com.n3sensei.app.core.ui.content.intervalLabel
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.practice.PracticeSession

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    markedOnly: Boolean,
    onFinished: (PracticeResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hints by viewModel.hints.collectAsStateWithLifecycle()
    UiStateContent(
        state = state,
        emptyMessage = stringResource(if (markedOnly) R.string.practice_marked_empty else R.string.practice_empty),
        notFoundMessage = stringResource(R.string.lesson_not_found),
        onRetry = viewModel::load,
        modifier = modifier,
    ) { ui ->
        val result = ui.result
        if (result != null) {
            LaunchedEffect(result) { onFinished(result) }
        } else {
            PracticeContent(
                session = ui.session,
                hints = hints.mapValues { (_, interval) -> intervalLabel(interval) },
                onReveal = viewModel::revealAnswer,
                onRate = viewModel::rate,
            )
        }
    }
}

@Composable
private fun PracticeContent(
    session: PracticeSession,
    hints: Map<ReviewRating, String>,
    onReveal: () -> Unit,
    onRate: (ReviewRating) -> Unit,
) {
    val item = session.current ?: return
    FlashcardSession(
        banner = stringResource(R.string.practice_not_saved),
        position = session.currentIndex,
        total = session.total,
        item = item,
        isAnswerRevealed = session.isAnswerRevealed,
        onReveal = onReveal,
        onRate = onRate,
        ratingHints = hints,
    )
}
