package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.ResolvedSource
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.SourceRefStatus

/** Opens the existing detail screen of a source row (grammar, word or kanji id). */
data class SourceLinks(
    val openGrammar: (String) -> Unit,
    val openVocabulary: (String) -> Unit,
    val openKanji: (String) -> Unit,
)

@Composable
fun ContrastGroupScreen(
    viewModel: ContrastLearningViewModel,
    links: SourceLinks,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastGroupContent(state, links, viewModel::retry, modifier)
}

@Composable
fun ContrastGroupContent(
    state: UiState<ContrastLearningContent>,
    links: SourceLinks,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier, onRetry = onRetry) { content ->
        when (content) {
            is ContrastLearningContent.Retired ->
                MessageContent(stringResource(R.string.contrast_retired, content.retired.reasonVi), modifier.testTag(ContrastTags.GROUP))
            is ContrastLearningContent.Group -> GroupBody(content, links, modifier)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupBody(content: ContrastLearningContent.Group, links: SourceLinks, modifier: Modifier) {
    val detail = content.detail
    val group = detail.group
    val labels = detail.items.associate { it.id to it.label }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = N3Spacing.screen, vertical = N3Spacing.s).testTag(ContrastTags.GROUP),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
    ) {
        Text(group.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
            Tag(typeLabel(group.contentType))
            Tag(group.jlptLevel, tone = TagTone.Accent)
            if (content.isDue) Tag(stringResource(R.string.contrast_status_due), tone = TagTone.Info)
        }
        Text(stringResource(R.string.contrast_progress_label, masteryLabel(content.mastery.mastery)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.contrast_progress_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionHeader(stringResource(R.string.contrast_section_overview))
        Text(group.summaryVi, style = MaterialTheme.typography.bodyLarge)
        detail.theory.forEach { block ->
            N3Card {
                Text(block.titleVi, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(block.bodyVi, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionHeader(stringResource(R.string.contrast_section_items))
        detail.items.forEach { item -> ItemCard(item, detail.sources[item.id], links) }

        if (detail.comparison.isNotEmpty()) {
            SectionHeader(stringResource(R.string.contrast_section_comparison))
            detail.comparison.forEach { row ->
                N3Card {
                    Text(row.aspectVi, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    detail.items.filter { it.id in row.values }.forEach { item ->
                        Field(item.label, row.values.getValue(item.id))
                    }
                }
            }
        }

        if (detail.examples.isNotEmpty()) {
            SectionHeader(stringResource(R.string.contrast_section_examples))
            detail.examples.forEach { example ->
                N3Card(modifier = Modifier.testTag(ContrastTags.example(example.id))) {
                    FuriganaText(example.furigana ?: example.japanese, style = MaterialTheme.typography.titleMedium)
                    Text(example.translationVi, style = MaterialTheme.typography.bodyMedium)
                    labels[example.itemId]?.let {
                        Text(stringResource(R.string.contrast_example_item, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (detail.memoryTips.isNotEmpty()) {
            SectionHeader(stringResource(R.string.contrast_section_tips))
            detail.memoryTips.forEach { tip -> N3Card { Text(tip.textVi, style = MaterialTheme.typography.bodyMedium) } }
        }

        if (detail.commonMistakes.isNotEmpty()) {
            SectionHeader(stringResource(R.string.contrast_section_mistakes))
            detail.commonMistakes.forEach { mistake ->
                N3Card {
                    Text(mistake.situationVi, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.contrast_mistake_wrong), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    FuriganaText(mistake.wrong.furigana ?: mistake.wrong.japanese)
                    Text(stringResource(R.string.contrast_mistake_correct), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    FuriganaText(mistake.correct.furigana ?: mistake.correct.japanese)
                    Text(mistake.explanationVi, style = MaterialTheme.typography.bodyMedium)
                    mistake.confusion?.let { c ->
                        Text(
                            stringResource(R.string.contrast_mistake_confusion, labels[c.expectedItemId] ?: c.expectedItemId, labels[c.chosenItemId] ?: c.chosenItemId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!detail.licenseVerified) {
            Text(stringResource(R.string.contrast_license_unverified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One compared item: its fields by type, its source status and a link to the existing entry. */
@Composable
private fun ItemCard(item: ContrastItem, source: ResolvedSource?, links: SourceLinks) {
    N3Card(modifier = Modifier.testTag(ContrastTags.itemCard(item.id))) {
        FuriganaText(item.labelFurigana ?: item.label, style = MaterialTheme.typography.headlineSmall)
        SourceNotice(item, source)
        when (val d = item.details) {
            is ItemDetails.Grammar -> {
                Field(stringResource(R.string.contrast_field_structure), d.connection)
                Field(stringResource(R.string.contrast_field_meaning), d.meaningVi)
                Field(stringResource(R.string.contrast_field_nuance), d.nuanceVi)
            }
            is ItemDetails.Vocabulary -> {
                Field(stringResource(R.string.contrast_field_reading), d.reading)
                Field(stringResource(R.string.contrast_field_meaning), d.meaningVi)
                Field(stringResource(R.string.contrast_field_usage), d.usageVi)
                if (d.collocations.isNotEmpty()) {
                    Text(stringResource(R.string.contrast_field_collocations), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    d.collocations.forEach { c ->
                        FuriganaText(c.furigana ?: c.japanese)
                        Text(c.meaningVi, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is ItemDetails.Kanji -> {
                d.hanViet?.let { Field(stringResource(R.string.contrast_field_han_viet), it) }
                Field(stringResource(R.string.contrast_field_meaning), d.meaningVi)
                if (d.onReadings.isNotEmpty()) Field(stringResource(R.string.contrast_field_on), d.onReadings.joinToString("、"))
                if (d.kunReadings.isNotEmpty()) Field(stringResource(R.string.contrast_field_kun), d.kunReadings.joinToString("、"))
                d.radical?.let { Field(stringResource(R.string.contrast_field_radical), "${it.character} · ${it.nameVi}") }
                Field(stringResource(R.string.contrast_field_shape), d.shapeNoteVi)
            }
        }
        val currentId = source?.currentId
        if (currentId != null && (source.status == SourceRefStatus.OK || source.status == SourceRefStatus.SOURCE_CHANGED)) {
            TextButton(onClick = {
                when (item.source.sourceType) {
                    ContrastContentType.GRAMMAR -> links.openGrammar(currentId)
                    ContrastContentType.VOCABULARY -> links.openVocabulary(currentId)
                    ContrastContentType.KANJI -> links.openKanji(currentId)
                }
            }) { Text(stringResource(R.string.contrast_open_source)) }
        }
    }
}

/**
 * The source status (ARCHITECTURE §7.3). OK and UNVERIFIABLE show nothing; CHANGED keeps the
 * reviewed Contrast text and says the source moved on; MISSING / AMBIGUOUS show the snapshot taken
 * when the group was written, and nothing is picked or deleted.
 */
@Composable
private fun SourceNotice(item: ContrastItem, source: ResolvedSource?) {
    val text = when (source?.status) {
        SourceRefStatus.SOURCE_CHANGED -> stringResource(R.string.contrast_source_changed)
        SourceRefStatus.SOURCE_MISSING -> stringResource(R.string.contrast_source_missing, snapshotText(item))
        SourceRefStatus.AMBIGUOUS -> stringResource(R.string.contrast_source_ambiguous, snapshotText(item))
        SourceRefStatus.OK, SourceRefStatus.UNVERIFIABLE, null -> return
    }
    InfoBanner(text, modifier = Modifier.testTag(ContrastTags.sourceNotice(item.id)))
}

private fun snapshotText(item: ContrastItem): String = item.source.sourceSnapshot.values.joinToString(" · ") { value ->
    when (value) {
        is SnapshotValue.Text -> value.value
        is SnapshotValue.Texts -> value.values.joinToString("、")
    }
}.ifBlank { item.label }

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
