package com.n3sensei.app.feature.lessons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.content.ExampleSentenceItem
import com.n3sensei.app.core.ui.content.ItemStatusBadge
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.course.CourseLesson
import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.TrackedItem

/**
 * A course lesson: Kanji, Từ vựng and Ngữ pháp as three tabs the learner switches between at
 * any time. Each tab keeps its own scroll position; nothing is locked or recorded here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    viewModel: LessonDetailViewModel,
    onOpenVocabulary: (id: String) -> Unit,
    onOpenKanji: (id: String) -> Unit,
    onOpenGrammar: (id: String) -> Unit,
    onStartPractice: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional entry point to Contrast Lab for the lesson's items (shown only when published groups exist). */
    contrastLinks: @Composable (CourseLesson) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val kanjiWords by viewModel.kanjiWords.collectAsStateWithLifecycle()
    val grammarDetails by viewModel.grammarDetails.collectAsStateWithLifecycle()
    // One list state per tab: coming back to a tab shows it where the learner left it.
    val listStates = LessonTab.entries.associateWith { rememberLazyListState() }
    UiStateContent(
        state = state,
        notFoundMessage = stringResource(R.string.lesson_not_found),
        modifier = modifier,
    ) { lesson ->
        Column(Modifier.fillMaxSize()) {
            LessonHeader(lesson, onStartPractice) { contrastLinks(lesson) }
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                LessonTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label(lesson), maxLines = 1) },
                    )
                }
            }
            val listState: LazyListState = listStates.getValue(selectedTab)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    LessonTab.KANJI -> kanjiTab(lesson.kanji, kanjiWords, viewModel::toggleKanji, onOpenKanji)
                    LessonTab.VOCABULARY -> vocabularyTab(lesson, onOpenVocabulary)
                    LessonTab.GRAMMAR -> grammarTab(lesson.grammar, grammarDetails, viewModel::toggleGrammar, onOpenGrammar)
                }
            }
        }
    }
}

@Composable
private fun LessonTab.label(lesson: CourseLesson): String = when (this) {
    LessonTab.KANJI -> stringResource(R.string.lesson_tab_kanji, lesson.kanji.size)
    LessonTab.VOCABULARY -> stringResource(R.string.lesson_tab_vocabulary, lesson.words.size)
    LessonTab.GRAMMAR -> stringResource(R.string.lesson_tab_grammar, lesson.grammar.size)
}

@Composable
private fun LessonHeader(lesson: CourseLesson, onStartPractice: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.course_lesson_title, lesson.number), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.course_lesson_counts, lesson.kanji.size, lesson.words.size, lesson.grammar.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onStartPractice, enabled = !lesson.isEmpty, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.lesson_practice))
        }
        extra()
    }
}

private fun LazyListScope.empty(messageRes: Int) {
    item(key = "empty") {
        Text(stringResource(messageRes), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    }
}

private fun LazyListScope.kanjiTab(
    kanji: List<TrackedItem<Kanji>>,
    words: Map<String, Expansion<List<KanjiWord>>>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (kanji.isEmpty()) return empty(R.string.course_no_kanji)
    items(kanji, key = { "k-${it.item.id}" }) { tracked ->
        KanjiRow(tracked, words[tracked.item.id], onToggle = { onToggle(tracked.item.id) }, onOpen = { onOpen(tracked.item.id) })
    }
}

/** A kanji that expands in place to show its words (not counted as kanji or vocabulary). */
@Composable
private fun KanjiRow(tracked: TrackedItem<Kanji>, words: Expansion<List<KanjiWord>>?, onToggle: () -> Unit, onOpen: () -> Unit) {
    val k = tracked.item
    val expanded = words != null
    val stateText = stringResource(if (expanded) R.string.course_expanded else R.string.course_collapsed)
    OutlinedCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(japaneseText(k.character), style = MaterialTheme.typography.displaySmall.japanese())
            Column(Modifier.weight(1f)) {
                k.hanViet?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(k.meaningVi, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        R.string.kanji_readings_line,
                        k.onReadings.joinToString("、").ifEmpty { stringResource(R.string.common_none) },
                        k.kunReadings.joinToString("、").ifEmpty { stringResource(R.string.common_none) },
                    ),
                    style = MaterialTheme.typography.bodySmall.japanese(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ItemStatusBadge(tracked.status, tracked.isMarked)
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (words != null) {
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (words) {
                    Expansion.Loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                    Expansion.Failed -> Text(stringResource(R.string.course_load_failed), color = MaterialTheme.colorScheme.error)
                    is Expansion.Loaded -> {
                        Text(stringResource(R.string.flashcard_kanji_words, words.value.size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (words.value.isEmpty()) Text(stringResource(R.string.flashcard_kanji_no_words), style = MaterialTheme.typography.bodyMedium)
                        words.value.forEach { WordLine(it) }
                    }
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.course_open_kanji)) }
            }
        }
    }
}

@Composable
private fun WordLine(word: KanjiWord) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.45f)) {
                if (word.furigana != null && LocalShowFurigana.current) {
                    FuriganaText(word.furigana, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(japaneseText(word.japanese), style = MaterialTheme.typography.titleMedium.japanese())
                }
            }
            Text(word.meaningVi, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
        }
    }
}

