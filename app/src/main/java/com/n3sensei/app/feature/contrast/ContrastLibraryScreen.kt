package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.SearchField
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.contrast.ContrastContentType

@Composable
fun ContrastLibraryScreen(
    viewModel: ContrastLibraryViewModel,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    ContrastLibraryContent(state, query, viewModel::setQuery, viewModel::selectType, viewModel::selectStatus, onOpenGroup, viewModel::retry, modifier)
}

@Composable
fun ContrastLibraryContent(
    state: UiState<ContrastLibraryUiState>,
    /** The text in the search field (as typed). */
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectType: (ContrastContentType?) -> Unit,
    onSelectStatus: (LibraryStatus?) -> Unit,
    onOpenGroup: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier.testTag(ContrastTags.LIBRARY), emptyMessage = stringResource(R.string.contrast_empty), onRetry = onRetry) { data ->
        Column(Modifier.fillMaxSize().testTag(ContrastTags.LIBRARY)) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.contrast_search_placeholder),
                modifier = Modifier.padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s).testTag(ContrastTags.SEARCH),
            )
            val all = stringResource(R.string.common_all)
            val typeDescription = stringResource(R.string.contrast_filter_type)
            FilterChipRow(
                options = listOf(FilterOption<ContrastContentType?>(null, all)) + ContrastContentType.entries.map { FilterOption(it, typeLabel(it)) },
                selected = data.type,
                onSelect = onSelectType,
                modifier = Modifier.testTag(ContrastTags.TYPE_FILTER).semantics { contentDescription = typeDescription },
            )
            val statusDescription = stringResource(R.string.contrast_filter_status)
            FilterChipRow(
                options = listOf(FilterOption<LibraryStatus?>(null, all)) + LibraryStatus.entries.map { FilterOption(it, statusLabel(it)) },
                selected = data.status,
                onSelect = onSelectStatus,
                modifier = Modifier.padding(vertical = N3Spacing.s).testTag(ContrastTags.STATUS_FILTER).semantics { contentDescription = statusDescription },
            )
            if (data.rows.isEmpty()) {
                MessageContent(stringResource(R.string.contrast_library_no_match))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = N3Spacing.screen, end = N3Spacing.screen, bottom = N3Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
                ) {
                    items(data.rows, key = { it.group.id }) { row -> ContrastGroupCard(row, onClick = { onOpenGroup(row.group.id) }) }
                }
            }
        }
    }
}
