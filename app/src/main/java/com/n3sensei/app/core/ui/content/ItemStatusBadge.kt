package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.domain.model.LearningStatus

fun LearningStatus.labelRes(): Int = when (this) {
    LearningStatus.NEW -> R.string.status_new
    LearningStatus.LEARNING -> R.string.status_learning
    LearningStatus.REVIEW -> R.string.status_review
    LearningStatus.MASTERED -> R.string.status_mastered
}

/** Trailing marker on list cards: bookmark when marked, status once learning has started. */
@Composable
fun ItemStatusBadge(
    status: LearningStatus,
    isMarked: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isMarked && status == LearningStatus.NEW) return
    Column(modifier = modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isMarked) {
            Icon(
                N3Icons.Marked,
                contentDescription = stringResource(R.string.marked_label),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (status != LearningStatus.NEW) {
            Text(
                stringResource(status.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
