package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.model.LessonSummary

@Composable
fun LessonCard(
    lesson: LessonSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.lesson_number, lesson.number, lesson.jlptLevel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
            Text(
                lessonCountsText(lesson),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun lessonCountsText(lesson: LessonSummary): String =
    stringResource(R.string.lesson_counts, lesson.vocabularyCount, lesson.kanjiCount, lesson.grammarCount)

@Preview(showBackground = true)
@Composable
private fun LessonCardPreview() = N3SenseiTheme {
    LessonCard(PreviewContent.lesson, onClick = {})
}
