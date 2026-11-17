package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastMastery

object ContrastTags {
    const val HOME = "contrast_home"
    const val LIBRARY = "contrast_library"
    const val GROUP = "contrast_group"
    const val EXPLORE = "contrast_explore"
    const val TYPE_FILTER = "contrast_filter_type"
    const val STATUS_FILTER = "contrast_filter_status"
    const val SEARCH = "contrast_search"
    fun groupRow(id: String) = "contrast_group_row_$id"
    fun itemCard(id: String) = "contrast_item_$id"
    fun example(id: String) = "contrast_example_$id"
    fun sourceNotice(itemId: String) = "contrast_source_$itemId"
}

@Composable
fun typeLabel(type: ContrastContentType): String = stringResource(
    when (type) {
        ContrastContentType.GRAMMAR -> R.string.contrast_type_grammar
        ContrastContentType.VOCABULARY -> R.string.contrast_type_vocabulary
        ContrastContentType.KANJI -> R.string.contrast_type_kanji
    },
)

@Composable
fun masteryLabel(mastery: ContrastMastery): String = stringResource(
    when (mastery) {
        ContrastMastery.NEW -> R.string.contrast_status_not_started
        ContrastMastery.LEARNING -> R.string.contrast_status_in_progress
        ContrastMastery.REVIEWING -> R.string.contrast_status_reviewing
        ContrastMastery.MASTERED -> R.string.contrast_status_mastered
    },
)

@Composable
fun statusLabel(status: LibraryStatus): String = stringResource(
    when (status) {
        LibraryStatus.NOT_STARTED -> R.string.contrast_status_not_started
        LibraryStatus.IN_PROGRESS -> R.string.contrast_status_in_progress
        LibraryStatus.NEEDS_REVIEW -> R.string.contrast_status_needs_review
        LibraryStatus.MASTERED -> R.string.contrast_status_mastered
    },
)

/** A group in a list: its items, type and level, summary and what the learner did on it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContrastGroupCard(row: ContrastGroupRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val group = row.group
    N3Card(onClick = onClick, modifier = modifier.testTag(ContrastTags.groupRow(group.id))) {
        Text(
            group.itemLabels.joinToString(" / "),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (group.title != group.itemLabels.joinToString(" / ")) {
            Text(group.title, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
            Tag(typeLabel(group.contentType))
            Tag(group.jlptLevel, tone = TagTone.Accent)
            Tag(masteryLabel(row.mastery), tone = TagTone.Info)
            if (row.isDue && row.mastery != ContrastMastery.NEW) Tag(stringResource(R.string.contrast_status_due), tone = TagTone.Info)
        }
        Text(group.summaryVi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
