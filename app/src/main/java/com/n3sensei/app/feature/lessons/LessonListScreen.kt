package com.n3sensei.app.feature.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.N3ProgressBar
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.course.CourseLesson

@Composable
fun LessonListScreen(
    viewModel: LessonListViewModel,
    onOpenLesson: (number: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.lessons.collectAsStateWithLifecycle()
    UiStateContent(state = state, emptyMessage = stringResource(R.string.common_no_content), modifier = modifier) { lessons ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.course_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(lessons, key = { it.number }) { lesson ->
                CourseLessonCard(lesson, onOpen = { onOpenLesson(lesson.number) })
            }
        }
    }
}

@Composable
private fun CourseLessonCard(lesson: CourseLesson, onOpen: () -> Unit) {
    N3Card(onClick = onOpen, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.course_lesson_title, lesson.number), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.course_lesson_counts, lesson.kanji.size, lesson.words.size, lesson.grammar.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.course_open)) }
        }
        // One bar per kind, each from the items' own progress: not merged into one number.
        ProgressLine(R.string.course_progress_kanji, lesson.startedKanji(), lesson.kanji.size)
        ProgressLine(R.string.course_progress_words, lesson.startedWords(), lesson.words.size)
        ProgressLine(R.string.course_progress_grammar, lesson.startedGrammar(), lesson.grammar.size)
    }
}

@Composable
private fun ProgressLine(labelRes: Int, started: Int, total: Int) {
    if (total == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(labelRes, started, total), style = MaterialTheme.typography.labelMedium)
        N3ProgressBar(started.toFloat() / total)
    }
}
