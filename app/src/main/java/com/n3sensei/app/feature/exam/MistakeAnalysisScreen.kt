package com.n3sensei.app.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.mistakes.AttemptMistakes

@Composable
fun MistakeAnalysisScreen(
    viewModel: MistakeAnalysisViewModel,
    actions: MistakeActions,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, onRetry = viewModel::load) { data ->
        MistakeAnalysisContent(data, actions)
    }
}

@Composable
fun MistakeAnalysisContent(data: AttemptMistakes, actions: MistakeActions, modifier: Modifier = Modifier) {
    if (data.analyses.isEmpty()) {
        MessageContent(stringResource(R.string.mistakes_analysis_none), modifier)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
    ) {
        item { Caption(stringResource(R.string.mistakes_analysis_intro)) }
        items(data.analyses, key = { it.question.id }) { MistakeAnalysisCard(it, actions) }
    }
}