private fun LazyListScope.vocabularyTab(lesson: CourseLesson, onOpen: (String) -> Unit) {
    if (lesson.vocabulary.isEmpty()) return empty(R.string.course_no_words)
    lesson.vocabulary.forEachIndexed { index, section ->
        item(key = "section-$index-${section.label}") {
            Column(Modifier.padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp)) {
                Text(
                    stringResource(R.string.course_vocabulary_section, section.label, section.words.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(section.groupTitle, style = MaterialTheme.typography.bodySmall.japanese(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(section.words, key = { "v-${it.item.id}" }) { tracked ->
            VocabularyCard(tracked.item, onClick = { onOpen(tracked.item.id) }, status = tracked.status, isMarked = tracked.isMarked)
        }
    }
}

private fun LazyListScope.grammarTab(
    grammar: List<TrackedItem<GrammarPoint>>,
    details: Map<String, Expansion<GrammarDetail>>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (grammar.isEmpty()) return empty(R.string.course_no_grammar)
    items(grammar, key = { "g-${it.item.id}" }) { tracked ->
        GrammarRow(tracked, details[tracked.item.id], onToggle = { onToggle(tracked.item.id) }, onOpen = { onOpen(tracked.item.id) })
    }
}

/** A grammar point that expands to its structure, usage, caution and examples (as stored). */
@Composable
private fun GrammarRow(tracked: TrackedItem<GrammarPoint>, detail: Expansion<GrammarDetail>?, onToggle: () -> Unit, onOpen: () -> Unit) {
    val g = tracked.item
    val expanded = detail != null
    val stateText = stringResource(if (expanded) R.string.course_expanded else R.string.course_collapsed)
    OutlinedCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(japaneseText(g.pattern), style = MaterialTheme.typography.titleMedium.japanese())
                Text(g.meaningVi, style = MaterialTheme.typography.bodyMedium)
            }
            ItemStatusBadge(tracked.status, tracked.isMarked)
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (detail != null) {
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Labelled(R.string.edit_field_structure, g.structure, japanese = true)
                if (g.usage.isNotBlank()) Labelled(R.string.grammar_usage, g.usage)
                g.cautionNote?.let { Labelled(R.string.edit_field_caution, it) }
                when (detail) {
                    Expansion.Loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                    Expansion.Failed -> Text(stringResource(R.string.course_load_failed), color = MaterialTheme.colorScheme.error)
                    is Expansion.Loaded -> if (detail.value.examples.isNotEmpty()) {
                        Text(stringResource(R.string.detail_examples), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        detail.value.examples.forEach { ExampleSentenceItem(it, highlight = ExampleHighlight.ofGrammar(it.japanese, g.pattern)) }
                    }
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.course_open_grammar)) }
            }
        }
    }
}

@Composable
private fun Labelled(labelRes: Int, text: String, japanese: Boolean = false) {
    Column {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (japanese) japaneseText(text) else AnnotatedString(text),
            style = if (japanese) MaterialTheme.typography.bodyLarge.japanese() else MaterialTheme.typography.bodyMedium,
        )
    }
}
