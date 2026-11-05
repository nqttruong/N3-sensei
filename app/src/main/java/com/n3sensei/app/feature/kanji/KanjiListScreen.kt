package com.n3sensei.app.feature.kanji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.ItemBrowser
import com.n3sensei.app.core.ui.content.KanjiCard
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

@Composable
fun KanjiListScreen(
    viewModel: KanjiListViewModel,
    onOpenKanji: (id: String) -> Unit,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    ItemBrowser(
        viewModel = viewModel,
        searchPlaceholder = stringResource(R.string.kanji_search_placeholder),
        emptyMessage = stringResource(R.string.kanji_empty),
        emptyInLessonMessage = stringResource(R.string.lesson_empty_kanji),
        itemKey = { it.id },
        onPracticeMarked = onPracticeMarked,
        modifier = modifier,
    ) { tracked ->
        KanjiCard(
            tracked.item,
            onClick = { onOpenKanji(tracked.item.id) },
            status = tracked.status,
            isMarked = tracked.isMarked,
            location = locations.of(ItemRef(ItemType.KANJI, tracked.item.id)),
        )
    }
}
