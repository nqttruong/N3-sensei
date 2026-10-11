package com.n3sensei.app.core.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.domain.srs.DueState
import com.n3sensei.app.domain.srs.IntervalUnit
import com.n3sensei.app.domain.srs.ReviewInterval

@Composable
fun intervalLabel(interval: ReviewInterval): String = stringResource(
    when (interval.unit) {
        IntervalUnit.MINUTES -> R.string.interval_minutes
        IntervalUnit.HOURS -> R.string.interval_hours
        IntervalUnit.DAYS -> R.string.interval_days
        IntervalUnit.WEEKS -> R.string.interval_weeks
        IntervalUnit.MONTHS -> R.string.interval_months
    },
    interval.amount,
)

fun DueState.labelRes(): Int? = when (this) {
    DueState.NOT_SCHEDULED -> null
    DueState.NOT_DUE -> R.string.due_state_not_due
    DueState.DUE -> R.string.due_state_due
    DueState.OVERDUE -> R.string.due_state_overdue
}
