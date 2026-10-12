package com.n3sensei.app.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.integrity.DatabaseResetNotice
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.feature.onboarding.OnboardingScreen

/** Applies user preferences, then shows onboarding (first run) or the main app. */
@Composable
fun N3SenseiRoot(viewModel: MainViewModel) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val prefs = preferences
    val darkTheme = when (prefs?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, null -> isSystemInDarkTheme()
    }
    N3SenseiTheme(darkTheme = darkTheme) {
        when {
            // Preferences load from disk in milliseconds; show a plain surface meanwhile.
            prefs == null -> Surface(Modifier.fillMaxSize()) {}
            !prefs.onboardingCompleted -> OnboardingScreen(onFinish = viewModel::completeOnboarding)
            else -> CompositionLocalProvider(LocalShowFurigana provides prefs.showFurigana) {
                N3SenseiApp()
            }
        }
        val resetNotice by viewModel.databaseResetNotice.collectAsStateWithLifecycle()
        resetNotice?.let { DatabaseResetDialog(it, viewModel::dismissDatabaseResetNotice) }
    }
}

/** The database file was corrupt and recreated empty: say so, and how to get the data back. */
@Composable
private fun DatabaseResetDialog(notice: DatabaseResetNotice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.database_reset_title)) },
        text = {
            Text(
                stringResource(R.string.database_reset_body) +
                    (notice.keptCopy?.let { "\n\n" + stringResource(R.string.database_reset_copy, it) } ?: ""),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.database_reset_ok)) } },
    )
}
