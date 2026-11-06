package com.n3sensei.app.app.translate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.app.tts.AndroidTtsEngine
import com.n3sensei.app.app.tts.TtsSpeaker
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.feature.translate.QuickTranslateScreen
import com.n3sensei.app.feature.translate.QuickTranslateViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * "Dịch nhanh" in the text selection menu of other apps (Intent.ACTION_PROCESS_TEXT).
 *
 * A floating window over the source app (theme `Theme.N3Sensei.QuickTranslate`): it shows the
 * selected text and closes with X, Back or a tap outside, back to that app. It never starts
 * MainActivity, never returns text (no setResult: the source text is left as it is), never
 * touches the clipboard and never logs the text.
 *
 * Task: started for a result, it opens on top of the source app's task and leaves with it;
 * started as a new task, the empty `taskAffinity` keeps it out of N3 Sensei's own task and
 * `excludeFromRecents` out of the recent apps. Standard launch mode: a new selection is a new
 * instance; [onNewIntent] still replaces the text if a caller reuses the instance.
 */
class QuickTranslateActivity : ComponentActivity() {

    private val container get() = (application as N3SenseiApplication).container

    private val viewModel: QuickTranslateViewModel by viewModels {
        viewModelFactory {
            initializer {
                // One speech engine per dialog, from the application context (never this activity).
                QuickTranslateViewModel(
                    container.userPreferencesRepository,
                    container.languageDetector,
                    container.translationRepository,
                    TtsSpeaker(AndroidTtsEngine.factory(applicationContext)),
                    container.userContentRepository,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the same text comes back and the dialog's choices are kept.
        viewModel.show(read(intent))
        val preferences = container.userPreferencesRepository.preferences
        setContent {
            // The app's light/dark choice, as in the app itself; drawn once it is known (milliseconds).
            val themeMode by remember { preferences.map { it.themeMode }.catch { emit(ThemeMode.SYSTEM) } }
                .collectAsStateWithLifecycle(initialValue = null)
            val mode = themeMode ?: return@setContent
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val shown = state ?: return@setContent
            N3SenseiTheme(darkTheme = dark) {
                QuickTranslateScreen(
                    state = shown,
                    onChooseSource = viewModel::chooseSource,
                    onChooseMode = viewModel::chooseMode,
                    onClose = ::finish,
                    onDownloadModels = viewModel::downloadModels,
                    onToggleDetails = viewModel::toggleDetails,
                    onSpeak = viewModel::speak,
                    onInstallVoices = ::openVoiceInstaller,
                    onBookmark = viewModel::bookmark,
                    onRetry = viewModel::retry,
                )
            }
        }
        sizeWindow()
    }

    /** Reading stops as soon as the dialog is no longer seen; the engine is freed when it closes. */
    override fun onStop() {
        super.onStop()
        viewModel.stopSpeaking()
    }

    /** The speech engine's own voice download screen; nothing happens if the device has none. */
    private fun openVoiceInstaller() {
        try {
            startActivity(AndroidTtsEngine.installVoicesIntent())
        } catch (e: ActivityNotFoundException) {
            // No engine screen to open: the dialog keeps saying the voice is missing.
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.show(read(intent))
    }

    /** A dialog width: most of a phone's width, at most 560 dp on large screens; height follows the content. */
    private fun sizeWindow() {
        val metrics = resources.displayMetrics
        val width = minOf((metrics.widthPixels * WIDTH_FRACTION).toInt(), (MAX_WIDTH_DP * metrics.density).toInt())
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
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
        const val WIDTH_FRACTION = 0.92f
        const val MAX_WIDTH_DP = 560
    }
}
