package com.n3sensei.app.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.SectionCard
import com.n3sensei.app.core.designsystem.theme.N3Spacing
import com.n3sensei.app.core.ui.state.UiStateContent
import com.n3sensei.app.domain.mistakes.Knowledge
import com.n3sensei.app.domain.mistakes.KnowledgeStat
import com.n3sensei.app.domain.mistakes.WeaknessAnalyzer
import com.n3sensei.app.domain.mistakes.WeaknessOverview
import com.n3sensei.app.domain.mistakes.WeaknessReport
import com.n3sensei.app.domain.model.ItemRef

@Composable
fun WeaknessScreen(viewModel: WeaknessViewModel, actions: MistakeActions, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, modifier, onRetry = viewModel::load) { overview ->
        WeaknessContent(overview, actions)
    }
}

/** Weak points over all submitted exams: skills, study items missed often, not fixed yet, improved. */
@Composable
fun WeaknessContent(overview: WeaknessOverview, actions: MistakeActions, modifier: Modifier = Modifier) {
    val weakness = overview.weakness
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(N3Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(N3Spacing.m),
    ) {
        item { WeaknessCard(weakness) }
        knowledgeCard(R.string.mistakes_frequent, weakness.frequentKnowledge, overview.knowledge, actions)
        knowledgeCard(R.string.mistakes_unresolved, weakness.unresolved, overview.knowledge, actions)
        knowledgeCard(R.string.mistakes_improved, weakness.improved, overview.knowledge, actions)
        if (weakness.recurring.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.mistakes_recurring)) {
                    weakness.recurring.forEach {
                        Text(stringResource(R.string.mistakes_recurring_row, it.content, it.missed, it.seen), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaknessCard(weakness: WeaknessReport) {
    SectionCard(stringResource(R.string.mistakes_weakness)) {
        if (weakness.skills.isEmpty()) Text(stringResource(R.string.mistakes_weakness_empty))
        weakness.skills.forEach { s ->
            Text(
                if (s.reliable) {
                    stringResource(R.string.mistakes_weakness_row, skillName(s.skill), s.missed, s.answered, s.missRate.percent())
                } else {
                    stringResource(R.string.mistakes_weakness_few, skillName(s.skill), s.answered, WeaknessAnalyzer.MIN_SKILL_QUESTIONS)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.knowledgeCard(
    title: Int,
    stats: List<KnowledgeStat>,
    knowledge: Map<ItemRef, Knowledge>,
    actions: MistakeActions,
) {
    if (stats.isEmpty()) return
    item {
        SectionCard(stringResource(title)) {
            stats.forEach { stat ->
                val k = knowledge[stat.ref]
                val name = k?.title() ?: stat.ref.id
                val text = stringResource(R.string.mistakes_knowledge_row, name, stat.missed, stat.seen)
                if (k?.item != null) {
                    androidx.compose.material3.TextButton(
                        onClick = { actions.onOpenItem(stat.ref) },
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(text) }
                } else {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
