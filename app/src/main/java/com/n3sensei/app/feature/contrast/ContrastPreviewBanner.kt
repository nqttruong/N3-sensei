package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.R
import com.n3sensei.app.domain.contrast.ContrastPreview
import com.n3sensei.app.domain.repository.ContrastContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** True while the installed Contrast content comes from the debug preview pack (unreviewed drafts). */
class ContrastPreviewViewModel(content: ContrastContentRepository) : ViewModel() {
    val isPreview: StateFlow<Boolean> = content.observeGroups()
        .map { groups -> groups.any(ContrastPreview::isPreview) }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

object ContrastPreviewTags {
    const val BANNER = "contrast_preview_banner"
}

/** Every Contrast screen inside this frame shows the preview label above its content when needed. */
@Composable
fun ContrastPreviewFrame(viewModel: ContrastPreviewViewModel, showDetail: Boolean = false, content: @Composable (Modifier) -> Unit) {
    val preview by viewModel.isPreview.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (preview) ContrastPreviewBanner(showDetail = showDetail)
        content(Modifier.weight(1f))
    }
}

@Composable
fun ContrastPreviewBanner(modifier: Modifier = Modifier, showDetail: Boolean = true) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = if (showDetail) 8.dp else 4.dp)
            .testTag(ContrastPreviewTags.BANNER)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            stringResource(R.string.contrast_preview_banner),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.semantics { heading() },
        )
        if (showDetail) {
            Text(
                stringResource(R.string.contrast_preview_banner_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
