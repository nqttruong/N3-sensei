package com.n3sensei.app.feature.exam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FilterChipRow
import com.n3sensei.app.core.designsystem.component.FilterOption
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.MessageContent
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ExamPassage
import com.n3sensei.app.domain.exam.ItemOutcome
import com.n3sensei.app.domain.exam.ResultItem

object ExamReviewTags {
    fun item(position: Int) = "exam_review_item_$position"
}

@Composable
fun ExamReviewScreen(
    viewModel: ExamReviewViewModel,
    /** Question to open the list at (its place in the exam), if any. */
    startPosition: Int?,
    onOpenError: (errorId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, onRetry = viewModel::load) { data ->
        ExamReviewContent(data, startPosition, viewModel::selectFilter, onOpenError = { viewModel.openError(it, onOpenError) })
    }
}

@Composable
fun ExamReviewContent(
    state: ExamReviewUiState,
    startPosition: Int?,
    onSelectFilter: (ReviewFilter) -> Unit,
    onOpenError: (questionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startPosition?.let { p -> state.items.indexOfFirst { it.position == p } }?.coerceAtLeast(0) ?: 0,
    )
    Column(modifier.fillMaxSize()) {
        FilterChipRow(
            options = listOf(
                FilterOption(ReviewFilter.ALL, stringResource(R.string.exam_review_filter_all)),
                FilterOption(ReviewFilter.WRONG, stringResource(R.string.exam_review_filter_wrong)),
                FilterOption(ReviewFilter.BLANK, stringResource(R.string.exam_review_filter_blank)),
            ),
            selected = state.filter,
            onSelect = onSelectFilter,
            modifier = Modifier.padding(vertical = N3Spacing.s),
        )
        if (state.items.isEmpty()) {
            MessageContent(stringResource(R.string.exam_review_empty))
            return@Column
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = N3Spacing.screen, end = N3Spacing.screen, bottom = N3Spacing.l),
            verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
        ) {
            items(state.items, key = { it.position }) { item ->
                ReviewItemCard(item, item.question.passageId?.let(state.report.record.passages::get), onOpenError)
            }
        }
    }
}

@Composable
private fun ReviewItemCard(item: ResultItem, passage: ExamPassage?, onOpenError: (questionId: String) -> Unit) {
    val question = item.question
    N3Card(modifier = Modifier.testTag(ExamReviewTags.item(item.position))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            Text(
                stringResource(R.string.exam_result_item_row, item.position + 1, stringResource(item.skill.labelRes())),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            when (item.outcome) {
                ItemOutcome.CORRECT -> Tag(stringResource(R.string.exam_review_outcome_correct), tone = TagTone.Accent)
                ItemOutcome.WRONG -> Tag(stringResource(R.string.exam_review_outcome_wrong), tone = TagTone.Info)
                ItemOutcome.UNANSWERED -> Tag(stringResource(R.string.exam_review_outcome_blank))
            }
        }
        if (passage != null && (passage.text != null || passage.title != null)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(N3Spacing.m), verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                    passage.title?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    (passage.furigana ?: passage.text)?.let { FuriganaText(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        FuriganaText(question.contentFurigana ?: question.content, style = MaterialTheme.typography.titleMedium)
        when (question.format) {
            AnswerFormat.CHOICE -> question.options.forEachIndexed { index, option ->
                ReviewOption(index, option, chosen = item.answer?.selectedIndex == index, right = question.answerIndex == index)
            }
            AnswerFormat.TEXT -> {
                item.answer?.textAnswer?.let { Text(stringResource(R.string.exam_review_your_text, it)) }
                Text(stringResource(R.string.exam_review_accepted, question.acceptedAnswers.joinToString(" / ")), fontWeight = FontWeight.SemiBold)
            }
        }
        question.explanation?.takeIf { it.isNotBlank() }?.let {
            Caption(stringResource(R.string.exam_review_explanation))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (item.outcome != ItemOutcome.CORRECT) {
            TextButton(onClick = { onOpenError(question.id) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.exam_review_open_error))
            }
        }
    }
}

/** An option: the right one outlined in the accent, a wrong pick outlined in the error colour; labels say which. */
@Composable
private fun ReviewOption(index: Int, option: String, chosen: Boolean, right: Boolean) {
    val colors = MaterialTheme.colorScheme
    val border = when {
        right -> BorderStroke(2.dp, colors.primary)
        chosen -> BorderStroke(2.dp, colors.error)
        else -> BorderStroke(1.dp, colors.outlineVariant)
    }
    Surface(shape = MaterialTheme.shapes.medium, border = border, color = if (right) colors.primaryContainer else colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = N3Spacing.m, vertical = N3Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(N3Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                FuriganaText(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            val labels = listOfNotNull(
                stringResource(R.string.exam_review_correct_choice).takeIf { right },
                stringResource(R.string.exam_review_your_choice).takeIf { chosen },
            )
            if (labels.isNotEmpty()) Caption(labels.joinToString(" · "))
        }
    }
}
