package com.n3sensei.app.core.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ExampleSentence

/**
 * One example: the Japanese (with furigana when there is some) and its translation below.
 * [highlight]: ranges of the Japanese to emphasize, see [com.n3sensei.app.domain.example.ExampleHighlight].
 */
@Composable
fun ExampleSentenceItem(example: ExampleSentence, modifier: Modifier = Modifier, highlight: List<IntRange> = emptyList()) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        // Plain text is drawn by FuriganaText too, so the emphasis looks the same either way.
        FuriganaText(example.furigana ?: example.japanese, style = MaterialTheme.typography.bodyLarge, highlight = highlight)
        Text(
            example.translationVi,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The examples of an item, under the theory: a header that folds them away (the choice is kept
 * while the screen lives) and each example with what it illustrates emphasized. Nothing at all
 * when there is no example, so an item without examples shows no empty section.
 */
@Composable
fun ExamplesSection(
    title: String,
    examples: List<ExampleSentence>,
    modifier: Modifier = Modifier,
    highlight: (ExampleSentence) -> List<IntRange> = { emptyList() },
) {
    if (examples.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    val state = stringResource(if (expanded) R.string.examples_state_expanded else R.string.examples_state_collapsed)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.examples_collapse else R.string.examples_expand)) {
                    expanded = !expanded
                }
                .semantics { stateDescription = state }
                .testTag(ExamplesSectionTags.HEADER),
        ) {
            SectionHeader(stringResource(R.string.examples_header, title, examples.size), Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                examples.forEachIndexed { i, example ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ExampleSentenceItem(example, highlight = highlight(example))
                }
            }
        }
    }
}

object ExamplesSectionTags {
    const val HEADER = "examples_section_header"
}

/** Source and license line. Never implies a verified license unless it was verified. */
@Composable
fun SourceNote(source: ContentSource, modifier: Modifier = Modifier) {
    val license = if (source.licenseVerified) {
        source.license
    } else {
        stringResource(R.string.source_license_unverified, source.license)
    }
    Text(
        text = stringResource(R.string.source_line, source.name, license),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
