package com.n3sensei.app.feature.vocabulary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.ItemBrowser
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

@Composable
fun VocabularyListScreen(
    viewModel: VocabularyListViewModel,
    onOpenVocabulary: (id: String) -> Unit,
    onPracticeMarked: () -> Unit,
    onAddWord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize()) {
        ItemBrowser(
            viewModel = viewModel,
            searchPlaceholder = stringResource(R.string.vocabulary_search_placeholder),
            emptyMessage = stringResource(if (viewModel.myWords) R.string.my_words_empty else R.string.vocabulary_empty),
            emptyInLessonMessage = stringResource(R.string.lesson_empty_vocabulary),
            itemKey = { it.id },
            onPracticeMarked = onPracticeMarked,
        ) { tracked ->
            VocabularyCard(
                tracked.item,
                onClick = { onOpenVocabulary(tracked.item.id) },
                status = tracked.status,
                isMarked = tracked.isMarked,
                isSaved = tracked.isSaved,
                // In "Từ của tôi" every word is the learner's: the tag would say only that.
                location = if (viewModel.myWords) null else locations.of(ItemRef(ItemType.VOCABULARY, tracked.item.id)),
            )
        }
        // Content overload: the text/icon overload leaves the button without a label for TalkBack.
        ExtendedFloatingActionButton(onClick = onAddWord, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_word_action))
        }
    }
}
