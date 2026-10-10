package com.n3sensei.app.feature.grammar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.GrammarCard
import com.n3sensei.app.core.ui.content.SearchableListLayout

@Composable
fun GrammarListScreen(
    viewModel: GrammarListViewModel,
    onOpenGrammar: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val lessonId by viewModel.lessonId.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    SearchableListLayout(
        query = query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(R.string.grammar_search_placeholder),
        lessons = lessons,
        selectedLessonId = lessonId,
        onLessonSelected = viewModel::onLessonSelected,
        state = items,
        emptyMessage = stringResource(R.string.grammar_empty),
        emptyInLessonMessage = stringResource(R.string.lesson_empty_grammar),
        itemKey = { it.id },
        modifier = modifier,
    ) { grammar ->
        GrammarCard(grammar, onClick = { onOpenGrammar(grammar.id) })
    }
}
