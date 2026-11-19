package com.n3sensei.app.feature.contrast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.LocalReduceMotion
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import kotlin.math.roundToInt
import kotlin.random.Random

object SummaryTags {
    const val SCREEN = "contrast_summary"
    const val HOME = "contrast_summary_home"
    const val LIBRARY = "contrast_summary_library"
}

@Composable
fun ContrastSessionSummaryScreen(
    viewModel: ContrastSessionSummaryViewModel,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContrastSessionSummaryContent(state, onOpenHome, onOpenLibrary, viewModel::retry, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContrastSessionSummaryContent(
    state: UiState<ContrastSessionSummary>,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiStateContent(state, modifier, onRetry = onRetry) { data ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag(SummaryTags.SCREEN),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val value = data.accuracy.value
                val headerTone = when {
                    value == null -> ContrastTones.practice()
                    value >= 0.8 -> ContrastTones.correct()
                    else -> ContrastTones.practice()
                }
                // A headline that fits the result (no points, XP or streaks).
                GradientHeader(headerTone) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProgressRing(
                            (value ?: 0.0).toFloat(),
                            Tone(Color.White, Color.Transparent, Color.White, emptyList()),
                            size = 84.dp,
                            stroke = 9.dp,
                        ) {
                            Text(if (value == null) "–" else "${(value * 100).roundToInt()}%", style = ContrastText.section.copy(fontSize = 22.sp), color = Color.White)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(
                                    when {
                                        value == null -> R.string.contrast_ui_summary_title_none
                                        value >= 0.8 -> R.string.contrast_ui_summary_title_great
                                        value >= 0.5 -> R.string.contrast_ui_summary_title_good
                                        else -> R.string.contrast_ui_summary_title_keep
                                    },
                                ),
                                style = ContrastText.screenTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(stringResource(R.string.contrast_summary_handled, data.handled), style = ContrastText.body, color = Color.White.copy(alpha = 0.92f))
                        }
                    }
                    Text(
                        if (value == null) {
                            stringResource(R.string.contrast_summary_accuracy_none)
                        } else {
                            stringResource(R.string.contrast_summary_accuracy, (value * 100).roundToInt(), data.accuracy.correct, data.accuracy.answered)
                        },
                        style = ContrastText.bodySecondary,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Count(R.string.contrast_summary_correct, data.correct, ContrastTones.correct(), Modifier.weight(1f))
                    Count(R.string.contrast_summary_incorrect, data.incorrect, ContrastTones.incorrect(), Modifier.weight(1f))
                    Count(R.string.contrast_summary_skipped, data.skipped, ContrastTones.neutral(), Modifier.weight(1f))
                }
                if (data.pending > 0 || data.unavailable > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (data.pending > 0) Count(R.string.contrast_summary_pending, data.pending, ContrastTones.neutral(), Modifier.weight(1f))
                        if (data.unavailable > 0) Count(R.string.contrast_summary_unavailable, data.unavailable, ContrastTones.neutral(), Modifier.weight(1f))
                    }
                }
                data.groups.forEach { group ->
                    ContrastCard(tone = ContrastTones.review()) {
                        Text(japaneseText(group.title), style = ContrastText.section.copy(fontSize = 18.sp).japanese(), modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.contrast_progress_label, masteryLabel(group.mastery)), style = ContrastText.body)
                        group.nextDueAt?.let { Text(stringResource(R.string.contrast_summary_next_due, formatDate(it)), style = ContrastText.body) }
                    }
                }
                GradientButton(stringResource(R.string.contrast_practice_back_home), ContrastTones.practice(), onOpenHome, Modifier.testTag(SummaryTags.HOME))
                OutlinedButton(
                    onClick = onOpenLibrary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(SummaryTags.LIBRARY),
                ) { Text(stringResource(R.string.contrast_summary_library), style = ContrastText.label.copy(fontSize = 17.sp)) }
                if (data.answers.isNotEmpty()) {
                    ExpandableCard(
                        stringResource(R.string.contrast_summary_answers),
                        ContrastTones.practice(),
                        initiallyExpanded = true,
                        subtitle = stringResource(R.string.contrast_ui_answers_count, data.answers.size),
                    ) {
                        data.answers.forEach { answer ->
                            val tone = when (answer.outcome) {
                                AnswerOutcome.CORRECT -> ContrastTones.correct()
                                AnswerOutcome.INCORRECT -> ContrastTones.incorrect()
                                AnswerOutcome.SKIPPED -> ContrastTones.neutral()
                            }
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tone.container).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TonePill(qtype(answer.type), ContrastTones.practice())
                                    TonePill(outcomeLabel(answer.outcome), tone)
                                }
                                Text(japaneseText(answer.groupTitle), style = ContrastText.caption.japanese(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                answer.chosenText?.let { Text(japaneseText(stringResource(R.string.contrast_summary_answer_chosen, it)), style = ContrastText.body.japanese()) }
                                Text(japaneseText(stringResource(R.string.contrast_summary_answer_correct, answer.correctText)), style = ContrastText.body.japanese())
                            }
                        }
                    }
                }
            }
            // One short burst of confetti when the session is done well; never with reduced motion, never over the results for long.
            if ((data.accuracy.value ?: 0.0) >= 0.8 && !LocalReduceMotion.current) Confetti(Modifier.matchParentSize())
        }
    }
}

@Composable
private fun Count(labelRes: Int, value: Int, tone: Tone, modifier: Modifier = Modifier) {
    FigureTile(value.toString(), stringResource(labelRes), tone, modifier)
}

/** ~40 small pieces falling once over 1.4 s from the top, then gone (drawn only; no touch, no semantics). */
@Composable
private fun Confetti(modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1400, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    val colors = listOf(Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF22C55E))
    val pieces = remember { List(40) { i -> Triple(Random(i).nextFloat(), Random(i * 7 + 3).nextFloat(), colors[i % colors.size]) } }
    Canvas(modifier) {
        val t = progress.value
        pieces.forEachIndexed { i, (x, speed, color) ->
            val y = (t * (0.35f + speed * 0.4f)) * size.height
            val alpha = (1f - t).coerceIn(0f, 1f)
            rotate(degrees = t * 360f * (if (i % 2 == 0) 1 else -1), pivot = Offset(x * size.width, y)) {
                drawRect(color.copy(alpha = alpha), topLeft = Offset(x * size.width, y), size = Size(8.dp.toPx(), 14.dp.toPx()))
            }
        }
    }
}

@Composable
private fun outcomeLabel(outcome: AnswerOutcome) = stringResource(
    when (outcome) {
        AnswerOutcome.CORRECT -> R.string.contrast_summary_correct
        AnswerOutcome.INCORRECT -> R.string.contrast_summary_incorrect
        AnswerOutcome.SKIPPED -> R.string.contrast_summary_skipped
    },
)

@Composable
private fun qtype(type: ContrastQuestionType) = stringResource(
    when (type) {
        ContrastQuestionType.MULTIPLE_CHOICE -> R.string.contrast_qtype_mc
        ContrastQuestionType.ERROR_DETECTION -> R.string.contrast_qtype_ed
        ContrastQuestionType.FILL_IN_THE_BLANK -> R.string.contrast_qtype_fill
        ContrastQuestionType.CONTEXT_SELECTION -> R.string.contrast_qtype_context
    },
)
