package com.n3sensei.app.feature.contrast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.SearchField
import com.n3sensei.app.core.designsystem.theme.LocalReduceMotion
import com.n3sensei.app.core.designsystem.theme.N3Motion
import com.n3sensei.app.core.designsystem.theme.japanese
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(ContrastTags.SEARCH),
            )
            val all = stringResource(R.string.common_all)
            val typeDescription = stringResource(R.string.contrast_filter_type)
            ToneFilterRow(
                options = listOf<Pair<ContrastContentType?, String>>(null to all) + ContrastContentType.entries.map { it to typeLabel(it) },
                selected = data.type,
                onSelect = onSelectType,
                tone = { it?.let { t -> ContrastTones.category(t) } ?: ContrastTones.practice() },
                glyph = { it?.let(::categoryGlyph) },
                modifier = Modifier.testTag(ContrastTags.TYPE_FILTER).semantics { contentDescription = typeDescription },
            )
            val statusDescription = stringResource(R.string.contrast_filter_status)
            ToneFilterRow(
                options = listOf<Pair<LibraryStatus?, String>>(null to all) + LibraryStatus.entries.map { it to statusLabel(it) },
                selected = data.status,
                onSelect = onSelectStatus,
                tone = { status ->
                    when (status) {
                        LibraryStatus.MASTERED -> ContrastTones.correct()
                        LibraryStatus.NEEDS_REVIEW -> ContrastTones.review()
                        LibraryStatus.IN_PROGRESS -> ContrastTones.practice()
                        LibraryStatus.NOT_STARTED, null -> ContrastTones.neutral()
                    }
                },
                glyph = { null },
                modifier = Modifier.padding(vertical = 8.dp).testTag(ContrastTags.STATUS_FILTER).semantics { contentDescription = statusDescription },
            )
            if (data.rows.isEmpty()) {
                MessageContent(stringResource(R.string.contrast_library_no_match))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "count") {
                        Text(
                            stringResource(R.string.contrast_ui_groups_count, data.rows.size),
                            style = ContrastText.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(data.rows, key = { it.group.id }) { row ->
                        ContrastGroupCard(row, onClick = { onOpenGroup(row.group.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/**
 * Single-choice chips in the colour of what they filter (a category, a status). The selected one is
 * filled; the colour change is animated. Each chip is a radio button for TalkBack, min 48 dp tall.
 */
@Composable
internal fun <K> ToneFilterRow(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    tone: @Composable (K) -> Tone,
    glyph: (K) -> String?,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    LazyRow(
        modifier = modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.first.toString() }) { (key, label) ->
            val t = tone(key)
            val isSelected = key == selected
            val container by animateColorAsState(if (isSelected) t.accent else MaterialTheme.colorScheme.surface, if (reduce) snap() else tween(N3Motion.MEDIUM), label = "chip")
            val content by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.surface else t.accent, if (reduce) snap() else tween(N3Motion.MEDIUM), label = "chipText")
            Surface(
                shape = ChipShape,
                color = container,
                border = BorderStroke(1.dp, t.accent.copy(alpha = if (isSelected) 1f else 0.4f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(key) }),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    glyph(key)?.let { Text(it, style = ContrastText.label.japanese(), color = content) }
                    Text(label, style = ContrastText.label.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = content)
                }
            }
        }
    }
}
