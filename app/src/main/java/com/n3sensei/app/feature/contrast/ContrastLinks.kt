package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.domain.contrast.ContrastGroupLink
import com.n3sensei.app.domain.contrast.ContrastSourceIds
import com.n3sensei.app.domain.repository.ContrastContentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Entry points from a course lesson or a grammar / vocabulary / kanji page to the published
 * contrast groups that contain one of its items. Read-only: it never creates a session, an
 * attempt or a schedule, and shows nothing when no reviewed pack is installed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContrastLinksViewModel(private val content: ContrastContentRepository) : ViewModel() {
    private val sources = MutableStateFlow(ContrastSourceIds())

    /** Empty while loading, without a reviewed pack, or if the lookup fails (the section is optional). */
    val links: StateFlow<List<ContrastGroupLink>> = sources
        .flatMapLatest { content.observeGroupsForSources(it) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun show(ids: ContrastSourceIds) {
        sources.value = ids
    }
}

object ContrastLinkTags {
    const val SECTION = "contrast_links"
    fun link(groupId: String) = "contrast_link_$groupId"
}

@Composable
fun ContrastLinksSection(
    viewModel: ContrastLinksViewModel,
    sources: ContrastSourceIds,
    title: String,
    onOpenGroup: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(sources) { viewModel.show(sources) }
    val links by viewModel.links.collectAsStateWithLifecycle()
    ContrastLinksContent(links, title, onOpenGroup, modifier)
}

@Composable
fun ContrastLinksContent(
    links: List<ContrastGroupLink>,
    title: String,
    onOpenGroup: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    // One compact line that scrolls sideways: a lesson can have several groups and its header must
    // not push the lesson's own lists off the screen.
    Column(modifier.fillMaxWidth().testTag(ContrastLinkTags.SECTION), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { link ->
                AssistChip(
                    onClick = { onOpenGroup(link.id) },
                    label = { Text(japaneseText(link.title), maxLines = 1) },
                    modifier = Modifier.testTag(ContrastLinkTags.link(link.id)),
                )
            }
        }
    }
}
