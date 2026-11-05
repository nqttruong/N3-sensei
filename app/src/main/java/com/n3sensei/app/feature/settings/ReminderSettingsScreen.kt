package com.n3sensei.app.feature.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.reminder.ReminderContent
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderOutcome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsScreen(
    viewModel: ReminderSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
    val testOutcome by viewModel.testOutcome.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose {}
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermission()
        if (granted) viewModel.setEnabled(true)
    }
    val requestForErrorReviews = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermission()
        if (granted) viewModel.setErrorReviews(true)
    }
    val openSystemSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val current = settings ?: return LoadingContent(modifier)

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.reminder_enable)) },
            supportingContent = { Text(stringResource(R.string.reminder_enable_description)) },
            trailingContent = { Switch(checked = current.enabled, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = current.enabled, role = Role.Switch) { on ->
                when {
                    !on -> viewModel.setEnabled(false)
                    allowed -> viewModel.setEnabled(true)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> openSystemSettings()
                }
            },
        )
        if (!allowed) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBanner(
                    stringResource(if (current.enabled) R.string.reminder_blocked_while_enabled else R.string.reminder_permission_denied),
                )
                OutlinedButton(onClick = openSystemSettings) { Text(stringResource(R.string.reminder_open_system_settings)) }
            }
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.reminder_error_reviews)) },
            supportingContent = { Text(stringResource(R.string.reminder_error_reviews_description)) },
            trailingContent = { Switch(checked = current.errorReviews, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = current.errorReviews, role = Role.Switch) { on ->
                when {
                    !on -> viewModel.setErrorReviews(false)
                    allowed -> viewModel.setErrorReviews(true)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        requestForErrorReviews.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> openSystemSettings()
                }
            },
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))

        SectionHeader(stringResource(R.string.reminder_window), Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.reminder_window_from))
            HourPicker(current.startHour, viewModel::setStartHour)
            Text(stringResource(R.string.reminder_window_to))
            HourPicker(current.endHour, viewModel::setEndHour)
        }
        Text(
            stringResource(R.string.reminder_window_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 4.dp),
        )

        SectionHeader(stringResource(R.string.reminder_frequency), Modifier.padding(horizontal = 16.dp))
        Column(Modifier.selectableGroup()) {
            // A longer period chosen in an earlier version stays listed while it is selected.
            (ReminderFrequency.SELECTABLE + current.frequency).distinct().sortedBy { it.minutes }.forEach { frequency ->
                RadioRow(frequencyLabel(frequency), current.frequency == frequency) { viewModel.setFrequency(frequency) }
            }
        }
        Text(
            stringResource(R.string.reminder_frequency_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 4.dp),
        )

        SectionHeader(stringResource(R.string.reminder_max_per_day), Modifier.padding(horizontal = 16.dp))
        ChoiceChips(
            options = ReminderSettings.MAX_PER_DAY_CHOICES,
            selected = current.maxPerDay,
            label = { if (it == 0) stringResource(R.string.reminder_max_unlimited) else stringResource(R.string.reminder_max_value, it) },
            onSelect = viewModel::setMaxPerDay,
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))

        SectionHeader(stringResource(R.string.reminder_scope), Modifier.padding(horizontal = 16.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReminderContent.entries.forEach { kind ->
                FilterChip(
                    selected = kind in current.content,
                    onClick = { viewModel.toggleContent(kind) },
                    label = {
                        Text(
                            stringResource(
                                when (kind) {
                                    ReminderContent.VOCABULARY -> R.string.totals_vocabulary
                                    ReminderContent.KANJI -> R.string.totals_kanji
                                    ReminderContent.GRAMMAR -> R.string.totals_grammar
                                },
                            ),
                        )
                    },
                )
            }
        }
        SwitchRow(
            stringResource(R.string.reminder_review_only),
            stringResource(R.string.reminder_review_only_description),
            current.reviewOnly,
            viewModel::setReviewOnly,
        )
        HorizontalDivider()

        SectionHeader(stringResource(R.string.reminder_content), Modifier.padding(horizontal = 16.dp))
        SwitchRow(stringResource(R.string.reminder_show_reading), null, current.showReading, viewModel::setShowReading)
        SwitchRow(
            stringResource(R.string.reminder_show_meaning),
            stringResource(R.string.reminder_show_meaning_description),
            current.showMeaning,
            viewModel::setShowMeaning,
        )
        HorizontalDivider()

        SectionHeader(stringResource(R.string.reminder_lock_screen), Modifier.padding(horizontal = 16.dp))
        Column(Modifier.selectableGroup()) {
            RadioRow(stringResource(R.string.reminder_lock_show_all), current.lockScreen == LockScreenPrivacy.SHOW_ALL) {
                viewModel.setLockScreen(LockScreenPrivacy.SHOW_ALL)
            }
            RadioRow(
                stringResource(R.string.reminder_lock_hide_meaning),
                current.lockScreen == LockScreenPrivacy.HIDE_MEANING,
                stringResource(R.string.reminder_lock_hide_meaning_description),
            ) { viewModel.setLockScreen(LockScreenPrivacy.HIDE_MEANING) }
            RadioRow(
                stringResource(R.string.reminder_lock_hide_all),
                current.lockScreen == LockScreenPrivacy.HIDE_ALL,
                stringResource(R.string.reminder_lock_hide_all_description),
            ) { viewModel.setLockScreen(LockScreenPrivacy.HIDE_ALL) }
        }
        HorizontalDivider()

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::sendTest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminder_send_test))
            }
            testOutcome?.let { outcome ->
                Text(
                    stringResource(
                        when (outcome) {
                            ReminderOutcome.SHOWN -> R.string.reminder_test_shown
                            ReminderOutcome.NOTIFICATIONS_BLOCKED -> R.string.reminder_test_blocked
                            ReminderOutcome.NOTHING_TO_SHOW -> R.string.reminder_test_nothing
                            else -> R.string.reminder_test_nothing
                        },
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val widgetManager = remember { AppWidgetManager.getInstance(context) }
            if (widgetManager.isRequestPinAppWidgetSupported) {
                OutlinedButton(
                    onClick = {
                        // The widget receiver is resolved by name: features must not depend on the app layer.
                        widgetManager.requestPinAppWidget(ComponentName(context.packageName, WIDGET_RECEIVER), null, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.reminder_add_widget)) }
            }
            InfoBanner(stringResource(R.string.reminder_word_rule))
            InfoBanner(stringResource(R.string.reminder_limits))
            InfoBanner(stringResource(R.string.reminder_lock_screen_widget_note))
        }
    }
}

private const val WIDGET_RECEIVER = "com.n3sensei.app.app.widget.StudyWordWidgetReceiver"

@Composable
fun frequencyLabel(frequency: ReminderFrequency): String = when {
    frequency == ReminderFrequency.ONCE_A_DAY -> stringResource(R.string.reminder_frequency_daily)
    frequency.minutes < 60 -> stringResource(R.string.reminder_frequency_minutes, frequency.minutes)
    else -> stringResource(R.string.reminder_frequency_hours, frequency.minutes / 60)
}

/** Single choice among a few values, as chips (fits the daily-limit row on one or two lines). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun HourPicker(hour: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(R.string.reminder_hour, hour)) }
        // Bounded height: unbounded, the menu was laid out for the whole edge-to-edge window,
        // the system clamped it between the status and navigation bars, and the last hours
        // (23:00) stayed below the screen edge, unreachable by scrolling.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            (0..23).forEach { h ->
                DropdownMenuItem(text = { Text(stringResource(R.string.reminder_hour, h)) }, onClick = { onSelect(h); expanded = false })
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
private fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
