package com.n3sensei.app.feature.grammar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.GrammarCard
import com.n3sensei.app.core.ui.content.ItemBrowser

@Composable
fun GrammarListScreen(
    viewModel: GrammarListViewModel,
    onOpenGrammar: (id: String) -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemBrowser(
        viewModel = viewModel,
        searchPlaceholder = stringResource(R.string.grammar_search_placeholder),
        emptyMessage = stringResource(R.string.grammar_empty),
        emptyInLessonMessage = stringResource(R.string.lesson_empty_grammar),
        itemKey = { it.id },
        onPracticeMarked = onPracticeMarked,
        modifier = modifier,
    ) { tracked ->
        GrammarCard(tracked.item, onClick = { onOpenGrammar(tracked.item.id) }, status = tracked.status, isMarked = tracked.isMarked)
    }
}
