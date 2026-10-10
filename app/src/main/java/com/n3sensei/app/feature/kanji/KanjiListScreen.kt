package com.n3sensei.app.feature.kanji

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.KanjiCard
import com.n3sensei.app.core.ui.content.ItemBrowser

@Composable
fun KanjiListScreen(
    viewModel: KanjiListViewModel,
    onOpenKanji: (id: String) -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemBrowser(
        viewModel = viewModel,
        searchPlaceholder = stringResource(R.string.kanji_search_placeholder),
        emptyMessage = stringResource(R.string.kanji_empty),
        emptyInLessonMessage = stringResource(R.string.lesson_empty_kanji),
        itemKey = { it.id },
        onPracticeMarked = onPracticeMarked,
        modifier = modifier,
    ) { tracked ->
        KanjiCard(tracked.item, onClick = { onOpenKanji(tracked.item.id) }, status = tracked.status, isMarked = tracked.isMarked)
    }
}
