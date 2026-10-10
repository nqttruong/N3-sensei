package com.n3sensei.app.core.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme

/**
 * Renders the non-success states of [state] consistently across the app and delegates
 * [UiState.Success] to [content].
 *
 * @param onRetry shown as a retry button on error; pass null when retrying is not possible.
 */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    emptyMessage: String = stringResource(R.string.common_empty_generic),
    notFoundMessage: String = stringResource(R.string.common_not_found),
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> LoadingContent(modifier)
        UiState.Empty -> MessageContent(message = emptyMessage, modifier = modifier)
        UiState.NotFound -> MessageContent(message = notFoundMessage, modifier = modifier)
        is UiState.Error -> ErrorContent(onRetry = onRetry, modifier = modifier)
        is UiState.Success -> content(state.data)
    }
}

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.common_loading)
    CenteredColumn(modifier) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
fun MessageContent(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    CenteredColumn(modifier) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorContent(
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.common_error_generic),
) {
    CenteredColumn(modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

@Composable
private fun CenteredColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Preview(showBackground = true)
@Composable
private fun LoadingPreview() = N3SenseiTheme { UiStateContent<Unit>(UiState.Loading) {} }

@Preview(showBackground = true)
@Composable
private fun EmptyPreview() = N3SenseiTheme { UiStateContent<Unit>(UiState.Empty) {} }

@Preview(showBackground = true)
@Composable
private fun ErrorPreview() = N3SenseiTheme {
    UiStateContent<Unit>(UiState.Error(IllegalStateException()), onRetry = {}) {}
}
