package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.content.FlashcardSession
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.practice.PracticeItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The back of a kanji card lists every word of the kanji, and the answer buttons stay usable. */
@RunWith(AndroidJUnit4::class)
class FlashcardKanjiWordsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val kanji = Kanji("k", "l", "経", listOf("ケイ"), listOf("へ.る"), "KINH", "trải qua", 11, null)

    private fun show(words: List<KanjiWord>) = composeRule.setContent {
        N3SenseiTheme {
            FlashcardSession(
                banner = null, position = 0, total = 1, item = PracticeItem.KanjiItem(kanji, words), isAnswerRevealed = true,
                onReveal = {}, onRate = {},
                ratingHints = mapOf(
                    ReviewRating.AGAIN to "20 phút", ReviewRating.HARD to "20 phút",
                    ReviewRating.GOOD to "1 giờ", ReviewRating.EASY to "1 ngày",
                ),
            )
        }
    }

    private fun assertButtonsUsable() {
        listOf(R.string.rating_again to "20 phút", R.string.rating_good to "1 giờ", R.string.rating_easy to "1 ngày").forEach { (label, hint) ->
            composeRule.onNode(hasText(string(label)) and hasText(hint) and hasClickAction()).assertIsDisplayed()
        }
    }

    @Test
    fun noWords_saysSo() {
        show(emptyList())
        composeRule.onNodeWithText(string(R.string.flashcard_kanji_words, 0)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flashcard_kanji_no_words)).assertIsDisplayed()
        assertButtonsUsable()
    }

    @Test
    fun oneWord_isShownWithItsMeaning() {
        show(listOf(KanjiWord("経験", "{経験|けいけん}", "kinh nghiệm")))
        composeRule.onNodeWithText(string(R.string.flashcard_kanji_words, 1)).assertIsDisplayed()
        composeRule.onNodeWithText("kinh nghiệm").assertIsDisplayed()
        assertButtonsUsable()
    }

    @Test
    fun manyWords_allReachable_kanaOnlyAndLinkedIncluded_buttonsStayOnScreen() {
        val words = (1..40).map { KanjiWord("経$it", null, "nghĩa số $it") } +
            KanjiWord("へる", null, "chỉ viết bằng kana") +
            KanjiWord("経由", null, "đi qua", vocabularyId = "user-w-1")
        show(words)
        composeRule.onNodeWithText(string(R.string.flashcard_kanji_words, 42)).assertIsDisplayed()
        // Every word is on the card: scroll to the first, a middle one and the last.
        listOf("nghĩa số 1", "nghĩa số 20", "nghĩa số 40", "chỉ viết bằng kana", "đi qua").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
            assertButtonsUsable()
        }
        composeRule.onNodeWithText(string(R.string.flashcard_kanji_word_linked)).performScrollTo().assertIsDisplayed()
    }
}
