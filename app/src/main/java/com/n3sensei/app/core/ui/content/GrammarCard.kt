package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.domain.model.GrammarPoint

@Composable
fun GrammarCard(
    grammar: GrammarPoint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(grammar.pattern, style = MaterialTheme.typography.titleLarge.japanese())
            Text(grammar.meaningVi, style = MaterialTheme.typography.bodyLarge)
            Text(
                grammar.structure,
                style = MaterialTheme.typography.bodyMedium.japanese(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GrammarCardPreview() = N3SenseiTheme {
    GrammarCard(PreviewContent.grammar, onClick = {})
}
