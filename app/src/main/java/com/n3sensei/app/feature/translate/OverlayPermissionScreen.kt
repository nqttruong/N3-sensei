package com.n3sensei.app.feature.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R

/**
 * Asks for "Hiển thị trên ứng dụng khác" before the first popup: why it is needed, a button to
 * the system screen where the learner grants it, and a way out. [stillDenied] after coming back
 * from that screen without granting it.
 */
@Composable
fun OverlayPermissionScreen(
    stillDenied: Boolean,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .testTag(QuickTranslateTags.PERMISSION),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.quick_translate_permission_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.quick_translate_permission_body), style = MaterialTheme.typography.bodyMedium)
            if (stillDenied) {
                Text(
                    stringResource(R.string.quick_translate_permission_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onLater, modifier = Modifier.testTag(QuickTranslateTags.LATER)) {
                    Text(stringResource(R.string.quick_translate_permission_later))
                }
                Button(onClick = onOpenSettings, modifier = Modifier.testTag(QuickTranslateTags.OPEN_SETTINGS)) {
                    Text(stringResource(R.string.quick_translate_permission_open))
                }
            }
        }
    }
}
