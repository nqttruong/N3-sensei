package com.n3sensei.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.reminder.LessonOption
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.reminder.ReminderLessonCatalog

object ReminderLessonsTags {
    fun lesson(key: String) = "reminder_lesson_$key"
}

@Composable
fun ReminderLessonsScreen(viewModel: ReminderLessonsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, emptyMessage = stringResource(R.string.reminder_lessons_empty)) { data ->
        ReminderLessonsContent(data, viewModel::toggle, viewModel::selectAll, viewModel::clearAll)
    }
}

@Composable
fun ReminderLessonsContent(
    state: ReminderLessonsUiState,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSelectAll) { Text(stringResource(R.string.reminder_lessons_select_all)) }
            OutlinedButton(onClick = onClearAll) { Text(stringResource(R.string.reminder_lessons_clear)) }
        }
        Text(
            when (val s = state.selection) {
                LessonSelection.All -> stringResource(R.string.reminder_lessons_all)
                is LessonSelection.Only -> if (s.keys.isEmpty()) {
                    stringResource(R.string.reminder_lessons_none)
                } else {
                    stringResource(R.string.reminder_lessons_some, s.keys.size, state.options.size)
                }
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(R.string.reminder_lessons_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        HorizontalDivider()
        LazyColumn {
            items(state.options, key = { it.key }) { option -> LessonRow(state.type, option, state.isChecked(option.key)) { onToggle(option.key) } }
        }
    }
}

@Composable
private fun LessonRow(type: ItemType, option: LessonOption, checked: Boolean, onToggle: () -> Unit) {
    val name = if (option.key == ReminderLessonCatalog.MY_WORDS) {
        stringResource(R.string.reminder_lesson_my_words)
    } else {
        stringResource(R.string.reminder_lesson_label, option.label)
    }
    val count = stringResource(
        when (type) {
            ItemType.VOCABULARY -> R.string.reminder_lesson_count_vocabulary
            ItemType.KANJI -> R.string.reminder_lesson_count_kanji
            ItemType.GRAMMAR -> R.string.reminder_lesson_count_grammar
        },
        option.itemCount,
    )
    ListItem(
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = { Text(name) },
        supportingContent = { Text(listOfNotNull(option.title, count).joinToString(" · ")) },
        modifier = Modifier
            .testTag(ReminderLessonsTags.lesson(option.key))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}
