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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
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
    onPractice: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastGroupContent(state, links, viewModel::retry, modifier, onPractice)
}

@Composable
fun ContrastGroupContent(
    state: UiState<ContrastLearningContent>,
    links: SourceLinks,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPractice: ((groupId: String) -> Unit)? = null,
) {
    UiStateContent(state, modifier, onRetry = onRetry) { content ->
        when (content) {
            is ContrastLearningContent.Retired ->
                MessageContent(stringResource(R.string.contrast_retired, content.retired.reasonVi), modifier.testTag(ContrastTags.GROUP))
            is ContrastLearningContent.Group -> GroupBody(content, links, modifier, onPractice)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupBody(content: ContrastLearningContent.Group, links: SourceLinks, modifier: Modifier, onPractice: ((String) -> Unit)?) {
    val detail = content.detail
    val group = detail.group
    val tone = ContrastTones.category(group.contentType)
    val itemIndex = detail.items.withIndex().associate { (i, item) -> item.id to i }
    val labels = detail.items.associate { it.id to it.label }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag(ContrastTags.GROUP),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero: category, title, the items, status.
        GradientHeader(tone) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryBadge(group.contentType, onGradient = true)
                TonePill(group.jlptLevel, tone, onGradient = true)
                if (content.isDue) TonePill(stringResource(R.string.contrast_status_due), tone, onGradient = true)
            }
            Text(group.title, style = ContrastText.groupTitle, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.contrast_progress_label, masteryLabel(content.mastery.mastery)),
                style = ContrastText.bodySecondary,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Text(stringResource(R.string.contrast_progress_note), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onPractice != null && group.questionCount > 0) {
            GradientButton(stringResource(R.string.contrast_practice_group), ContrastTones.practice(), { onPractice(group.id) }, Modifier.testTag(ContrastTags.PRACTICE_GROUP))
        }

        // 1. Overview.
        SectionTitle(stringResource(R.string.contrast_section_overview), tone, glyph = "概")
        ContrastCard(tone = tone) { Text(group.summaryVi, style = ContrastText.body) }
        detail.theory.forEachIndexed { i, block ->
            if (i < 2) {
                ContrastCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberDot(i + 1, tone)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(block.titleVi, style = ContrastText.section.copy(fontSize = 18.sp, lineHeight = 26.sp), modifier = Modifier.semantics { heading() })
                            Text(block.bodyVi, style = ContrastText.body)
                        }
                    }
                }
            }
        }
        if (detail.theory.size > 2) {
            ExpandableCard(stringResource(R.string.contrast_ui_more_theory), tone) {
                detail.theory.drop(2).forEach { block ->
                    Text(block.titleVi, style = ContrastText.section.copy(fontSize = 18.sp, lineHeight = 26.sp), modifier = Modifier.semantics { heading() })
                    Text(block.bodyVi, style = ContrastText.body)
                }
            }
        }

        // 2. The items, each in its own colour.
        SectionTitle(stringResource(R.string.contrast_section_items), tone, glyph = "比")
        detail.items.forEachIndexed { i, item -> ItemCard(item, i, detail.sources[item.id], links) }

        // 3. Differences, aspect by aspect.
        if (detail.comparison.isNotEmpty()) {
            SectionTitle(stringResource(R.string.contrast_section_comparison), tone, glyph = "違")
            detail.comparison.forEach { row ->
                ContrastCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(row.aspectVi, style = ContrastText.section.copy(fontSize = 18.sp, lineHeight = 26.sp), color = tone.accent, modifier = Modifier.semantics { heading() })
                    detail.items.filter { it.id in row.values }.forEach { item ->
                        val itemTone = ContrastTones.item(itemIndex.getValue(item.id))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 9.dp).size(10.dp).clip(CircleShape).background(itemTone.accent))
                            Column(Modifier.weight(1f)) {
                                Text(japaneseText(item.label), style = ContrastText.label.copy(fontSize = 16.sp).japanese(), color = itemTone.accent)
                                Text(row.values.getValue(item.id), style = ContrastText.body)
                            }
                        }
                    }
                }
            }
        }

        // 4. Examples, by item, the compared form highlighted.
        if (detail.examples.isNotEmpty()) {
            SectionTitle(stringResource(R.string.contrast_section_examples), tone, glyph = "例")
            detail.examples.forEach { example ->
                val index = itemIndex[example.itemId] ?: 0
                val itemTone = ContrastTones.item(index)
                val item = detail.items.getOrNull(index)
                ContrastCard(modifier = Modifier.testTag(ContrastTags.example(example.id)), contentPadding = PaddingValues(0.dp)) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(5.dp).fillMaxHeight().background(itemTone.accent))
                        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            labels[example.itemId]?.let { TonePill(stringResource(R.string.contrast_example_item, it), itemTone) }
                            val markup = example.furigana ?: example.japanese
                            FuriganaText(markup, style = ContrastText.exampleJp, highlight = item?.let { highlightOf(example.japanese, it.label) }.orEmpty(), highlightColor = itemTone.accent)
                            Text(example.translationVi, style = ContrastText.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // 5. Memory tips.
        if (detail.memoryTips.isNotEmpty()) {
            val tipTone = ContrastTones.review()
            SectionTitle(stringResource(R.string.contrast_section_tips), tipTone, glyph = "覚")
            detail.memoryTips.forEach { tip ->
                ContrastCard(tone = tipTone) { Text(tip.textVi, style = ContrastText.body) }
            }
        }

        // 6. Common mistakes: opened on demand.
        if (detail.commonMistakes.isNotEmpty()) {
            val wrongTone = ContrastTones.incorrect()
            val rightTone = ContrastTones.correct()
            ExpandableCard(
                stringResource(R.string.contrast_section_mistakes),
                wrongTone,
                subtitle = stringResource(R.string.contrast_ui_mistakes_count, detail.commonMistakes.size),
            ) {
                detail.commonMistakes.forEachIndexed { i, mistake ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(mistake.situationVi, style = ContrastText.body.copy(fontWeight = FontWeight.SemiBold))
                    VerdictLine(stringResource(R.string.contrast_mistake_wrong), Icons.Filled.Close, wrongTone, mistake.wrong.furigana ?: mistake.wrong.japanese)
                    VerdictLine(stringResource(R.string.contrast_mistake_correct), Icons.Filled.Check, rightTone, mistake.correct.furigana ?: mistake.correct.japanese)
                    Text(mistake.explanationVi, style = ContrastText.body)
                    mistake.confusion?.let { c ->
                        Text(
                            stringResource(R.string.contrast_mistake_confusion, labels[c.expectedItemId] ?: c.expectedItemId, labels[c.chosenItemId] ?: c.chosenItemId),
                            style = ContrastText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!detail.licenseVerified) {
            Text(stringResource(R.string.contrast_license_unverified), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onPractice != null && group.questionCount > 0) {
            GradientButton(stringResource(R.string.contrast_practice_group), ContrastTones.practice(), { onPractice(group.id) })
        }
    }
}

@Composable
private fun NumberDot(n: Int, tone: Tone) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(tone.container), contentAlignment = Alignment.Center) {
        Text(n.toString(), style = ContrastText.label, color = tone.accent)
    }
}

/** "Sai" / "Đúng" with an icon (not only colour), then the sentence. */
@Composable
private fun VerdictLine(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Tone, markup: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tone.container).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(18.dp))
            Text(label, style = ContrastText.label, color = tone.accent)
        }
        FuriganaText(markup, style = ContrastText.exampleJp.copy(fontSize = 20.sp, lineHeight = 32.sp), color = tone.onContainer)
    }
}

