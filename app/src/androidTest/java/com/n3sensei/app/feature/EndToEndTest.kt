package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import com.n3sensei.app.testing.countRows
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full app against the real Room database and DataStore, with SEED TEST DATA loaded only for
 * the duration of the test.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun launch(
        seedContent: Boolean = true,
        onboardingCompleted: Boolean = true,
        bundledContent: Boolean = false,
    ): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent, onboardingCompleted, bundledContent)
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForText(text: String, substring: Boolean = false) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun clickable(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasClickAction())

    /** Waits until a clickable node with [text] is on screen (after a navigation or reload). */
    private fun awaitClickable(text: String): SemanticsNodeInteraction {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        return clickable(text)
    }

    private fun openTab(tab: TopLevelDestination) = composeRule.onNodeWithTag(tab.testTag).performClick()

    /** Học → Bài học → Bài 01 (the seed content: 2 kanji, 4 words, 1 grammar point). */
    private fun openLesson1() {
        openTab(TopLevelDestination.LEARN)
        clickable(string(R.string.lessons_title)).performClick()
        waitForText(string(R.string.course_lesson_title, 1))
        awaitClickable(string(R.string.course_open)).performClick()
        waitForText(string(R.string.course_lesson_counts, 2, 4, 1))
    }

    @Test
    fun emptyDatabase_screensSayNoContentIsLoaded() {
        launch(seedContent = false).use {
            waitForText(string(R.string.common_no_content))
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            waitForText(string(R.string.common_no_content))
            Espresso.pressBack()
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText(string(R.string.vocabulary_empty))
        }
    }

    @Test
    fun dashboard_seedPackIsTestData_andNotCounted() {
        launch().use {
            // The seed pack is marked is_seed = 1, so statistics treat the app as having no content.
            waitForText(string(R.string.dashboard_goal))
            composeRule.onNodeWithText(string(R.string.common_no_content)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.dashboard_priority_empty)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun lesson_toVocabularyKanjiAndGrammarDetails() {
        launch().use {
            openLesson1()

            // Kanji tab (first): a kanji expands to its words, then opens its own detail screen.
            composeRule.onNodeWithText("経").performClick()
            waitForText(string(R.string.flashcard_kanji_words, 1))
            composeRule.onNodeWithText("Tôi có kinh nghiệm làm việc ở Nhật.").assertIsDisplayed()
            clickable(string(R.string.course_open_kanji)).performClick()
            waitForText("KINH")
            composeRule.onNodeWithText("ケイ、キョウ").assertIsDisplayed()
            Espresso.pressBack()

            clickable(string(R.string.lesson_tab_vocabulary, 4)).performClick()
            waitForText(string(R.string.course_vocabulary_section, "1", 4))
            composeRule.onNodeWithText("経験").performClick()
            waitForText("kinh nghiệm")
            composeRule.onNodeWithText("Tôi có kinh nghiệm làm việc ở Nhật.").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.source_license_unverified, ""), substring = true).performScrollTo().assertIsDisplayed()
            Espresso.pressBack()

            clickable(string(R.string.lesson_tab_grammar, 1)).performClick()
            composeRule.onNodeWithText("〜たばかり").performClick()
            waitForText("日本に来たばかりです。")
            clickable(string(R.string.course_open_grammar)).performScrollTo().performClick()
            waitForText(string(R.string.grammar_caution))
        }
    }

    @Test
    fun lessonTabs_switchFreelyInAnyOrder_withoutLockingOrRecording() {
        launch().use {
            openLesson1()
            listOf(
                string(R.string.lesson_tab_grammar, 1) to "〜たばかり",
                string(R.string.lesson_tab_kanji, 2) to "経",
                string(R.string.lesson_tab_vocabulary, 4) to "経験",
                string(R.string.lesson_tab_grammar, 1) to "〜たばかり",
                string(R.string.lesson_tab_vocabulary, 4) to "増える",
            ).forEach { (tab, shown) ->
                clickable(tab).performClick()
                waitForText(shown)
            }
            // Expanding a kanji and switching tabs back keeps it expanded.
            clickable(string(R.string.lesson_tab_kanji, 2)).performClick()
            composeRule.onNodeWithText("経").performClick()
            waitForText(string(R.string.course_open_kanji))
            clickable(string(R.string.lesson_tab_grammar, 1)).performClick()
            clickable(string(R.string.lesson_tab_kanji, 2)).performClick()
            waitForText(string(R.string.course_open_kanji))
        }
        assertEquals(0, AppTestState.appContainer.database.countRows("learning_progress"))
        assertEquals(0, AppTestState.appContainer.database.countRows("review_records"))
    }

    @Test
    fun vocabularySearch_findsByReading_reportsNoResult_andFiltersByLesson() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText("経験")

            val field = composeRule.onNode(hasSetTextAction())
            field.performTextInput("ふえる")
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextExact("経験").isEmpty() }
            composeRule.onNodeWithText("増える").assertIsDisplayed()

            field.performTextReplacement("zzz")
            waitForText(string(R.string.common_no_results, "zzz"))

            field.performTextReplacement("")
            waitForText("経験")
            // Lesson 2 has no vocabulary, so it gets no chip; lesson 1 filters the list.
            assertTrue(composeRule.onAllNodesWithTextExact(string(R.string.lesson_number, 2, "N3")).isEmpty())
            composeRule.onNodeWithText(string(R.string.lesson_number, 1, "N3")).performClick()
            waitForText("経験")
        }
    }

    @Test
    fun practiceSession_ratesEveryCard_thenShowsResult_andReturnsToLesson() {
        launch().use {
            openLesson1()
            composeRule.onNodeWithText(string(R.string.lesson_practice)).performClick()
            waitForText(string(R.string.practice_not_saved))

            repeat(7) { index ->
                waitForText(string(R.string.practice_progress, index + 1, 7))
                composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
                clickable(string(if (index == 0) R.string.rating_again else R.string.rating_good)).performClick()
            }

            waitForText(string(R.string.practice_result_title))
            composeRule.onNodeWithText(string(R.string.practice_result_not_saved)).assertIsDisplayed()
            composeRule.onNodeWithText("7 / 7", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.practice_result_back)).performClick()
            waitForText(string(R.string.course_lesson_counts, 2, 4, 1))

            // Practice results are not saved to the review schedule.
            openTab(TopLevelDestination.REVIEW)
            waitForText(string(R.string.review_overview_today, 0, 200, 0, 20))
        }
    }

    @Test
    fun onboarding_canBeSkipped_isRemembered_andCanBeReplayed() {
        launch(onboardingCompleted = false).use {
            waitForText(string(R.string.onboarding_1_title))
            composeRule.onNodeWithText(string(R.string.onboarding_skip)).performClick()
            waitForText(string(R.string.dashboard_goal))
            assertTrue(runBlocking { AppTestState.preferences.first().onboardingCompleted })
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(string(R.string.dashboard_goal))
            openTab(TopLevelDestination.SETTINGS)
            clickable(string(R.string.settings_replay_onboarding)).performScrollTo().performClick()
            waitForText(string(R.string.onboarding_1_title))
        }
    }

    @Test
    fun settings_themeAndFuriganaArePersisted() {
        launch().use {
            openTab(TopLevelDestination.SETTINGS)
            clickable(string(R.string.settings_theme_dark)).performClick()
            clickable(string(R.string.settings_show_furigana)).performClick()
            composeRule.waitUntil(5_000) {
                runBlocking { AppTestState.preferences.first() }.let { it.themeMode == ThemeMode.DARK && !it.showFurigana }
            }
            val prefs = runBlocking { AppTestState.preferences.first() }
            assertEquals(ThemeMode.DARK, prefs.themeMode)
            assertFalse(prefs.showFurigana)
            // The content section stays; the list of every content source is no longer in Cài đặt.
            composeRule.onNodeWithText(string(R.string.settings_import)).performScrollTo().assertIsDisplayed()
            composeRule.onAllNodesWithText(SEED_SOURCE_NAME, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun bundledCourse_lessonOne_has24Kanji30Words7Grammar_fromEachList() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            waitForText(string(R.string.course_lesson_title, 1))
            composeRule.onAllNodes(hasText(string(R.string.course_lesson_counts, 24, 30, 7))).onFirst().assertIsDisplayed()
            // The last lesson holds the 10 words left after 30 full lessons.
            composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(string(R.string.course_lesson_title, 31)))
            composeRule.onNodeWithText(string(R.string.course_lesson_counts, 0, 10, 0)).assertIsDisplayed()
        }
    }

    @Test
    fun bundledMimikaraPack_isInstalledOnFirstLaunch_withAll880Words() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.lessons_title)).performClick()
            waitForText(string(R.string.course_lesson_title, 1))
            composeRule.onAllNodes(hasText(string(R.string.course_open)) and hasClickAction()).onFirst().performClick()
            awaitClickable(string(R.string.lesson_tab_vocabulary, 30)).performClick()
            waitForText(string(R.string.course_vocabulary_section, "1.1", 30))
            waitForText("男性")
            // Kanji tab: 意 expands to all its words.
            awaitClickable(string(R.string.lesson_tab_kanji, 24)).performClick()
            composeRule.onNodeWithText("意").performClick()
            waitForText(string(R.string.flashcard_kanji_words, 3))
            waitForText("Quyết tâm")
        }
        val vocabulary = AppTestState.appContainer.database.countRows("vocabulary")
        // Illustration 30 + Mimikara 880; the kanji pack adds no vocabulary.
        assertEquals(30 + 880, vocabulary)
    }

    @Test
    fun bundledGrammarPack_isInstalledOnFirstLaunch_andShownInGrammarList() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.grammar_title)).performClick()
            waitForText("〜ことにしている")
            composeRule.onNodeWithText("〜ことにしている").performClick()
            waitForText("健康のために、毎朝三十分歩くことにしている。")
            waitForText("Vì sức khỏe, tôi có thói quen đi bộ ba mươi phút mỗi sáng.")
        }
        // 7 grammar points of the illustration pack + 110 + 64 from N3.pdf.
        assertEquals(7 + 174, AppTestState.appContainer.database.countRows("grammar"))
    }

    @Test
    fun bundledKanjiPack_kanjiDetailShowsItsPdfGroupWords() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.kanji_title)).performClick()
            // The kanji pack comes first in curriculum order: its lesson 1 starts with 意.
            waitForText("意")
            composeRule.onAllNodes(hasText("意") and hasClickAction()).onFirst().performClick()
            waitForText("Ý, ý nghĩ, tâm ý")
            // The section title counts the words (注意, 意味, 決意).
            composeRule.onNodeWithText(string(R.string.examples_header, string(R.string.kanji_example_words), 3)).performScrollTo()
            waitForText("Chú ý")
            waitForText("Quyết tâm")
        }
        // 27 kanji of the illustration pack + 393; the kanji words are kanji examples.
        assertEquals(27 + 393, AppTestState.appContainer.database.countRows("kanji"))
        assertEquals(30 + 880, AppTestState.appContainer.database.countRows("vocabulary"))
    }

    @Test
    fun editingAKanjiMeaning_isShownOnItsDetail_andCanBeReverted() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.kanji_title)).performClick()
            waitForText("経")
            composeRule.onAllNodes(hasText("経") and hasClickAction()).onFirst().performClick()
            awaitClickable(string(R.string.edit_action)).performScrollTo().performClick()
            waitForText(string(R.string.edit_field_meaning) + " *")
            composeRule.onNode(hasSetTextAction() and hasText("trải qua, kinh qua")).performTextReplacement("kinh qua (đã sửa)")
            awaitClickable(string(R.string.edit_save)).performScrollTo().performClick()
            // Back on the detail, the saved meaning is shown.
            waitForText("kinh qua (đã sửa)")
            awaitClickable(string(R.string.edit_action)).performScrollTo().performClick()
            waitForText(string(R.string.edit_field_original, "trải qua, kinh qua"))
            awaitClickable(string(R.string.edit_revert)).performScrollTo().performClick()
            // The dialog's button (the form's button is still behind it).
            composeRule.onAllNodes(hasText(string(R.string.edit_revert)) and hasClickAction()).onLast().performClick()
            waitForText("trải qua, kinh qua")
        }
    }

    @Test
    fun kanjiExamples_addedInTheEditForm_areSavedWithLuu_shownAgain_andRemovable() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.kanji_title)).performClick()
            waitForText("経")
            composeRule.onAllNodes(hasText("経") and hasClickAction()).onFirst().performClick()
            awaitClickable(string(R.string.edit_action)).performScrollTo().performClick()
            awaitClickable(string(R.string.edit_example_add)).performScrollTo().performClick()
            // The dialog checks the example before keeping it.
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.edit_example_field_ja) + " *")).performTextInput("路")
            clickable(string(R.string.edit_example_keep)).performClick()
            waitForText(string(R.string.edit_error_missing_kanji))
            composeRule.onNode(hasSetTextAction() and hasText("路")).performTextReplacement("経路")
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.edit_example_field_furigana))).performTextInput("けいろ")
            // The dialog's field (the form's meaning field has the same label behind it).
            composeRule.onAllNodes(hasSetTextAction() and hasText(string(R.string.edit_example_field_meaning) + " *")).onLast().performTextInput("lộ trình (thử)")
            clickable(string(R.string.edit_example_keep)).performClick()
            waitForText(string(R.string.edit_example_unsaved))
            awaitClickable(string(R.string.edit_save)).performScrollTo().performClick()
            // On the kanji page with the pack's words.
            waitForText("lộ trình (thử)")
            composeRule.onNodeWithText("lộ trình (thử)").performScrollTo().assertIsDisplayed()

            // Shown again when the form opens; removing it and leaving with Hủy keeps it.
            awaitClickable(string(R.string.edit_action)).performScrollTo().performClick()
            waitForText(string(R.string.edit_example_own))
            awaitClickable(string(R.string.edit_example_delete)).performScrollTo().performClick()
            composeRule.onAllNodes(hasText(string(R.string.edit_example_delete)) and hasClickAction()).onLast().performClick()
            awaitClickable(string(R.string.common_cancel)).performScrollTo().performClick()
            waitForText("lộ trình (thử)")
            composeRule.onNodeWithText("lộ trình (thử)").performScrollTo().assertIsDisplayed()

            // Removed and saved: gone.
            awaitClickable(string(R.string.edit_action)).performScrollTo().performClick()
            awaitClickable(string(R.string.edit_example_delete)).performScrollTo().performClick()
            composeRule.onAllNodes(hasText(string(R.string.edit_example_delete)) and hasClickAction()).onLast().performClick()
            awaitClickable(string(R.string.edit_save)).performScrollTo().performClick()
            awaitClickable(string(R.string.edit_action))
            composeRule.onAllNodesWithText("lộ trình (thử)").assertCountEquals(0)
        }
        assertEquals(0, AppTestState.appContainer.database.countRows("user_kanji_examples"))
    }

    @Test
    fun vocabularyGroups_splitLessonsInto30s_openAndPractise_andProgressSurvivesARestart() {
        val group12 = string(R.string.vocabulary_group_part, 2, 4)
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            // Mimikara lesson 1 has 120 words: Bài 1.1 to 1.4, 30 each.
            awaitClickable(string(R.string.vocabulary_group_title, "1.2")).performClick()
            waitForText("$group12 · " + string(R.string.vocabulary_group_progress, 0, 30))
            awaitClickable(string(R.string.vocabulary_group_practice)).performScrollTo().performClick()
            waitForText(string(R.string.practice_progress, 1, 30))
        }
        // The 31st word of the lesson is the first of Bài 1.2; progress belongs to the word's id.
        val db = AppTestState.appContainer.database
        val word31 = db.openHelper.readableDatabase
            .query("SELECT id FROM vocabulary WHERE lesson_id = 'mimikara-n3-l01' ORDER BY sort_order LIMIT 1 OFFSET 30")
            .use { it.moveToFirst(); it.getString(0) }
        runBlocking {
            db.progressDao().upsert(LearningProgressEntity(ItemType.VOCABULARY, word31, ProgressState.LEARNING, 0, 20, 1, 1, 1, 0, 1, 1))
        }
        // Closed and opened again: same groups, the word counted in Bài 1.2 only, no copies.
        ActivityScenario.launch(MainActivity::class.java).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_group_title, "1.2")).performClick()
            waitForText("$group12 · " + string(R.string.vocabulary_group_progress, 1, 30))
        }
        assertEquals(30 + 880, db.countRows("vocabulary"))
    }

    @Test
    fun cardsAndDetails_sayWhereEachItemIsStudied() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.kanji_title)).performClick()
            // 意 opens Học → Bài học: lesson 1, Kanji tab.
            val kanjiPlace = string(R.string.location_in_course, string(R.string.course_lesson_title, 1), string(R.string.totals_kanji))
            waitForText(kanjiPlace, substring = true)
            composeRule.onAllNodes(hasText("意") and hasClickAction()).onFirst().performClick()
            waitForText(string(R.string.location_section))
            composeRule.onNodeWithText(string(R.string.location_section)).performScrollTo().assertIsDisplayed()
            assertTrue(composeRule.onAllNodes(hasText(kanjiPlace)).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun addingAWord_putsItInMyWords_andRejectsADuplicate() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText("経験")
            awaitClickable(string(R.string.add_word_action)).performClick()
            waitForText(string(R.string.edit_new_word_hint))
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.edit_field_expression) + " *")).performTextInput("経験")
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.edit_field_reading) + " *")).performTextInput("けいけん")
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.edit_field_meaning) + " *")).performTextInput("kinh nghiệm")
            clickable(string(R.string.edit_add_word)).performScrollTo().performClick()
            waitForText(string(R.string.edit_duplicate_word, "Gói seed kiểm thử"), substring = true)
            composeRule.onNode(hasSetTextAction() and hasText("経験")).performTextReplacement("経由")
            composeRule.onNode(hasSetTextAction() and hasText("けいけん")).performTextReplacement("けいゆ")
            composeRule.onNode(hasSetTextAction() and hasText("kinh nghiệm")).performTextReplacement("đi qua, quá cảnh")
            clickable(string(R.string.edit_add_word)).performScrollTo().performClick()
            waitForText("経験")
            composeRule.onNode(hasSetTextAction()).performTextInput("けいゆ")
            waitForText("đi qua, quá cảnh")
        }
        assertEquals(1, AppTestState.appContainer.database.countRows("content_packs WHERE id = 'user-words'"))
    }

    @Test
    fun vocabularySearch_ignoresVietnameseDiacritics() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText("経験")
            composeRule.onNode(hasSetTextAction()).performTextInput("gui den")
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextExact("経験").isEmpty() }
            composeRule.onNodeWithText("届ける").assertIsDisplayed()
        }
    }

    @Test
    fun markItem_filterMarked_thenPracticeMarkedItems() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText("増える")
            composeRule.onNodeWithText("増える").performClick()
            clickable(string(R.string.mark_add)).performClick()
            waitForText(string(R.string.mark_remove))
            Espresso.pressBack()

            clickable(string(R.string.filter_marked)).performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextExact("経験").isEmpty() }
            composeRule.onNodeWithText("増える").assertIsDisplayed()

            composeRule.onNodeWithText(string(R.string.practice_marked)).performClick()
            waitForText(string(R.string.practice_progress, 1, 1))
            composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
            clickable(string(R.string.rating_good)).performClick()
            waitForText(string(R.string.practice_result_title))
            composeRule.onNodeWithText("1 / 1", substring = true).assertIsDisplayed()

            openTab(TopLevelDestination.REVIEW)
            waitForText(string(R.string.practice_marked_count, 1))
        }
    }

    @Test
    fun grammarExercise_showsResultAndExplanation() {
        launch(seedContent = false, bundledContent = true).use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.grammar_title)).performClick()
            // With the bundled N3 grammar pack the list is long: search instead of scrolling.
            composeRule.onNode(hasSetTextAction()).performTextInput("たばかり")
            waitForText("〜たばかり")
            composeRule.onNodeWithText("〜たばかり").performClick()
            waitForText(string(R.string.grammar_exercises))
            composeRule.onNodeWithText("来た").performScrollTo().performClick()
            composeRule.onNodeWithText(string(R.string.grammar_exercise_correct)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.grammar_exercise_retry)).performScrollTo().performClick()
            composeRule.onNodeWithText("来る").performScrollTo().performClick()
            waitForText(string(R.string.grammar_exercise_incorrect, "来た"))
        }
    }

    @Test
    fun kanjiDetail_saysStrokeOrderIsNotAvailable() {
        launch().use {
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.kanji_title)).performClick()
            waitForText("増")
            composeRule.onNodeWithText("増").performClick()
            composeRule.onNodeWithText(string(R.string.kanji_stroke_order_unavailable)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun reviewSession_savesEveryAnswer_andProgressSurvivesRestart() {
        launch().use {
            openTab(TopLevelDestination.REVIEW)
            // 7 seed items are new; the default limit allows 20 new items a day.
            waitForText(string(R.string.review_start, 7))
            clickable(string(R.string.review_start, 7)).performClick()
            waitForText(string(R.string.review_session_saved_note))
            repeat(7) { index ->
                waitForText(string(R.string.practice_progress, index + 1, 7))
                composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
                // Button label includes the next interval, e.g. "Tốt\n1 giờ".
                composeRule.onNode(hasText(string(R.string.rating_good), substring = true) and hasClickAction()).performClick()
            }
            waitForText(string(R.string.review_result_saved))
            composeRule.onNodeWithText(string(R.string.review_result_back)).performClick()
            waitForText(string(R.string.review_overview_today, 0, 200, 7, 20))
            composeRule.onNodeWithText(string(R.string.review_start, 0)).assertIsNotEnabled()
        }
        // Relaunch: progress is read back from Room.
        ActivityScenario.launch(MainActivity::class.java).use {
            openTab(TopLevelDestination.REVIEW)
            waitForText(string(R.string.review_overview_today, 0, 200, 7, 20))
            openTab(TopLevelDestination.LEARN)
            clickable(string(R.string.vocabulary_title)).performClick()
            awaitClickable(string(R.string.vocabulary_all)).performClick()
            waitForText("経験")
            composeRule.onNodeWithText("経験").performClick()
            waitForText(string(R.string.item_memory_level, 2, 7))
            composeRule.onNodeWithText(string(R.string.due_state_not_due), substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun reviewSettings_validatesAndSaves() {
        launch().use {
            openTab(TopLevelDestination.SETTINGS)
            clickable(string(R.string.review_settings_title)).performClick()
            waitForText(string(R.string.review_settings_intervals))
            val firstAmount = composeRule.onAllNodes(hasSetTextAction())[0]
            firstAmount.performTextReplacement("")
            composeRule.onNodeWithText(string(R.string.review_settings_save)).performScrollTo().performClick()
            waitForText(string(R.string.review_settings_error_amount, 1))

            firstAmount.performTextReplacement("10")
            composeRule.onNodeWithText(string(R.string.review_settings_save)).performScrollTo().performClick()
            waitForText(string(R.string.review_settings_saved))
            assertEquals("10m,1h,1d,2d,1w,2w,1mo", runBlocking { AppTestState.preferences.first() }.reviewSettings.ladder.encode())
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextExact(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes()

    private companion object {
        const val SEED_SOURCE_NAME = "dữ liệu seed cho kiểm thử"
    }
}
