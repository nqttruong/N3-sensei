package com.n3sensei.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.reminder.LessonSelection

object KanjiReviewLessonsTags {
    fun lesson(id: String) = "kanji_review_lesson_$id"
    fun selectAll(level: String) = "kanji_review_select_all_$level"
    fun clearAll(level: String) = "kanji_review_clear_$level"
}

@Composable
fun KanjiReviewLessonsScreen(viewModel: KanjiReviewLessonsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, emptyMessage = stringResource(R.string.kanji_review_lessons_empty)) { data ->
        KanjiReviewLessonsContent(data, viewModel::toggle, viewModel::selectAll, viewModel::clearAll)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanjiReviewLessonsContent(
    state: KanjiReviewLessonsUiState,
    onToggle: (level: String, lessonId: String) -> Unit,
    onSelectAll: (level: String) -> Unit,
    onClearAll: (level: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        item(key = "hint") {
            Text(
                stringResource(R.string.kanji_review_lessons_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        state.levels.forEach { group ->
            item(key = "level-${group.level}") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.kanji_review_level_title, group.level),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        when {
                            group.selection == LessonSelection.All -> stringResource(R.string.kanji_review_level_all, group.lessons.size)
                            group.checkedCount == 0 -> stringResource(R.string.kanji_review_level_none, group.lessons.size)
                            else -> stringResource(R.string.kanji_review_level_some, group.checkedCount, group.lessons.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSelectAll(group.level) },
                            modifier = Modifier.testTag(KanjiReviewLessonsTags.selectAll(group.level)),
                        ) { Text(stringResource(R.string.kanji_review_select_all, group.level)) }
                        OutlinedButton(
                            onClick = { onClearAll(group.level) },
                            modifier = Modifier.testTag(KanjiReviewLessonsTags.clearAll(group.level)),
                        ) { Text(stringResource(R.string.kanji_review_clear_all, group.level)) }
                    }
                }
            }
            items(group.lessons, key = { it.id }) { lesson ->
                LessonRow(lesson, group.isChecked(lesson.id)) { onToggle(group.level, lesson.id) }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: LessonSummary, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = { Text(stringResource(R.string.kanji_review_lesson_label, lesson.number)) },
        supportingContent = { Text(stringResource(R.string.kanji_review_lesson_details, lesson.title, lesson.kanjiCount)) },
        modifier = Modifier
            .testTag(KanjiReviewLessonsTags.lesson(lesson.id))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}
