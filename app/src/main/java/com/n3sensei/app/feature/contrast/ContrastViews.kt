package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n3sensei.app.R
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastMastery

object ContrastTags {
    const val HOME = "contrast_home"
    const val LIBRARY = "contrast_library"
    const val GROUP = "contrast_group"
    const val EXPLORE = "contrast_explore"
    const val START = "contrast_start_practice"
    const val RESUME = "contrast_resume_practice"
    const val PRACTICE_GROUP = "contrast_practice_group"
    const val TYPE_FILTER = "contrast_filter_type"
    const val STATUS_FILTER = "contrast_filter_status"
    const val SEARCH = "contrast_search"
    fun groupRow(id: String) = "contrast_group_row_$id"
    fun category(type: ContrastContentType) = "contrast_category_${type.name}"
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

/**
 * A group in a list: category badge, the items to tell apart (each in its own colour, as one text
 * "A / B" so search results and TalkBack read it whole), a two-line summary and the learner's status.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContrastGroupCard(row: ContrastGroupRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val group = row.group
    val tone = ContrastTones.category(group.contentType)
    ContrastCard(onClick = onClick, modifier = modifier.testTag(ContrastTags.groupRow(group.id)), contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Brush.verticalGradient(tone.gradient)))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryBadge(group.contentType)
                    TonePill(group.jlptLevel, ContrastTones.neutral())
                    StatusPill(row.mastery)
                    if (row.isDue && row.mastery != ContrastMastery.NEW) TonePill(stringResource(R.string.contrast_status_due), ContrastTones.review())
                }
                ItemLabels(group.itemLabels, group.contentType, Modifier.semantics { heading() })
                Text(
                    group.summaryVi,
                    style = ContrastText.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "A / B / C" with each item in its own hue; kanji groups get bigger glyphs. */
@Composable
fun ItemLabels(labels: List<String>, type: ContrastContentType, modifier: Modifier = Modifier) {
    val separator = MaterialTheme.colorScheme.outline
    val tones = labels.indices.map { ContrastTones.item(it) }
    val text = buildAnnotatedString {
        labels.forEachIndexed { i, label ->
            if (i > 0) withStyle(SpanStyle(color = separator, fontWeight = FontWeight.Normal)) { append(" / ") }
            withStyle(SpanStyle(color = tones[i].accent)) { append(label) }
        }
    }
    val style = if (type == ContrastContentType.KANJI) ContrastText.itemLabel.copy(fontSize = 30.sp, lineHeight = 40.sp) else ContrastText.itemLabel
    Text(japaneseTextOf(text), style = style, modifier = modifier)
}

private fun japaneseTextOf(text: AnnotatedString): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(localeList = LocaleList("ja-JP")), 0, text.length)
}

/** Learning status in its colour: new (neutral), learning (practice), reviewing (review), mastered (correct). */
@Composable
fun StatusPill(mastery: ContrastMastery) {
    val tone = when (mastery) {
        ContrastMastery.NEW -> ContrastTones.neutral()
        ContrastMastery.LEARNING -> ContrastTones.practice()
        ContrastMastery.REVIEWING -> ContrastTones.review()
        ContrastMastery.MASTERED -> ContrastTones.correct()
    }
    TonePill(masteryLabel(mastery), tone)
}
