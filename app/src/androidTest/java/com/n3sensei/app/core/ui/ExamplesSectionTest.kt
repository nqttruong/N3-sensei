package com.n3sensei.app.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.core.designsystem.component.LocalShowFurigana
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.content.ExamplesSection
import com.n3sensei.app.core.ui.content.ExamplesSectionTags
import com.n3sensei.app.domain.example.ExampleHighlight
import com.n3sensei.app.domain.model.ExampleSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamplesSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val example = ExampleSentence("e1", "明日は友達と映画を見る予定です。", "{明日|あした}は{友達|ともだち}と{映画|えいが}を{見|み}る{予定|よてい}です。", "Ngày mai tôi dự định xem phim với bạn.")

    @Test
    fun theWordIsEmphasized_inTheJapaneseOnly_andTheTextIsUnchanged() {
        composeRule.setContent {
            // Furigana off: the sentence is one text whose styles can be read back.
            CompositionLocalProvider(LocalShowFurigana provides false) {
                N3SenseiTheme {
                    ExamplesSection("Ví dụ", listOf(example)) { ExampleHighlight.ofVocabulary(it.japanese, "予定", "よてい") }
                }
            }
        }
        val text = composeRule.onNodeWithText(example.japanese).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single()
        assertEquals(example.japanese, text.text)
        val bold = text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(listOf("予定"), bold.map { text.text.substring(it.start, it.end) })
        // The sentence keeps its Japanese language for TalkBack.
        assertTrue(text.spanStyles.any { it.item.localeList?.first()?.language == "ja" && it.start == 0 && it.end == text.length })
        composeRule.onNodeWithText(example.translationVi).fetchSemanticsNode()
    }

    @Test
    fun noExamples_noSection() {
        composeRule.setContent { N3SenseiTheme { ExamplesSection("Ví dụ", emptyList()) } }
        assertTrue(composeRule.onAllNodesWithTag(ExamplesSectionTags.HEADER).fetchSemanticsNodes().isEmpty())
    }
}
