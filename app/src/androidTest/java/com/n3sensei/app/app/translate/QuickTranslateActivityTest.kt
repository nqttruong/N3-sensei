package com.n3sensei.app.app.translate

import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.testing.AppTestState
import com.n3sensei.app.testing.OverlayTesting
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Dịch nhanh" opened by another app's text selection menu (ACTION_PROCESS_TEXT): the entry
 * activity hands the text to the floating popup and finishes, or first asks for the overlay
 * permission. The popup itself: one small window that lets the app behind work.
 */
@RunWith(AndroidJUnit4::class)
class QuickTranslateActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun processText(text: CharSequence?) =
        Intent(Intent.ACTION_PROCESS_TEXT).setClass(context, QuickTranslateActivity::class.java).setType("text/plain").apply {
            if (text != null) putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }

    /** As the system does from another app's menu: a new task. */
    private fun select(text: CharSequence?) = context.startActivity(processText(text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun waitForPopup() = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(QuickTranslateTags.POPUP).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) = composeRule.waitUntil(timeoutMs, condition)

    private fun activities(): List<Any> {
        val found = mutableListOf<Any>()
        instrumentation.runOnMainSync {
            Stage.entries.filter { it != Stage.DESTROYED }.forEach { stage ->
                found.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
            }
        }
        return found
    }

    private val screen get() = context.resources.displayMetrics

    @Before
    fun setUp() {
        OverlayTesting.permission(true)
    }

    @After
    fun tearDown() = runBlocking {
        OverlayTesting.close()
        OverlayTesting.permission(true)
        AppTestState.appContainer.userPreferencesRepository.setThemeMode(ThemeMode.SYSTEM)
        AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings())
    }

    @Test
    fun theSystemOffersIt_forSelectedPlainText() {
        // The int overload works on every supported API level (26+).
        @Suppress("DEPRECATION")
        val handlers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
        val ours = handlers.single { it.activityInfo.packageName == context.packageName }
        assertEquals(QuickTranslateActivity::class.java.name, ours.activityInfo.name)
        assertEquals(string(R.string.quick_translate_action), ours.loadLabel(context.packageManager).toString())
        assertTrue(ours.activityInfo.exported)
    }

    @Test
    fun theTextGoesToThePopup_theEntryFinishes_andTheAppIsNotOpened() {
        select("日本で働いた経験 · Việt Nam · experience 👍")
        waitForPopup()
        composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("日本で働いた経験 · Việt Nam · experience 👍")
        // The entry activity is gone: nothing of N3 Sensei stands between the learner and the app behind.
        waitUntil { activities().isEmpty() }
        assertTrue(activities().none { it is MainActivity })
    }

    @Test
    fun theWindow_isSmall_notFocusable_notModal_withoutDim() {
        assertTrue(OverlayTesting.show("experience"))
        waitForPopup()
        val (view, params) = OverlayTesting.window!!
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        // The app behind keeps the focus and the keyboard; touches outside go to it.
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        // Its own buttons work; no dim, no outside-touch watching.
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
        // Only its own rectangle, never the whole screen.
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)
        waitUntil { view.width > 0 }
        assertTrue("width ${view.width}", view.width <= (290 * screen.density).toInt())
        assertTrue("height ${view.height}", view.height < screen.heightPixels / 2)
        // Inside the screen.
        assertTrue(params.x >= 0 && params.y >= 0 && params.x + view.width <= screen.widthPixels && params.y + view.height <= screen.heightPixels)
    }

    @Test
    fun aNewSelection_updatesTheSamePopup_inItsPlace() {
        assertTrue(OverlayTesting.show("最初の言葉"))
        waitForPopup()
        val (firstView, params) = OverlayTesting.window!!
        // The learner moved it.
        dragHeader(-200, 300)
        val place = params.x to params.y
        select("second words")
        waitUntil { composeRule.onAllNodesWithText("second words").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(composeRule.onAllNodesWithText("最初の言葉").fetchSemanticsNodes().isEmpty())
        val (view, again) = OverlayTesting.window!!
        assertSame(firstView, view) // one popup, not a second window
        assertEquals(place, again.x to again.y)
        assertEquals(1, composeRule.onAllNodesWithTag(QuickTranslateTags.POPUP).fetchSemanticsNodes().size)
    }

    @Test
    fun onlyXCloses_aTapOrAScrollOutsideDoesNot() {
        assertTrue(OverlayTesting.show("experience"))
        waitForPopup()
        val (view, params) = OverlayTesting.window!!
        waitUntil { view.width > 0 }
        // Well away from the popup.
        val outsideX = if (params.x > screen.widthPixels / 2) 40 else screen.widthPixels - 40
        val outsideY = if (params.y > screen.heightPixels / 2) screen.heightPixels / 4 else screen.heightPixels * 3 / 4
        OverlayTesting.run("input tap $outsideX $outsideY")
        OverlayTesting.run("input swipe $outsideX $outsideY $outsideX ${outsideY - 300} 300")
        Thread.sleep(500)
        assertTrue(OverlayTesting.isShowing)
        composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).performClick()
        waitUntil { !OverlayTesting.isShowing }
        assertEquals(null, OverlayTesting.window)
        assertFalse(view.isAttachedToWindow)
    }

    /** Drags the header by (dx, dy) with a real finger (shell input). */
    private fun dragHeader(dx: Int, dy: Int, durationMs: Int = 500) {
        val (_, params) = OverlayTesting.window!!
        val handle = composeRule.onNodeWithTag(QuickTranslateTags.DRAG_HANDLE).fetchSemanticsNode().boundsInWindow
        val x = params.x + handle.center.x.toInt()
        val y = params.y + handle.center.y.toInt()
        OverlayTesting.run("input swipe $x $y ${x + dx} ${y + dy} $durationMs")
        Thread.sleep(300)
    }

    @Test
    fun theHeader_dragsIt_aTapDoesNot_andItStaysOnScreen() {
        assertTrue(OverlayTesting.show("experience"))
        waitForPopup()
        val (view, params) = OverlayTesting.window!!
        // Its final size: the translation (or the model notice) is there.
        waitUntil { composeRule.onAllNodesWithTag(QuickTranslateTags.result(Language.VIETNAMESE)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
        Thread.sleep(300) // the window follows its new size
        composeRule.waitForIdle()
        var before = params.x to params.y
        // A tap on the header: no move.
        val handle = composeRule.onNodeWithTag(QuickTranslateTags.DRAG_HANDLE).fetchSemanticsNode().boundsInWindow
        OverlayTesting.run("input tap ${params.x + handle.center.x.toInt()} ${params.y + handle.center.y.toInt()}")
        Thread.sleep(300)
        assertEquals(before, params.x to params.y)
        // Toward the middle of the screen (it opens where it was last left): follows the finger,
        // a few pixels of input rounding aside.
        val roomRight = screen.widthPixels - view.width - params.x
        val dx = if (roomRight >= 150) 150 else -150
        val dy = if (params.y > screen.heightPixels / 2) -400 else 400
        dragHeader(dx, dy)
        assertTrue("x ${params.x}, expected ${before.first + dx} (from $before by $dx, width ${view.width}, height ${view.height})", kotlin.math.abs(params.x - (before.first + dx)) <= 8)
        assertTrue("y ${params.y}, expected ${before.second + dy}", kotlin.math.abs(params.y - (before.second + dy)) <= 8)
        before = params.x to params.y
        // A fast fling far past the edges: kept wholly on screen.
        dragHeader(-3000, 5000, durationMs = 120)
        assertNotEquals(before, params.x to params.y)
        assertTrue(params.x >= 0 && params.y >= 0)
        assertTrue(params.x + view.width <= screen.widthPixels && params.y + view.height <= screen.heightPixels)
        dragHeader(3000, -5000, durationMs = 120)
        assertTrue(params.x >= 0 && params.y >= 0)
        assertTrue(params.x + view.width <= screen.widthPixels && params.y + view.height <= screen.heightPixels)
        // The bookmark and X are not drag areas: tapping them never moves it.
        before = params.x to params.y
        composeRule.onNodeWithTag(QuickTranslateTags.BOOKMARK).performClick()
        assertEquals(before, params.x to params.y)
    }

    @Test
    fun withdrawingThePermission_closesIt_withoutACrash() {
        assertTrue(OverlayTesting.show("experience"))
        waitForPopup()
        OverlayTesting.permission(false)
        waitUntil { !OverlayTesting.isShowing }
        // Asked again without the permission: nothing shown.
        assertFalse(OverlayTesting.show("experience"))
    }

    @Test
    fun withoutThePermission_itExplains_andLaterCloses_withNothingShown() {
        OverlayTesting.permission(false)
        ActivityScenario.launch<QuickTranslateActivity>(processText("experience")).use { scenario ->
            waitUntil { composeRule.onAllNodesWithTag(QuickTranslateTags.PERMISSION).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(string(R.string.quick_translate_permission_title)).assertIsDisplayed()
            assertFalse(OverlayTesting.isShowing)
            composeRule.onNodeWithTag(QuickTranslateTags.LATER).performClick()
            waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
        assertFalse(OverlayTesting.isShowing)
    }

    @Test
    fun grantedInSettings_thePopupOpensOnReturn_deniedSaysSo() {
        OverlayTesting.permission(false)
        ActivityScenario.launch<QuickTranslateActivity>(processText("experience")).use { scenario ->
            waitUntil { composeRule.onAllNodesWithTag(QuickTranslateTags.OPEN_SETTINGS).fetchSemanticsNodes().isNotEmpty() }
            // To the system screen and back without granting it: still nothing, and it says so.
            composeRule.onNodeWithTag(QuickTranslateTags.OPEN_SETTINGS).performClick()
            Thread.sleep(1_500)
            OverlayTesting.run("input keyevent KEYCODE_BACK")
            waitUntil { composeRule.onAllNodesWithText(string(R.string.quick_translate_permission_denied)).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(OverlayTesting.isShowing)
            // Again, granted this time (as the switch in that screen does): the popup on return.
            composeRule.onNodeWithTag(QuickTranslateTags.OPEN_SETTINGS).performClick()
            Thread.sleep(1_500)
            OverlayTesting.permission(true)
            OverlayTesting.run("input keyevent KEYCODE_BACK")
            waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
        waitForPopup()
        composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("experience")
    }

    @Test
    fun noText_orTooLong_saysSo_inThePopup() {
        val wrongAction = Intent(Intent.ACTION_VIEW).setClass(context, QuickTranslateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Each time: the entry gone and the popup closed before the next selection.
        fun next() {
            waitUntil { activities().isEmpty() }
            OverlayTesting.close()
        }
        context.startActivity(wrongAction)
        waitUntil { composeRule.onAllNodesWithText(string(R.string.quick_translate_no_text)).fetchSemanticsNodes().isNotEmpty() }
        next()
        select("   ")
        waitUntil { composeRule.onAllNodesWithText(string(R.string.quick_translate_no_text)).fetchSemanticsNodes().isNotEmpty() }
        next()
        val max = SelectedText.MAX_CODE_POINTS
        select("あ".repeat(max + 5))
        waitUntil { composeRule.onAllNodesWithText(string(R.string.quick_translate_too_long, max + 5, max)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun followsTheAppsDarkAndLightChoice() {
        // The overlay is not an activity window: its view is drawn into a bitmap.
        fun surfaceLuminance(): Float {
            // Called from waitUntil, on the main thread.
            val view = OverlayTesting.overlay.window?.first ?: return -1f
            if (view.width == 0) return -1f
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            // Bottom middle, inside the rounded surface (past the shadow margin), clear of the text.
            val pixel = bitmap.getPixel(view.width / 2, view.height - (10 * screen.density).toInt())
            bitmap.recycle()
            return androidx.compose.ui.graphics.Color(pixel).luminance()
        }
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setThemeMode(ThemeMode.DARK) }
        assertTrue(OverlayTesting.show("経験"))
        waitForPopup()
        waitUntil { surfaceLuminance().let { it in 0f..0.3f } }
        OverlayTesting.close()
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setThemeMode(ThemeMode.LIGHT) }
        assertTrue(OverlayTesting.show("経験"))
        waitForPopup()
        waitUntil { surfaceLuminance() > 0.6f }
    }
}
