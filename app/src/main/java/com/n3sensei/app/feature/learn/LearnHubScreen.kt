package com.n3sensei.app.feature.learn

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText

@Composable
fun LearnHubScreen(
    onOpenLessons: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenKanji: () -> Unit,
    onOpenGrammar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LearnSectionItem("課", R.string.lessons_title, R.string.learn_lessons_description, onOpenLessons)
        HorizontalDivider()
        LearnSectionItem("語", R.string.vocabulary_title, R.string.learn_vocabulary_description, onOpenVocabulary)
        HorizontalDivider()
        LearnSectionItem("漢", R.string.kanji_title, R.string.learn_kanji_description, onOpenKanji)
        HorizontalDivider()
        LearnSectionItem("文", R.string.grammar_title, R.string.learn_grammar_description, onOpenGrammar)
    }
}

@Composable
private fun LearnSectionItem(
    glyph: String,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(descriptionRes)) },
        leadingContent = {
            // Decorative glyph; the headline already names the section.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp).clearAndSetSemantics {},
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(japaneseText(glyph), style = MaterialTheme.typography.titleLarge.japanese())
                }
            }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun LearnHubScreenPreview() {
    N3SenseiTheme { LearnHubScreen(onOpenLessons = {}, onOpenVocabulary = {}, onOpenKanji = {}, onOpenGrammar = {}) }
}
