package com.n3sensei.app.feature.lessons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.GrammarCard
import com.n3sensei.app.core.ui.content.KanjiCard
import com.n3sensei.app.core.ui.content.VocabularyCard
import com.n3sensei.app.core.ui.content.lessonCountsText
import com.n3sensei.app.core.ui.state.UiStateContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LessonDetailScreen(
    viewModel: LessonDetailViewModel,
    onOpenVocabulary: (id: String) -> Unit,
    onOpenKanji: (id: String) -> Unit,
    onOpenGrammar: (id: String) -> Unit,
    onStartPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    UiStateContent(
        state = state,
        notFoundMessage = stringResource(R.string.lesson_not_found),
        modifier = modifier,
    ) { detail ->
        // One scrolling list: the header scrolls away, the tab row sticks to the top.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") { LessonHeader(detail, onStartPractice) }
            stickyHeader(key = "tabs") {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == LessonTab.VOCABULARY,
                        onClick = { viewModel.selectTab(LessonTab.VOCABULARY) },
                        text = { Text(stringResource(R.string.lesson_tab_vocabulary, detail.vocabulary.size)) },
                    )
                    Tab(
                        selected = selectedTab == LessonTab.KANJI,
                        onClick = { viewModel.selectTab(LessonTab.KANJI) },
                        text = { Text(stringResource(R.string.lesson_tab_kanji, detail.kanji.size)) },
                    )
                    Tab(
                        selected = selectedTab == LessonTab.GRAMMAR,
                        onClick = { viewModel.selectTab(LessonTab.GRAMMAR) },
                        text = { Text(stringResource(R.string.lesson_tab_grammar, detail.grammar.size)) },
                    )
                }
            }
            when (selectedTab) {
                LessonTab.VOCABULARY -> itemsOrEmpty(detail.vocabulary, { "v-${it.id}" }, R.string.lesson_empty_vocabulary) {
                    VocabularyCard(it, onClick = { onOpenVocabulary(it.id) })
                }
                LessonTab.KANJI -> itemsOrEmpty(detail.kanji, { "k-${it.id}" }, R.string.lesson_empty_kanji) {
                    KanjiCard(it, onClick = { onOpenKanji(it.id) })
                }
                LessonTab.GRAMMAR -> itemsOrEmpty(detail.grammar, { "g-${it.id}" }, R.string.lesson_empty_grammar) {
                    GrammarCard(it, onClick = { onOpenGrammar(it.id) })
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LessonHeader(detail: LessonDetail, onStartPractice: () -> Unit) {
    val lesson = detail.lesson
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.lesson_number, lesson.number, lesson.jlptLevel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
        lesson.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(
            lessonCountsText(lesson),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onStartPractice,
            enabled = detail.hasItems,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text(stringResource(R.string.lesson_practice)) }
    }
}

private fun <T> LazyListScope.itemsOrEmpty(
    items: List<T>,
    key: (T) -> Any,
    emptyMessageRes: Int,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        item(key = "empty") {
            Text(
                stringResource(emptyMessageRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    items(items, key = key) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { itemContent(it) }
    }
}
