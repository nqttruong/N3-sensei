package com.n3sensei.app.feature

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.testing.AnalyticsFixture
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The activity is destroyed and recreated (as on rotation, theme or language change) in the
 * middle of a task; the task must continue where it was, without lost or duplicated work.
 */
@RunWith(AndroidJUnit4::class)
class ConfigurationChangeTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
    private val db get() = AppTestState.appContainer.database

    private fun waitFor(text: String, substring: Boolean = false) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun launch(): ActivityScenario<MainActivity> {
        AppTestState.reset(seedContent = true)
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun openTab(tab: TopLevelDestination) = composeRule.onNodeWithTag(tab.testTag).performClick()

    private fun rateGood() =
        composeRule.onNode(hasText(string(R.string.rating_good), substring = true) and hasClickAction()).performClick()

    @Test
    fun reviewSession_continuesAtTheSameCard_withoutDuplicateAnswers() {
        launch().use { scenario ->
            openTab(TopLevelDestination.REVIEW)
            waitFor(string(R.string.review_start, 7))
            composeRule.onNode(hasText(string(R.string.review_start, 7)) and hasClickAction()).performClick()
            waitFor(string(R.string.practice_progress, 1, 7))
            composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()
            rateGood()
            waitFor(string(R.string.practice_progress, 2, 7))
            composeRule.onNodeWithText(string(R.string.practice_reveal)).performClick()

            scenario.recreate()

            waitFor(string(R.string.practice_progress, 2, 7))
            // The revealed answer stays revealed: the rating buttons are still there.
            rateGood()
            waitFor(string(R.string.practice_progress, 3, 7))
            assertEquals(2, db.countRows("review_records"))
        }
    }

    @Test
    fun searchText_andResults_survive() {
        launch().use { scenario ->
            openTab(TopLevelDestination.LEARN)
            composeRule.onNode(hasText(string(R.string.vocabulary_title)) and hasClickAction()).performClick()
            composeRule.onNode(hasText(string(R.string.vocabulary_all)) and hasClickAction()).performClick()
            waitFor("経験")
            composeRule.onNode(hasSetTextAction()).performTextInput("ふえる")
            composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("経験")).fetchSemanticsNodes().isEmpty() }

            scenario.recreate()

            waitFor("増える")
            composeRule.onNode(hasSetTextAction() and hasText("ふえる")).assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodes(hasText("経験")).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun backStack_survives() {
        launch().use { scenario ->
            openTab(TopLevelDestination.LEARN)
            composeRule.onNode(hasText(string(R.string.vocabulary_title)) and hasClickAction()).performClick()
            composeRule.onNode(hasText(string(R.string.vocabulary_all)) and hasClickAction()).performClick()
            waitFor("経験")
            composeRule.onNodeWithText("経験").performClick()
            waitFor("kinh nghiệm", substring = true)

            scenario.recreate()

            waitFor("kinh nghiệm", substring = true)
            Espresso.pressBack()
            waitFor("増える") // back on the vocabulary list, not on the start screen
        }
    }

    @Test
    fun restorePreview_survives_andCanBeConfirmed() {
        AppTestState.reset(seedContent = true, seedCountsAsRealContent = true)
        runBlocking { AnalyticsFixture(ZoneId.systemDefault(), LocalDate.now()).insert(db) }
        val file = File(context.cacheDir, "config-backup.json").apply { delete() }
        runBlocking { AppTestState.appContainer.backupRepository.export(Uri.fromFile(file).toString()) }
        listOf("learning_progress", "review_records", "item_marks", "review_sessions").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openTab(TopLevelDestination.SETTINGS)
                composeRule.onNodeWithText(string(R.string.backup_restore)).performScrollTo().performClick()
                waitFor(string(R.string.backup_preview_title))

                scenario.recreate()

                waitFor(string(R.string.backup_preview_title))
                composeRule.onNodeWithText(string(R.string.backup_preview_confirm)).performClick()
                waitFor(string(R.string.backup_restore_done))
            }
        } finally {
            Intents.release()
            file.delete()
        }
        assertEquals(5, db.countRows("learning_progress"))
    }
}
