package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.component.N3Card
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.mistakes.ErrorCategory
import com.n3sensei.app.domain.mistakes.Evidence
import com.n3sensei.app.domain.mistakes.Knowledge
import com.n3sensei.app.domain.mistakes.LinkBasis
import com.n3sensei.app.domain.mistakes.MistakeAnalysis
import com.n3sensei.app.domain.mistakes.Recommendation
import com.n3sensei.app.domain.mistakes.ReviewPriority
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.practice.PracticeItem

/** Where the analysis can send the learner (wired to the existing screens by the navigation). */
data class MistakeActions(
    val onOpenItem: (ItemRef) -> Unit,
    val onOpenLesson: (Int) -> Unit,
    val onPracticeWords: (List<ItemRef>) -> Unit,
)

object MistakeTags {
    fun card(questionId: String) = "mistake_card_$questionId"
}

@StringRes
internal fun ErrorCategory.labelRes(): Int = when (this) {
    ErrorCategory.VOCABULARY_GAP -> R.string.mistake_category_vocabulary_gap
    ErrorCategory.KANJI_CONFUSION -> R.string.mistake_category_kanji_confusion
    ErrorCategory.GRAMMAR_CONFUSION -> R.string.mistake_category_grammar_confusion
    ErrorCategory.READING_MISUNDERSTANDING -> R.string.mistake_category_reading
    ErrorCategory.LISTENING_MISUNDERSTANDING -> R.string.mistake_category_listening
    ErrorCategory.CARELESS_MISTAKE -> R.string.mistake_category_careless
    ErrorCategory.UNKNOWN -> R.string.mistake_category_unknown
}

@StringRes
internal fun ItemType.mistakeLabelRes(): Int = when (this) {
    ItemType.VOCABULARY -> R.string.mistake_item_vocabulary
    ItemType.KANJI -> R.string.mistake_item_kanji
    ItemType.GRAMMAR -> R.string.mistake_item_grammar
}

/** "経験 (kinh nghiệm)", "経 · KINH", "〜ために (để)"; the id when the item is gone. */
internal fun Knowledge.title(): String = when (val item = item) {
    is PracticeItem.VocabularyItem -> "${item.vocabulary.expression} (${item.vocabulary.meaningVi})"
    is PracticeItem.KanjiItem -> listOfNotNull(item.kanji.character, item.kanji.hanViet).joinToString(" · ")
    is PracticeItem.GrammarItem -> "${item.grammar.pattern} (${item.grammar.meaningVi})"
    null -> ref.id
}

@Composable
private fun evidenceText(evidence: Evidence): String = when (evidence) {
    is Evidence.TaskType -> stringResource(R.string.mistake_evidence_task, stringResource(evidence.type.labelRes()))
    is Evidence.LinkedKnowledge -> stringResource(R.string.mistake_evidence_linked, stringResource(evidence.type.mistakeLabelRes()))
    is Evidence.Section -> stringResource(R.string.mistake_evidence_section, stringResource(evidence.section.labelRes()))
    Evidence.LeftBlank -> stringResource(R.string.mistake_evidence_blank)
    is Evidence.RightBefore -> stringResource(R.string.mistake_evidence_right_before, evidence.times)
    Evidence.NotEnoughData -> stringResource(R.string.mistake_evidence_not_enough)
}

