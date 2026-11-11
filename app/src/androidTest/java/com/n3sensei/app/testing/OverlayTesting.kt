package com.n3sensei.app.testing

import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.app.translate.QuickTranslateOverlay
import com.n3sensei.app.domain.translate.SelectedText

/**
 * The floating "Dịch nhanh" popup in device tests: the overlay permission set through the
 * shell (`appops`, as the system settings switch does), the popup opened and closed on the main
 * thread.
 */
object OverlayTesting {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    val overlay: QuickTranslateOverlay get() = (context.applicationContext as N3SenseiApplication).quickTranslateOverlay

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { pfd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes()
    }

    /** "Hiển thị trên ứng dụng khác" on or off; waits until the app sees the change. */
    fun permission(granted: Boolean) {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW ${if (granted) "allow" else "deny"}")
        val end = System.currentTimeMillis() + 5_000
        while (Settings.canDrawOverlays(context) != granted && System.currentTimeMillis() < end) Thread.sleep(50)
        check(Settings.canDrawOverlays(context) == granted) { "overlay permission did not change" }
    }

    fun show(text: String): Boolean {
        var shown = false
        instrumentation.runOnMainSync { shown = overlay.show(SelectedText.from(text, readOnly = true)) }
        return shown
    }

    fun close() = instrumentation.runOnMainSync { overlay.close() }

    val isShowing: Boolean get() {
        var showing = false
        instrumentation.runOnMainSync { showing = overlay.isShowing }
        return showing
    }

    /** The popup's window: its view and layout parameters (null when closed). */
    val window: Pair<View, WindowManager.LayoutParams>? get() {
        var w: Pair<View, WindowManager.LayoutParams>? = null
        instrumentation.runOnMainSync { w = overlay.window }
        return w
    }

    /** Runs [command] as the shell (e.g. `input swipe`), waiting for it to finish. */
    fun run(command: String) {
        shell(command)
    }
}
