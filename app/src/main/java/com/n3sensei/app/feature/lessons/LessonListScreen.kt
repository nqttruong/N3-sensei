package com.n3sensei.app.feature.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.content.LessonCard
import com.n3sensei.app.core.ui.state.UiStateContent

@Composable
fun LessonListScreen(
    viewModel: LessonListViewModel,
    onOpenLesson: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.lessons.collectAsStateWithLifecycle()
    UiStateContent(state = state, emptyMessage = stringResource(R.string.common_no_content), modifier = modifier) { lessons ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lessons, key = { it.id }) { lesson ->
                LessonCard(lesson, onClick = { onOpenLesson(lesson.id) })
            }
        }
    }
}
