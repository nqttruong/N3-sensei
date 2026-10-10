package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.domain.model.ContentTotals

@Composable
fun ContentTotalsRow(totals: ContentTotals, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        StatItem(totals.lessons.toString(), stringResource(R.string.totals_lessons), Modifier.weight(1f))
        StatItem(totals.vocabulary.toString(), stringResource(R.string.totals_vocabulary), Modifier.weight(1f))
        StatItem(totals.kanji.toString(), stringResource(R.string.totals_kanji), Modifier.weight(1f))
        StatItem(totals.grammar.toString(), stringResource(R.string.totals_grammar), Modifier.weight(1f))
    }
}
