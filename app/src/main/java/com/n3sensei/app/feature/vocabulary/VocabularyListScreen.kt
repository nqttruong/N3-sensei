package com.n3sensei.app.feature.vocabulary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.core.ui.content.ItemBrowser

@Composable
fun VocabularyListScreen(
    viewModel: VocabularyListViewModel,
    onOpenVocabulary: (id: String) -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemBrowser(
        viewModel = viewModel,
        searchPlaceholder = stringResource(R.string.vocabulary_search_placeholder),
        emptyMessage = stringResource(R.string.vocabulary_empty),
        emptyInLessonMessage = stringResource(R.string.lesson_empty_vocabulary),
        itemKey = { it.id },
        onPracticeMarked = onPracticeMarked,
        modifier = modifier,
    ) { tracked ->
        VocabularyCard(tracked.item, onClick = { onOpenVocabulary(tracked.item.id) }, status = tracked.status, isMarked = tracked.isMarked)
    }
}
