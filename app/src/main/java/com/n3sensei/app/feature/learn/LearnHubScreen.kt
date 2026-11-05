package com.n3sensei.app.feature.learn

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.GlyphSize
import com.n3sensei.app.core.designsystem.component.GlyphTile
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme

@Composable
fun LearnHubScreen(
    onOpenLessons: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenKanji: () -> Unit,
    onOpenGrammar: () -> Unit,
    onOpenMyWords: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenErrors: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenJlpt: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LearnSectionItem("課", R.string.lessons_title, R.string.learn_lessons_description, onOpenLessons)
        HorizontalDivider()
        LearnSectionItem("語", R.string.vocabulary_title, R.string.learn_vocabulary_description, onOpenVocabulary)
        HorizontalDivider()
        LearnSectionItem("漢", R.string.kanji_title, R.string.learn_kanji_description, onOpenKanji)
        HorizontalDivider()
        LearnSectionItem("文", R.string.grammar_title, R.string.learn_grammar_description, onOpenGrammar)
        HorizontalDivider()
        LearnSectionItem("私", R.string.learn_my_words, R.string.learn_my_words_description, onOpenMyWords)
        HorizontalDivider()
        LearnSectionItem("試", R.string.learn_exam, R.string.learn_exam_description, onOpenExams)
        HorizontalDivider()
        LearnSectionItem("誤", R.string.mistakes_title, R.string.learn_errors_description, onOpenErrors)
        HorizontalDivider()
        LearnSectionItem("析", R.string.jlpt_dashboard_title, R.string.jlpt_dashboard_entry_description, onOpenJlpt)
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
            GlyphTile(glyph, size = GlyphSize.Small, decorative = true)
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun LearnHubScreenPreview() {
    N3SenseiTheme { LearnHubScreen(onOpenLessons = {}, onOpenVocabulary = {}, onOpenKanji = {}, onOpenGrammar = {}, onOpenMyWords = {}, onOpenExams = {}, onOpenErrors = {}) }
}
