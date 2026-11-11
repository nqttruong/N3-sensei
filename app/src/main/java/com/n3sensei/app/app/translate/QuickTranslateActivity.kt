package com.n3sensei.app.app.translate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.feature.translate.OverlayPermissionScreen
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * "Dịch nhanh" in the text selection menu of other apps (Intent.ACTION_PROCESS_TEXT).
 *
 * Only an entry point: the selected text goes to the floating popup ([QuickTranslateOverlay])
 * and this activity finishes at once, so the app behind is back in front and usable. Before the
 * first popup, "Hiển thị trên ứng dụng khác" must be granted: the activity then explains why and
 * opens the system screen; back from it, the permission is checked for real before anything is
 * shown. Declining closes it, with nothing shown.
 *
 * It never starts MainActivity, never returns text (no setResult: the source text is left as it
 * is), never touches the clipboard and never logs the text. The empty `taskAffinity` and
 * `excludeFromRecents` keep it out of N3 Sensei's task and of the recent apps.
 */
class QuickTranslateActivity : ComponentActivity() {

    private val application get() = getApplication() as N3SenseiApplication
    private val overlay get() = application.quickTranslateOverlay

    /** Back from the system permission screen without granting it. */
    private var stillDenied by mutableStateOf(false)
    private var askedForPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedForPermission = savedInstanceState?.getBoolean(ASKED) ?: false
        if (handOver()) return
        val preferences = application.container.userPreferencesRepository.preferences
        setContent {
            val themeMode by remember { preferences.map { it.themeMode }.catch { emit(ThemeMode.SYSTEM) } }
                .collectAsStateWithLifecycle(initialValue = null)
            val mode = themeMode ?: return@setContent
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            N3SenseiTheme(darkTheme = dark) {
                OverlayPermissionScreen(stillDenied = stillDenied, onOpenSettings = ::openPermissionScreen, onLater = ::finish)
            }
        }
        val metrics = resources.displayMetrics
        window.setLayout(
            minOf((metrics.widthPixels * WIDTH_FRACTION).toInt(), (MAX_WIDTH_DP * metrics.density).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Back from the system screen: shown only if the permission is really granted now. */
    override fun onResume() {
        super.onResume()
        if (askedForPermission && !handOver()) stillDenied = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handOver()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ASKED, askedForPermission)
    }

    /** Gives the text to the popup and finishes; false (staying open) without the permission. */
    private fun handOver(): Boolean {
        if (!overlay.show(read(intent))) return false
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        return true
    }

    private fun openPermissionScreen() {
        askedForPermission = true
        val forThisApp = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        try {
            startActivity(forThisApp)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e: ActivityNotFoundException) {
                stillDenied = true // no such screen on this device: nothing else to open
            }
        }
    }

    private fun read(intent: Intent?): SelectedText =
        if (intent?.action != Intent.ACTION_PROCESS_TEXT) {
            SelectedText.Missing
        } else {
            SelectedText.from(
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
                readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false),
            )
        }

    private companion object {
        const val ASKED = "asked_for_overlay_permission"
        const val WIDTH_FRACTION = 0.92f
        const val MAX_WIDTH_DP = 560
    }
}
