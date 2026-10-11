package com.n3sensei.app.feature

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Settings → export → wipe learner data → restore, through the real UI. The system file
 * picker is replaced by a stub that answers with a file in the app's cache.
 */
@RunWith(AndroidJUnit4::class)
class BackupEndToEndTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
    private val file = File(context.cacheDir, "e2e-backup.json")
    private val db get() = AppTestState.appContainer.database

    @Before
    fun setUp() {
        Intents.init()
        file.delete()
        AppTestState.reset(seedContent = true, seedCountsAsRealContent = true)
        runBlocking { AnalyticsFixture(ZoneId.systemDefault(), LocalDate.now()).insert(db) }
    }

    @After
    fun tearDown() {
        Intents.release()
        file.delete()
    }

    private fun pickerAnswers(action: String, target: File) {
        intending(hasAction(action)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))))
    }

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openSettingsAndClick(@StringRes action: Int) {
        composeRule.onNodeWithTag(TopLevelDestination.SETTINGS.testTag).performClick()
        composeRule.onNodeWithText(string(action)).performScrollTo().performClick()
    }

    private fun wipeLearnerData() {
        listOf("learning_progress", "review_records", "item_marks", "review_sessions").forEach {
            db.openHelper.writableDatabase.execSQL("DELETE FROM $it")
        }
    }

    @Test
    fun exportWipeRestore_bringsProgressBack() {
        pickerAnswers(Intent.ACTION_CREATE_DOCUMENT, file)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettingsAndClick(R.string.backup_export)
            waitFor(string(R.string.backup_export_done))
            // 4 fixture items + the removed item "gone" (kept, like all learner data).
            composeRule.onNodeWithText(string(R.string.backup_count_line, string(R.string.backup_section_progress), 5)).assertExists()
            composeRule.onNodeWithText(string(R.string.import_report_close)).performClick()
        }
        assertTrue(file.length() > 0)

        wipeLearnerData()
        assertEquals(0, db.countRows("learning_progress"))
        pickerAnswers(Intent.ACTION_OPEN_DOCUMENT, file)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettingsAndClick(R.string.backup_restore)
            waitFor(string(R.string.backup_preview_title))
            composeRule.onNodeWithText(
                string(R.string.backup_plan_line, string(R.string.backup_section_progress), 5, 5, 0, 0),
            ).assertExists()
            composeRule.onNodeWithText(string(R.string.backup_preview_confirm)).performClick()
            waitFor(string(R.string.backup_restore_done))
            composeRule.onNodeWithText(string(R.string.backup_result_line, string(R.string.backup_section_records), 8, 0, 0)).assertExists()
        }
        assertEquals(5, db.countRows("learning_progress"))
        assertEquals(8, db.countRows("review_records"))
    }

    @Test
    fun cancelledPreview_restoresNothing() {
        pickerAnswers(Intent.ACTION_CREATE_DOCUMENT, file)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettingsAndClick(R.string.backup_export)
            waitFor(string(R.string.backup_export_done))
        }
        wipeLearnerData()
        pickerAnswers(Intent.ACTION_OPEN_DOCUMENT, file)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettingsAndClick(R.string.backup_restore)
            waitFor(string(R.string.backup_preview_title))
            composeRule.onNodeWithText(string(R.string.backup_cancel)).performClick()
            composeRule.waitForIdle()
        }
        assertEquals(0, db.countRows("learning_progress"))
    }

    @Test
    fun corruptFile_isRejected_andDataIsUntouched() {
        file.writeText("{\"format\":\"n3sensei-backup\",\"formatVersion\":1,\"progress\":[{\"itemType\":")
        pickerAnswers(Intent.ACTION_OPEN_DOCUMENT, file)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettingsAndClick(R.string.backup_restore)
            waitFor(string(R.string.backup_rejected))
            composeRule.onNodeWithText(string(R.string.backup_nothing_changed)).assertExists()
        }
        assertEquals(5, db.countRows("learning_progress"))
    }
}