/**
 * Where the compared form appears in an example (display only; the text is never changed): the label
 * without its leading 〜, or, for conjugated words, its stem (label minus the last kana).
 */
internal fun highlightOf(sentence: String, label: String): List<IntRange> {
    val core = label.removePrefix("〜").removePrefix("～").trim()
    if (core.isEmpty()) return emptyList()
    val candidates = buildList {
        add(core)
        if (core.length >= 2) add(core.dropLast(1))
    }
    for (c in candidates) {
        val at = sentence.indexOf(c)
        if (at >= 0) return listOf(at until at + c.length)
    }
    return emptyList()
}

/** One compared item in its own colour: the form large, its fields, its source status and a link to the existing entry. */
@Composable
private fun ItemCard(item: ContrastItem, index: Int, source: ResolvedSource?, links: SourceLinks) {
    val tone = ContrastTones.item(index)
    ContrastCard(tone = tone, modifier = Modifier.testTag(ContrastTags.itemCard(item.id)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val heroStyle = if (item.details is ItemDetails.Kanji) ContrastText.heroKanji else ContrastText.heroPattern
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(tone.accent), contentAlignment = Alignment.Center) {
                Text(('A' + index).toString(), style = ContrastText.label, color = MaterialTheme.colorScheme.surface)
            }
            FuriganaText(item.labelFurigana ?: item.label, style = heroStyle, color = tone.accent)
        }
        SourceNotice(item, source)
        when (val d = item.details) {
            is ItemDetails.Grammar -> {
                Field(stringResource(R.string.contrast_field_structure), d.connection, tone, japaneseValue = true)
                Field(stringResource(R.string.contrast_field_meaning), d.meaningVi, tone)
                Field(stringResource(R.string.contrast_field_nuance), d.nuanceVi, tone)
            }
            is ItemDetails.Vocabulary -> {
                Field(stringResource(R.string.contrast_field_reading), d.reading, tone, japaneseValue = true)
                Field(stringResource(R.string.contrast_field_meaning), d.meaningVi, tone)
                Field(stringResource(R.string.contrast_field_usage), d.usageVi, tone)
                if (d.collocations.isNotEmpty()) {
                    Text(stringResource(R.string.contrast_field_collocations), style = ContrastText.label, color = tone.accent)
                    d.collocations.forEach { c ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(10.dp)) {
                            FuriganaText(c.furigana ?: c.japanese, style = ContrastText.exampleJp.copy(fontSize = 20.sp, lineHeight = 32.sp))
                            Text(c.meaningVi, style = ContrastText.bodySecondary)
                        }
                    }
                }
            }
            is ItemDetails.Kanji -> {
                d.hanViet?.let { Field(stringResource(R.string.contrast_field_han_viet), it, tone) }
                Field(stringResource(R.string.contrast_field_meaning), d.meaningVi, tone)
                if (d.onReadings.isNotEmpty()) Field(stringResource(R.string.contrast_field_on), d.onReadings.joinToString("、"), tone, japaneseValue = true)
                if (d.kunReadings.isNotEmpty()) Field(stringResource(R.string.contrast_field_kun), d.kunReadings.joinToString("、"), tone, japaneseValue = true)
                d.radical?.let { Field(stringResource(R.string.contrast_field_radical), "${it.character} · ${it.nameVi}", tone) }
                Field(stringResource(R.string.contrast_field_shape), d.shapeNoteVi, tone)
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
            }) { Text(stringResource(R.string.contrast_open_source), style = ContrastText.label.copy(fontSize = 15.sp), color = tone.accent) }
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
private fun Field(label: String, value: String, tone: Tone, japaneseValue: Boolean = false) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = ContrastText.label, color = tone.accent)
        Text(if (japaneseValue) japaneseText(value) else AnnotatedString(value), style = if (japaneseValue) ContrastText.body.japanese() else ContrastText.body)
    }
}
