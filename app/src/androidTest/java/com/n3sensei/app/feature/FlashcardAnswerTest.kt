package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.content.FlashcardSession
import com.n3sensei.app.core.ui.content.FlashcardTags
import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.practice.PracticeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The answer side of word and grammar cards: everything the content has, nothing made up (test values only). */
@RunWith(AndroidJUnit4::class)
class FlashcardAnswerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val keiken = Vocabulary("v", "l", "経験", "けいけん", "{経験|けいけん}", "kinh nghiệm", "danh từ", "N3", "Hay đi với する: 経験する.")
    private val withFurigana = ExampleSentence("e1", "日本で働いた経験があります。", "{日本|にほん}で{働|はたら}いた{経験|けいけん}があります。", "Tôi có kinh nghiệm làm việc ở Nhật.")
    private val withoutFurigana = ExampleSentence("e2", "いい経験になった。", null, "Đó là một trải nghiệm tốt.")
    private val bakari = GrammarPoint("g", "l", "〜たばかり", "V-た + ばかり", "vừa mới", "Hành động vừa xảy ra, theo cảm nhận của người nói.", "Khác 〜たところ: …")

    private var revealed by mutableStateOf(false)
    private val ratings = mutableListOf<ReviewRating>()

    private fun show(item: PracticeItem, fontScale: Float = 1f) = composeRule.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            N3SenseiTheme {
                FlashcardSession(
                    banner = null, position = 0, total = 1, item = item, isAnswerRevealed = revealed,
                    onReveal = { revealed = true }, onRate = { ratings += it },
                    ratingHints = mapOf(ReviewRating.AGAIN to "20 phút", ReviewRating.HARD to "20 phút", ReviewRating.GOOD to "1 giờ", ReviewRating.EASY to "1 ngày"),
                )
            }
        }
    }

    private fun reveal() = composeRule.onNode(hasText(string(R.string.practice_reveal)) and hasClickAction()).performClick()

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertRatingButtonsReachable() {
        listOf(R.string.rating_again, R.string.rating_hard, R.string.rating_good, R.string.rating_easy).forEach {
            composeRule.onNode(hasText(string(it)) and hasClickAction()).assertIsDisplayed()
        }
    }

    @Test
    fun aWordWithOneExample_frontHidesTheAnswer_backShowsEverything() {
        show(PracticeItem.VocabularyItem(keiken, listOf(withFurigana)))
        composeRule.onNodeWithTag(FlashcardTags.FRONT).assertIsDisplayed()
        listOf("kinh nghiệm", "danh từ", "Tôi có kinh nghiệm làm việc ở Nhật.", "Hay đi với する: 経験する.").forEach { assertTrue("$it before reveal", !shown(it)) }

        reveal()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("kinh nghiệm").assertIsDisplayed()
        composeRule.onNodeWithText("danh từ").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flashcard_examples, 1)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tôi có kinh nghiệm làm việc ở Nhật.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("日本で働いた経験があります。").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hay đi với する: 経験する.").performScrollTo().assertIsDisplayed()
        // Revealing rated nothing: the learner chooses.
        assertTrue(ratings.isEmpty())
        assertRatingButtonsReachable()
        composeRule.onNode(hasText(string(R.string.rating_good)) and hasClickAction()).performClick()
        assertEquals(listOf(ReviewRating.GOOD), ratings)
    }

    @Test
    fun aWordWithManyExamples_showsThreeThenAllOnRequest_eachOnce() {
        val examples = (1..5).map { ExampleSentence("x$it", "例文$it。", null, "Câu số $it") }
        show(PracticeItem.VocabularyItem(keiken, examples))
        reveal()
        composeRule.onNodeWithText(string(R.string.flashcard_examples, 5)).performScrollTo().assertIsDisplayed()
        assertTrue(shown("Câu số 3") && !shown("Câu số 4"))
        composeRule.onNodeWithTag(FlashcardTags.MORE_EXAMPLES).performScrollTo().performClick()
        (1..5).forEach { assertEquals("Câu số $it", 1, composeRule.onAllNodesWithText("Câu số $it").fetchSemanticsNodes().size) }
        composeRule.onNodeWithText(string(R.string.flashcard_fewer_examples, 2)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aWordWithoutExamples_saysSo_andMakesNoneUp() {
        // No part of speech, no note, no furigana in the content: none shown, the reading is kana.
        show(PracticeItem.VocabularyItem(keiken.copy(furigana = null, partOfSpeech = null, note = null), emptyList()))
        reveal()
        composeRule.onNodeWithText("けいけん").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flashcard_examples, 0)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flashcard_no_examples_word)).performScrollTo().assertIsDisplayed()
        assertTrue(!shown(string(R.string.flashcard_note)))
        assertRatingButtonsReachable()
    }

    @Test
    fun anExampleWithoutFurigana_getsNoneAdded_oneWithFuriganaShowsIt() {
        // The same sentence twice: with furigana in the content, and without.
        val ruby = ExampleSentence("r", "経験です。", "{経験|けいけん}です。", "Có furigana.")
        val plain = ExampleSentence("p", "経験です。", null, "Không có furigana.")
        show(PracticeItem.VocabularyItem(keiken, listOf(ruby, plain)))
        reveal()
        composeRule.onNodeWithText("Không có furigana.").performScrollTo().assertIsDisplayed()
        val heights = composeRule.onAllNodesWithText("経験です。").fetchSemanticsNodes().map { it.boundsInRoot.height }
        assertEquals(2, heights.size)
        // The reading line sits above the first only: nothing was added to the second.
        assertTrue("heights $heights", heights[0] > heights[1] * 1.2f)
    }

    @Test
    fun aGrammarPattern_asksBeforeAndExplainsAfter_withAllItsExamples() {
        show(PracticeItem.GrammarItem(bakari, listOf(ExampleSentence("g1", "日本に来たばかりです。", "{日本|にほん}に{来|き}たばかりです。", "Tôi vừa mới đến Nhật."), withoutFurigana.copy(id = "g2", translationVi = "Vừa mới ăn xong."))))
        composeRule.onNodeWithTag(FlashcardTags.PROMPT).assertIsDisplayed()
        assertTrue(!shown("vừa mới"))
        reveal()
        composeRule.waitForIdle()
        assertTrue("the prompt goes once the answer shows", composeRule.onAllNodesWithText(string(R.string.flashcard_grammar_prompt)).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("vừa mới").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flashcard_structure)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("V-た + ばかり").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flashcard_examples, 2)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tôi vừa mới đến Nhật.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vừa mới ăn xong.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hành động vừa xảy ra, theo cảm nhận của người nói.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Khác 〜たところ: …").performScrollTo().assertIsDisplayed()
        assertTrue(ratings.isEmpty())
    }

    @Test
    fun aGrammarPatternWithoutExamples_saysSo() {
        show(PracticeItem.GrammarItem(bakari.copy(usage = "", cautionNote = null), emptyList()))
        reveal()
        composeRule.onNodeWithText(string(R.string.flashcard_no_examples_grammar)).performScrollTo().assertIsDisplayed()
        assertTrue(!shown(string(R.string.flashcard_usage)) && !shown(string(R.string.flashcard_caution)))
    }

    @Test
    fun longContent_onASmallScreenWithLargeText_scrolls_andTheButtonsStay() {
        val long = "Giải thích rất dài ".repeat(40)
        val examples = (1..3).map { ExampleSentence("l$it", "とても長い例文です。".repeat(4), null, "Bản dịch dài số $it. ".repeat(6)) }
        show(PracticeItem.GrammarItem(bakari.copy(usage = long, cautionNote = long), examples), fontScale = 2f)
        reveal()
        // Every part reachable by scrolling the card…
        composeRule.onNodeWithText(string(R.string.flashcard_caution)).performScrollTo().assertIsDisplayed()
        // …and the answer buttons never pushed off screen.
        assertRatingButtonsReachable()
    }
}
