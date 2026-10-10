package com.n3sensei.app.feature.grammar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.model.GrammarExercise

/** One multiple-choice question; answering reveals correctness and the explanation. */
@Composable
fun GrammarExerciseCard(exercise: GrammarExercise, modifier: Modifier = Modifier) {
    var selected by rememberSaveable(exercise.id) { mutableStateOf<Int?>(null) }
    val answered = selected != null

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FuriganaText(exercise.prompt, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                exercise.options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected == index,
                                enabled = !answered,
                                role = Role.RadioButton,
                                onClick = { selected = index },
                            ),
                    ) {
                        RadioButton(selected = selected == index, onClick = null, enabled = !answered)
                        FuriganaText(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            selected?.let { choice ->
                val correct = choice == exercise.answerIndex
                Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        if (correct) {
                            stringResource(R.string.grammar_exercise_correct)
                        } else {
                            stringResource(R.string.grammar_exercise_incorrect, Furigana.baseText(exercise.options[exercise.answerIndex]))
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                    exercise.explanationVi?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.grammar_exercise_retry)) }
            }
        }
    }
}
