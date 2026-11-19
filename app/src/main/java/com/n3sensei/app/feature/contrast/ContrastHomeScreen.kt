package com.n3sensei.app.feature.contrast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.contrast.ContrastContentType

@Composable
fun ContrastHomeScreen(
    viewModel: ContrastHomeViewModel,
    onOpenGroup: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartPractice: () -> Unit,
    onResumePractice: () -> Unit,
    onOpenErrors: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCategory: (ContrastContentType) -> Unit = { onOpenLibrary() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastHomeContent(
        state, onOpenGroup, onOpenLibrary, viewModel::retry, modifier, onStartPractice, onResumePractice,
        InsightLinks(onOpenErrors, onOpenReview, onOpenProgress), onOpenCategory,
    )
}

/** M7 entries from Home. */
data class InsightLinks(val errors: () -> Unit = {}, val review: () -> Unit = {}, val progress: () -> Unit = {})

@Composable
fun ContrastHomeContent(
    state: UiState<ContrastHomeUiState>,
    onOpenGroup: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onStartPractice: () -> Unit = {},
    onResumePractice: () -> Unit = {},
    insights: InsightLinks = InsightLinks(),
    onOpenCategory: (ContrastContentType) -> Unit = { onOpenLibrary() },
) {
    Column(modifier.fillMaxSize().testTag(ContrastTags.HOME)) {
        UiStateContent(state, emptyMessage = stringResource(R.string.contrast_empty), onRetry = onRetry) { data ->
            HomeSections(data, onOpenGroup, onOpenLibrary, onStartPractice, onResumePractice, insights, onOpenCategory)
        }
    }
}

@Composable
private fun HomeSections(
    data: ContrastHomeUiState,
    onOpenGroup: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartPractice: () -> Unit,
    onResumePractice: () -> Unit,
    insights: InsightLinks,
    onOpenCategory: (ContrastContentType) -> Unit,
) {
    val practice = ContrastTones.practice()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Hero.
        GradientHeader(practice) {
            Text(stringResource(R.string.contrast_ui_home_kicker), style = ContrastText.label.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.85f))
            Text(stringResource(R.string.contrast_ui_home_title), style = ContrastText.screenTitle, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.contrast_home_intro), style = ContrastText.body, color = Color.White.copy(alpha = 0.92f))
        }
        // Figures: 2 × 2, compact.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FigureTile(data.available.toString(), stringResource(R.string.contrast_overview_available), practice, Modifier.weight(1f))
            FigureTile(data.learning.toString(), stringResource(R.string.contrast_overview_learning), ContrastTones.category(ContrastContentType.GRAMMAR), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FigureTile(data.mastered.toString(), stringResource(R.string.contrast_overview_mastered), ContrastTones.correct(), Modifier.weight(1f))
            FigureTile(data.due.toString(), stringResource(R.string.contrast_overview_due), ContrastTones.review(), Modifier.weight(1f))
        }

        // 2. Continue learning / the main action.
        data.activeRemaining?.let { remaining ->
            GradientButton(stringResource(R.string.contrast_continue_session, remaining), practice, onResumePractice, Modifier.testTag(ContrastTags.RESUME))
        }
        GradientButton(
            stringResource(R.string.contrast_start_practice),
            if (data.activeRemaining == null) practice else ContrastTones.category(ContrastContentType.GRAMMAR),
            onStartPractice,
            Modifier.testTag(ContrastTags.START),
        )
        data.continueGroup?.let { row ->
            SectionTitle(stringResource(R.string.contrast_continue_title), practice)
            ContrastGroupCard(row, onClick = { onOpenGroup(row.group.id) })
        }

        // 3. Explore by category.
        SectionTitle(stringResource(R.string.contrast_ui_explore_categories), practice)
        ContrastContentType.entries.forEach { type ->
            CategoryTile(type, data.byType[type] ?: 0) { onOpenCategory(type) }
        }

        // 4. Smart review.
        val review = ContrastTones.review()
        ContrastCard(tone = review, onClick = insights.review, modifier = Modifier.testTag(InsightTags.OPEN_REVIEW)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing(if (data.learning + data.mastered == 0) 0f else data.due.toFloat() / (data.learning + data.mastered), review, size = 56.dp, stroke = 6.dp) {
                    Text(data.due.toString(), style = ContrastText.section, color = review.accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.contrast_open_review), style = ContrastText.section, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.contrast_ui_due_today, data.due), style = ContrastText.bodySecondary)
                }
            }
        }
        if (data.dueGroups.isNotEmpty()) {
            SectionTitle(stringResource(R.string.contrast_due_title), review)
            Text(stringResource(R.string.contrast_due_note), style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            data.dueGroups.forEach { row -> ContrastGroupCard(row, onClick = { onOpenGroup(row.group.id) }) }
        }

        // 5. Progress and errors.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val correct = ContrastTones.correct()
            ContrastCard(tone = correct, onClick = insights.progress, modifier = Modifier.weight(1f).testTag(InsightTags.OPEN_PROGRESS)) {
                ProgressRing(if (data.available == 0) 0f else data.mastered.toFloat() / data.available, correct, size = 52.dp, stroke = 6.dp)
                Text(stringResource(R.string.contrast_open_progress), style = ContrastText.section.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.contrast_ui_mastered_of, data.mastered, data.available), style = ContrastText.caption)
            }
            val incorrect = ContrastTones.incorrect()
            ContrastCard(tone = incorrect, onClick = insights.errors, modifier = Modifier.weight(1f).testTag(InsightTags.OPEN_ERRORS)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(incorrect.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text("誤", style = ContrastText.section.copy(fontSize = 24.sp).japanese(), color = incorrect.accent)
                }
                Text(stringResource(R.string.contrast_open_errors), style = ContrastText.section.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.contrast_ui_confusions_count, data.confusions.size), style = ContrastText.caption)
            }
        }
        SectionTitle(stringResource(R.string.contrast_confused_title), ContrastTones.incorrect())
        if (data.confusions.isEmpty()) {
            Text(stringResource(R.string.contrast_confused_none), style = ContrastText.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            data.confusions.forEach { c ->
                ContrastCard(onClick = { onOpenGroup(c.groupId) }) {
                    Text(c.groupTitle, style = ContrastText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.contrast_confused_row, c.expectedLabel, c.chosenLabel, c.count), style = ContrastText.answer)
                }
            }
        }
        GradientButton(stringResource(R.string.contrast_explore), ContrastTones.category(ContrastContentType.VOCABULARY), onOpenLibrary, Modifier.testTag(ContrastTags.EXPLORE))
    }
}

/** A category entry: its glyph on its gradient, name and number of groups. Opens the library filtered. */
@Composable
private fun CategoryTile(type: ContrastContentType, count: Int, onClick: () -> Unit) {
    val tone = ContrastTones.category(type)
    ContrastCard(tone = tone, onClick = onClick, modifier = Modifier.testTag(ContrastTags.category(type))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(tone.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text(categoryGlyph(type), style = ContrastText.section.copy(fontSize = 28.sp, lineHeight = 34.sp).japanese(), color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(typeLabel(type), style = ContrastText.section, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.contrast_ui_groups_count, count), style = ContrastText.bodySecondary, color = tone.accent)
            }
            Text("›", style = ContrastText.screenTitle, color = tone.accent)
        }
    }
}
