package com.n3sensei.app.feature.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.domain.widget.WidgetMode
import com.n3sensei.app.domain.widget.WidgetTap

@Composable
fun WidgetSettingsScreen(viewModel: WidgetSettingsViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lessonCount by viewModel.lessonCount.collectAsStateWithLifecycle()
    val current = settings ?: return LoadingContent(modifier)
    val context = LocalContext.current

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.widget_settings_content), Modifier.padding(horizontal = 16.dp))
        Column(Modifier.selectableGroup()) {
            WidgetMode.entries.forEach { mode ->
                Choice(modeLabel(mode), modeDescription(mode), current.mode == mode) { viewModel.setMode(mode) }
            }
        }
        if (current.mode == WidgetMode.LESSON) {
            LessonPicker(current.lesson, lessonCount, viewModel::setLesson)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))

        SectionHeader(stringResource(R.string.widget_settings_show), Modifier.padding(horizontal = 16.dp))
        Toggle(stringResource(R.string.widget_settings_furigana), current.showFurigana, viewModel::setShowFurigana)
        Toggle(stringResource(R.string.widget_settings_meaning), current.showMeaning, viewModel::setShowMeaning)
        HorizontalDivider()

        SectionHeader(stringResource(R.string.widget_settings_tap), Modifier.padding(horizontal = 16.dp))
        Column(Modifier.selectableGroup()) {
            Choice(stringResource(R.string.widget_tap_open), null, current.tap == WidgetTap.OPEN) { viewModel.setTap(WidgetTap.OPEN) }
            Choice(stringResource(R.string.widget_tap_next), null, current.tap == WidgetTap.NEXT) { viewModel.setTap(WidgetTap.NEXT) }
        }
        HorizontalDivider()

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val manager = remember { AppWidgetManager.getInstance(context) }
            if (manager.isRequestPinAppWidgetSupported) {
                // Receivers are named, not imported: features must not depend on the app layer.
                OutlinedButton(onClick = { manager.requestPinAppWidget(ComponentName(context.packageName, SMALL_RECEIVER), null, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.widget_add_small))
                }
                OutlinedButton(onClick = { manager.requestPinAppWidget(ComponentName(context.packageName, LARGE_RECEIVER), null, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.widget_add_large))
                }
            }
            InfoBanner(stringResource(R.string.widget_how_to_add))
            Caption(stringResource(R.string.widget_update_note))
        }
    }
}

private const val SMALL_RECEIVER = "com.n3sensei.app.app.widget.SmallStudyWordWidgetReceiver"
private const val LARGE_RECEIVER = "com.n3sensei.app.app.widget.StudyWordWidgetReceiver"

@Composable
private fun modeLabel(mode: WidgetMode): String = stringResource(
    when (mode) {
        WidgetMode.RANDOM -> R.string.widget_mode_random
        WidgetMode.REVIEW -> R.string.widget_mode_review
        WidgetMode.KANJI -> R.string.totals_kanji
        WidgetMode.VOCABULARY -> R.string.totals_vocabulary
        WidgetMode.GRAMMAR -> R.string.totals_grammar
        WidgetMode.LESSON -> R.string.widget_mode_lesson
    },
)

@Composable
private fun modeDescription(mode: WidgetMode): String? = when (mode) {
    WidgetMode.RANDOM -> stringResource(R.string.widget_mode_random_description)
    WidgetMode.REVIEW -> stringResource(R.string.widget_mode_review_description)
    WidgetMode.LESSON -> stringResource(R.string.widget_mode_lesson_description)
    else -> null
}

@Composable
private fun LessonPicker(lesson: Int, count: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { expanded = true }, enabled = count > 0) {
            Text(stringResource(R.string.widget_lesson_button, stringResource(R.string.course_lesson_title, lesson)))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            (1..count).forEach { n ->
                DropdownMenuItem(text = { Text(stringResource(R.string.course_lesson_title, n)) }, onClick = { onSelect(n); expanded = false })
            }
        }
    }
    if (count in 1 until lesson) {
        Caption(stringResource(R.string.widget_lesson_missing), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
private fun Choice(label: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
