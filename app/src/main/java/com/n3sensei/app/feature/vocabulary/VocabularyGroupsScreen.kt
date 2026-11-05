package com.n3sensei.app.feature.vocabulary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.course.VocabularyGroup
import com.n3sensei.app.domain.course.VocabularyGroups

/**
 * "Học → Từ vựng": the vocabulary in groups of at most 30 words ("Bài 3.1", "Bài 3.2"…), each
 * with its source lesson's title and the learner's progress. The full searchable list stays one
 * tap away ("Tất cả từ vựng"), unchanged.
 */
@Composable
fun VocabularyGroupsScreen(
    viewModel: VocabularyGroupsViewModel,
    onOpenGroup: (key: String) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        // Outside the list state: reachable while the groups load or when there are none.
        FilledTonalButton(
            onClick = onOpenAll,
            modifier = Modifier.fillMaxWidth().padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s),
        ) { Text(stringResource(R.string.vocabulary_all)) }
        UiStateContent(state = state, emptyMessage = stringResource(R.string.vocabulary_groups_empty)) { groups ->
            LazyColumn(
                contentPadding = PaddingValues(start = N3Spacing.screen, end = N3Spacing.screen, bottom = N3Spacing.l),
                verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
            ) {
                item(key = "intro") { Caption(stringResource(R.string.vocabulary_groups_intro, VocabularyGroups.MAX_WORDS)) }
                items(groups, key = { it.key }) { group -> GroupCard(group, onOpen = { onOpenGroup(group.key) }) }
            }
        }
    }
}



@Composable
private fun GroupCard(group: VocabularyGroup, onOpen: () -> Unit) {
    N3Card(onClick = onOpen, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.vocabulary_group_title, group.label), style = MaterialTheme.typography.titleLarge)
        Text(group.sourceTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(groupLine(group), style = MaterialTheme.typography.labelMedium)
        N3ProgressBar(if (group.words.isEmpty()) 0f else group.started().toFloat() / group.words.size)
    }
}

@Composable
private fun groupLine(group: VocabularyGroup): String {
    val progress = stringResource(R.string.vocabulary_group_progress, group.started(), group.words.size)
    return if (group.partCount == 1) progress else stringResource(R.string.vocabulary_group_part, group.part, group.partCount) + " · " + progress
}

/** One group: its words in order, and practice over just these words. */
@Composable
fun VocabularyGroupScreen(
    viewModel: VocabularyGroupViewModel,
    onOpenVocabulary: (id: String) -> Unit,
    onPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state = state, notFoundMessage = stringResource(R.string.vocabulary_group_not_found), modifier = modifier) { group ->
        LazyColumn(
            contentPadding = PaddingValues(N3Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.s),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    Text(
                        stringResource(R.string.vocabulary_group_title, group.label),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(group.sourceTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(groupLine(group), style = MaterialTheme.typography.labelMedium)
                    N3ProgressBar(if (group.words.isEmpty()) 0f else group.started().toFloat() / group.words.size)
                    Button(onClick = onPractice, enabled = group.words.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = N3Spacing.s)) {
                        Text(stringResource(R.string.vocabulary_group_practice))
                    }
                }
            }
            items(group.words, key = { it.item.id }) { tracked ->
                VocabularyCard(tracked.item, onClick = { onOpenVocabulary(tracked.item.id) }, status = tracked.status, isMarked = tracked.isMarked)
            }
        }
    }
}
