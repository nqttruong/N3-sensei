package com.n3sensei.app.app.translate

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.n3sensei.app.R
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dialog with the real translation engine and the seed content (SEED TEST DATA), on a device
 * without ML Kit models: the dictionary answers, machine translation waits for the learner.
 */
@RunWith(AndroidJUnit4::class)
class QuickTranslateResultsTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun open(text: String) = ActivityScenario.launch<QuickTranslateActivity>(
        Intent(Intent.ACTION_PROCESS_TEXT).setClass(context, QuickTranslateActivity::class.java).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, text),
    )

    private fun waitForText(text: String) = composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Before
    fun setUp() {
        AppTestState.reset(seedContent = true)
        // As on a device that never downloaded them (an earlier run or a manual check may have).
        runBlocking {
            listOf(TranslateLanguage.VIETNAMESE, TranslateLanguage.JAPANESE).forEach {
                RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(it).build()).await()
            }
        }
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings(mode = TranslationMode.JAPANESE)) }
    }

    @After
    fun tearDown() = AppTestState.reset(seedContent = false)

    @Test
    fun aJapaneseWordOfTheContent_meaningReadingAndExample_fromTheDictionary() {
        open("経験").use {
            waitForText("kinh nghiệm")
            composeRule.onNodeWithText(string(R.string.quick_translate_reading, "けいけん")).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.quick_translate_from_dictionary)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(QuickTranslateTags.DETAILS).performScrollTo().performClick()
            waitForText("Tôi có kinh nghiệm làm việc ở Nhật.")
            // Nothing needed a model: no download offered.
            composeRule.onNodeWithTag(QuickTranslateTags.DOWNLOAD).assertDoesNotExist()
        }
    }

    @Test
    fun englishAsked_withoutItsModel_waitsForTheLearner_neverDownloadsAlone() {
        open("経験").use {
            waitForText("kinh nghiệm")
            composeRule.onNodeWithTag(QuickTranslateTags.mode(TranslationMode.BOTH)).performScrollTo().performClick()
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(QuickTranslateTags.DOWNLOAD).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(string(R.string.quick_translate_field_needs_model)).performScrollTo().assertIsDisplayed()
            // The dictionary meaning stays while English waits.
            composeRule.onNodeWithText("kinh nghiệm").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun switchingLanguagesQuickly_endsOnTheLastChoice() {
        open("経験").use {
            waitForText("kinh nghiệm")
            repeat(3) {
                composeRule.onNodeWithTag(QuickTranslateTags.source(Language.VIETNAMESE)).performScrollTo().performClick()
                composeRule.onNodeWithTag(QuickTranslateTags.source(Language.ENGLISH)).performScrollTo().performClick()
                composeRule.onNodeWithTag(QuickTranslateTags.source(Language.JAPANESE)).performScrollTo().performClick()
            }
            // Back on Japanese: the dictionary result again, not one of the cancelled requests.
            waitForText("kinh nghiệm")
            composeRule.onNodeWithText(string(R.string.quick_translate_reading, "けいけん")).performScrollTo().assertIsDisplayed()
        }
    }
}
