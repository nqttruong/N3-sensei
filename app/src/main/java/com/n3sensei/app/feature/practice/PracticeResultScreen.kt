package com.n3sensei.app.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.content.StatItem
import com.n3sensei.app.domain.practice.PracticeResult

@Composable
fun PracticeResultScreen(
    result: PracticeResult,
    onPracticeAgain: () -> Unit,
    onBackToLesson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.practice_result_summary, result.rated, result.total, formatDuration(result.durationSeconds)),
            style = MaterialTheme.typography.titleMedium,
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp)) {
                StatItem(result.again.toString(), stringResource(R.string.rating_again), Modifier.weight(1f))
                StatItem(result.hard.toString(), stringResource(R.string.rating_hard), Modifier.weight(1f))
                StatItem(result.good.toString(), stringResource(R.string.rating_good), Modifier.weight(1f))
                StatItem(result.easy.toString(), stringResource(R.string.rating_easy), Modifier.weight(1f))
            }
        }
        InfoBanner(stringResource(R.string.practice_result_not_saved))
        Button(onClick = onPracticeAgain, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.practice_result_again))
        }
        OutlinedButton(onClick = onBackToLesson, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.practice_result_back))
        }
    }
}

@Composable
private fun formatDuration(seconds: Long): String =
    if (seconds >= 60) {
        stringResource(R.string.practice_result_duration_min_sec, (seconds / 60).toInt(), (seconds % 60).toInt())
    } else {
        stringResource(R.string.practice_result_duration_sec, seconds.toInt())
    }

@Preview(showBackground = true)
@Composable
private fun PracticeResultPreview() = N3SenseiTheme {
    PracticeResultScreen(PracticeResult(7, 1, 2, 3, 1, 95), onPracticeAgain = {}, onBackToLesson = {})
}
