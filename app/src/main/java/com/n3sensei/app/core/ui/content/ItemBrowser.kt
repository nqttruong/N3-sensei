package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.SearchField
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.ui.state.ItemBrowserViewModel
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.repository.ContentRepository

object ItemBrowserTags {
    fun level(level: String) = "browse_level_$level"
}

/**
 * Search box, filter chips (lesson, status + marked, topic) and the live item list, with
 * empty / no-result states. Shared by the vocabulary, kanji and grammar lists.
 *
 * @param onPracticeMarked shown as a button while the "marked" filter is on and has items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ItemBrowser(
    viewModel: ItemBrowserViewModel<T>,
    searchPlaceholder: String,
    emptyMessage: String,
    emptyInLessonMessage: String,
    itemKey: (T) -> Any,
    onPracticeMarked: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (TrackedItem<T>) -> Unit,
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val state by viewModel.items.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // Several JLPT levels (Học → Kanji: N3 / N2): one at a time, never mixed in one list.
        if (levels.size > 1 && level != null) {
            PrimaryTabRow(selectedTabIndex = levels.indexOf(level).coerceAtLeast(0)) {
                levels.forEach { option ->
                    Tab(
                        selected = option == level,
                        onClick = { viewModel.onLevelSelected(option) },
                        text = { Text(stringResource(R.string.browse_level_tab, option)) },
                        modifier = Modifier.testTag(ItemBrowserTags.level(option)),
                    )
                }
            }
        }
        SearchField(
            query = filters.text,
            onQueryChange = viewModel::onQueryChange,
            placeholder = searchPlaceholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (lessons.isNotEmpty()) {
            // "Bài 1 · N3" is ambiguous once several packs are installed: use the lesson title then.
            val severalPacks = lessons.map { it.packId }.distinct().size > 1
            val options = listOf(FilterOption<String?>(null, stringResource(R.string.common_all))) +
                lessons.map {
                    FilterOption<String?>(it.id, if (severalPacks) it.title else stringResource(R.string.lesson_number, it.number, it.jlptLevel))
                }
            FilterChipRow(options = options, selected = filters.lessonId, onSelect = viewModel::onLessonSelected)
        }
        StatusFilterRow(
            status = filters.status,
            markedOnly = filters.markedOnly,
            onStatusSelected = viewModel::onStatusSelected,
            onMarkedOnlyChange = viewModel::onMarkedOnlyChange,
        )
        if (tags.isNotEmpty()) {
            val options = listOf(FilterOption<String?>(null, stringResource(R.string.filter_all_topics))) +
                tags.map { FilterOption<String?>(it.id, it.name) }
            FilterChipRow(options = options, selected = filters.tagId, onSelect = viewModel::onTagSelected)
        }

        val searching = filters.text.isNotBlank()
        UiStateContent(
            state = state,
            emptyMessage = when {
                searching -> stringResource(R.string.common_no_results, filters.text.trim())
                filters.status != null || filters.markedOnly || filters.tagId != null -> stringResource(R.string.list_empty_filtered)
                filters.lessonId != null -> emptyInLessonMessage
                else -> emptyMessage
            },
        ) { items ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filters.markedOnly && state is UiState.Success) {
                    item(key = "practice-marked") {
                        FilledTonalButton(onClick = onPracticeMarked, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.practice_marked))
                        }
                    }
                }
                if (searching) {
                    item(key = "result-count") {
                        // Announced by TalkBack as the results change, without moving focus.
                        Text(
                            if (items.size >= ContentRepository.MAX_PAGE_SIZE) {
                                stringResource(R.string.common_results_capped, ContentRepository.MAX_PAGE_SIZE)
                            } else {
                                stringResource(R.string.common_results_count, items.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                items(items, key = { itemKey(it.item) }) { itemContent(it) }
            }
        }
    }
}

@Composable
private fun StatusFilterRow(
    status: LearningStatus?,
    markedOnly: Boolean,
    onStatusSelected: (LearningStatus?) -> Unit,
    onMarkedOnlyChange: (Boolean) -> Unit,
) {
    val statuses = listOf(null) + LearningStatus.entries
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "marked") {
            FilterChip(
                selected = markedOnly,
                onClick = { onMarkedOnlyChange(!markedOnly) },
                label = { Text(stringResource(R.string.filter_marked)) },
                leadingIcon = { Icon(if (markedOnly) N3Icons.Marked else N3Icons.Unmarked, contentDescription = null) },
            )
        }
        items(statuses, key = { it?.name ?: "any" }) { option ->
            FilterChip(
                selected = status == option,
                onClick = { onStatusSelected(option) },
                label = { Text(stringResource(option?.labelRes() ?: R.string.status_any)) },
            )
        }
    }
}
