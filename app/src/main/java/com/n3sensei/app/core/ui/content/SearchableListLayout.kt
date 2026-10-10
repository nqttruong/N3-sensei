package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.SearchField
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.repository.ContentRepository

/** Search box + lesson filter chips + item list, with empty / no-result states. */
@Composable
fun <T> SearchableListLayout(
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    lessons: List<LessonSummary>,
    selectedLessonId: String?,
    onLessonSelected: (String?) -> Unit,
    state: UiState<List<T>>,
    emptyMessage: String,
    /** Shown when a lesson is selected, the query is blank and that lesson has no items. */
    emptyInLessonMessage: String,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = searchPlaceholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (lessons.isNotEmpty()) {
            val allLabel = stringResource(R.string.common_all)
            val options = listOf(FilterOption<String?>(null, allLabel)) +
                lessons.map { FilterOption<String?>(it.id, stringResource(R.string.lesson_number, it.number, it.jlptLevel)) }
            FilterChipRow(options = options, selected = selectedLessonId, onSelect = onLessonSelected)
        }
        val searching = query.isNotBlank()
        UiStateContent(
            state = state,
            emptyMessage = when {
                searching -> stringResource(R.string.common_no_results, query.trim())
                selectedLessonId != null -> emptyInLessonMessage
                else -> emptyMessage
            },
        ) { items ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searching && items.size >= ContentRepository.MAX_PAGE_SIZE) {
                    item {
                        Text(
                            stringResource(R.string.common_results_capped, ContentRepository.MAX_PAGE_SIZE),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items, key = itemKey) { itemContent(it) }
            }
        }
    }
}
