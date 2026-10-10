package com.n3sensei.app.feature.vocabulary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.core.ui.content.SearchableListLayout

@Composable
fun VocabularyListScreen(
    viewModel: VocabularyListViewModel,
    onOpenVocabulary: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val lessonId by viewModel.lessonId.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    SearchableListLayout(
        query = query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(R.string.vocabulary_search_placeholder),
        lessons = lessons,
        selectedLessonId = lessonId,
        onLessonSelected = viewModel::onLessonSelected,
        state = items,
        emptyMessage = stringResource(R.string.vocabulary_empty),
        emptyInLessonMessage = stringResource(R.string.lesson_empty_vocabulary),
        itemKey = { it.id },
        modifier = modifier,
    ) { vocabulary ->
        VocabularyCard(vocabulary, onClick = { onOpenVocabulary(vocabulary.id) })
    }
}
