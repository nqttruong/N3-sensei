package com.n3sensei.app.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.core.ui.state.MessageContent

/**
 * Honest placeholder for a screen whose feature is not built yet. Shows no actions, so the
 * user is never offered a button that does nothing.
 */
@Composable
fun FeaturePendingContent(
    @StringRes messageRes: Int,
    modifier: Modifier = Modifier,
) {
    MessageContent(
        title = stringResource(R.string.common_feature_pending_title),
        message = stringResource(messageRes),
        modifier = modifier,
    )
}