/** The full analysis of one missed question: answers, explanation, category with its evidence, knowledge, fixes, lessons, repeats. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MistakeAnalysisCard(analysis: MistakeAnalysis, actions: MistakeActions, modifier: Modifier = Modifier) {
    val q = analysis.question
    val o = analysis.occurrence
    N3Card(modifier = modifier.testTag(MistakeTags.card(q.id)), verticalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            Text(
                stringResource(R.string.exam_result_item_row, o.position + 1, stringResource(o.skill.labelRes())),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            when (analysis.repeat.priority) {
                ReviewPriority.HIGH -> Tag(stringResource(R.string.mistake_priority_high), tone = TagTone.Info)
                ReviewPriority.RAISED -> Tag(stringResource(R.string.mistake_priority_raised))
                ReviewPriority.NORMAL -> Unit
            }
        }
        FuriganaText(q.contentFurigana ?: q.content, style = MaterialTheme.typography.titleMedium)
        Text(analysis.givenAnswer?.let { stringResource(R.string.mistake_your_answer, it) } ?: stringResource(R.string.mistake_blank_answer))
        Text(stringResource(R.string.mistake_correct_answer, analysis.correctAnswer), fontWeight = FontWeight.SemiBold)
        Caption(stringResource(R.string.mistake_explanation))
        Text(analysis.explanation ?: stringResource(R.string.mistake_no_explanation), style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider(Modifier.padding(vertical = N3Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(N3Spacing.s)) {
            Caption(stringResource(R.string.mistake_category))
            Tag(stringResource(analysis.category.labelRes()), tone = TagTone.Accent)
        }
        Caption(stringResource(R.string.mistake_evidence))
        analysis.evidence.forEach { Text("• " + evidenceText(it), style = MaterialTheme.typography.bodyMedium) }

        Caption(stringResource(R.string.mistake_knowledge))
        if (analysis.knowledge.isEmpty()) {
            Text(stringResource(R.string.mistake_knowledge_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                analysis.knowledge.forEach { k ->
                    if (k.item != null) {
                        AssistChip(onClick = { actions.onOpenItem(k.ref) }, label = { Text(k.title()) })
                    } else {
                        Text(
                            stringResource(R.string.mistake_knowledge_missing, stringResource(k.ref.type.mistakeLabelRes()), k.ref.id),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Caption(stringResource(R.string.mistake_fix))
        analysis.recommendations.forEach { RecommendationView(it, actions) }

        if (analysis.lessons.isNotEmpty()) {
            Caption(stringResource(R.string.mistake_lessons))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
                analysis.lessons.forEach { n ->
                    AssistChip(onClick = { actions.onOpenLesson(n) }, label = { Text(stringResource(R.string.mistake_lesson, n)) })
                }
            }
        }

        val repeat = analysis.repeat
        Caption(stringResource(R.string.mistake_repeat, repeat.timesMissed, repeat.timesSeen))
        if (repeat.linked.isNotEmpty()) {
            Caption(stringResource(R.string.mistake_linked, repeat.linked.size))
            repeat.linked.take(5).forEach { link ->
                val basis = when (link.basis) {
                    is LinkBasis.SameKnowledge -> stringResource(R.string.mistake_linked_knowledge)
                    is LinkBasis.SameTask -> stringResource(R.string.mistake_linked_task)
                }
                Text("• ${link.content} ($basis)", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun RecommendationView(recommendation: Recommendation, actions: MistakeActions) {
    when (recommendation) {
        is Recommendation.CompareGrammar -> {
            Text(stringResource(R.string.mistake_fix_compare), style = MaterialTheme.typography.bodyMedium)
            GrammarComparison(recommendation.points, actions)
        }
        is Recommendation.StudyItem -> TextButton(
            onClick = { actions.onOpenItem(recommendation.knowledge.ref) },
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) { Text(stringResource(R.string.mistake_fix_study_item, recommendation.knowledge.title())) }
        is Recommendation.PracticeWords -> FilledTonalButton(onClick = { actions.onPracticeWords(recommendation.refs) }) {
            Text(stringResource(R.string.mistake_fix_practice_words, recommendation.refs.size))
        }
        is Recommendation.StudySkill -> Bullet(stringResource(R.string.mistake_fix_study_skill, stringResource(recommendation.skill.labelRes())))
        Recommendation.ReadingKeywords -> Bullet(stringResource(R.string.mistake_fix_reading))
        is Recommendation.ListenAgain -> Bullet(
            stringResource(if (recommendation.audioAvailable) R.string.mistake_fix_listen_audio else R.string.mistake_fix_listen_no_audio),
        )
        Recommendation.ReadCarefully -> Bullet(stringResource(R.string.mistake_fix_careful))
        is Recommendation.ManageTime -> Bullet(
            stringResource(if (recommendation.timeRanOut) R.string.mistake_fix_time_up else R.string.mistake_fix_answer_all),
        )
        Recommendation.ReadExplanation -> Bullet(stringResource(R.string.mistake_fix_explanation))
    }
}

@Composable
private fun Bullet(text: String) = Text("• $text", style = MaterialTheme.typography.bodyMedium)

/** The grammar points side by side, one block each: pattern, structure, meaning, caution. */
@Composable
private fun GrammarComparison(points: List<GrammarPoint>, actions: MistakeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(N3Spacing.xs)) {
        points.forEach { g ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                onClick = { actions.onOpenItem(ItemRef(ItemType.GRAMMAR, g.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(N3Spacing.m), verticalArrangement = Arrangement.spacedBy(N3Spacing.xxs)) {
                    FuriganaText(g.pattern, style = MaterialTheme.typography.titleSmall)
                    LabeledLine(R.string.mistake_compare_structure, g.structure)
                    LabeledLine(R.string.mistake_compare_meaning, g.meaningVi)
                    g.cautionNote?.takeIf { it.isNotBlank() }?.let { LabeledLine(R.string.mistake_compare_caution, it) }
                }
            }
        }
    }
}

@Composable
private fun LabeledLine(@StringRes label: Int, value: String) {
    if (value.isBlank()) return
    Text("${stringResource(label)}: $value", style = MaterialTheme.typography.bodySmall)
}

/** Skill names reused here so the notebook reads the same as the results. */
@Composable
internal fun skillName(skill: ExamSkill): String = stringResource(skill.labelRes())
