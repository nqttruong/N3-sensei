package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.domain.model.ItemUserState

/** Status line, "needs attention" toggle and a shortcut to practise the item's lesson. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemActions(
    state: ItemUserState?,
    onToggleMark: () -> Unit,
    onPracticeLesson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state != null) {
            Text(
                stringResource(R.string.item_status, stringResource(state.status.labelRes())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val marked = state?.isMarked == true
            FilterChip(
                selected = marked,
                enabled = state != null,
                onClick = onToggleMark,
                label = { Text(stringResource(if (marked) R.string.mark_remove else R.string.mark_add)) },
                leadingIcon = { Icon(if (marked) N3Icons.Marked else N3Icons.Unmarked, contentDescription = null) },
            )
            OutlinedButton(onClick = onPracticeLesson) { Text(stringResource(R.string.practice_this_lesson)) }
        }
    }
}
