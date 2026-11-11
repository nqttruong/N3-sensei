package com.n3sensei.app.app.translate

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.n3sensei.app.R
import com.n3sensei.app.app.di.AppContainer
import com.n3sensei.app.app.tts.AndroidTtsEngine
import com.n3sensei.app.app.tts.TtsSpeaker
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.feature.translate.QuickTranslatePopup
import com.n3sensei.app.feature.translate.QuickTranslateViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The floating "Dịch nhanh" popup: one small window of type `TYPE_APPLICATION_OVERLAY` over
 * whatever app is shown, owned by the application (never by an activity), so the app behind
 * keeps working.
 *
 * Window: wrap-content (only the popup's own rectangle), `FLAG_NOT_FOCUSABLE` (the app behind
 * keeps the focus and its keyboard; ours never opens), `FLAG_NOT_TOUCH_MODAL` (touches outside
 * the rectangle go to the app behind), `FLAG_LAYOUT_IN_SCREEN` (screen coordinates for x/y). No
 * dim, no full-screen layer, no outside-touch watching: a tap outside never closes it.
 *
 * Lifecycle: one popup at most; a new selection updates it in place (same position). It closes
 * only with X ([close]), when the overlay permission is withdrawn, or with the process. Closing
 * stops speech, removes the window, frees the speech engine and the translators (the view model's
 * store is cleared). No service: a process showing an overlay window is kept by the system as
 * perceptible, and no foreground service type fits a translation popup.
 *
 * Position: the header's icon and text drag it (raw screen coordinates, touch slop); it is kept
 * inside the safe area and remembered as a fraction of the free room for this process's life,
 * so a rotation or the next popup keeps the learner's place.
 */
class QuickTranslateOverlay(context: Context, private val container: () -> AppContainer) {

    private val app = context.applicationContext

    /** A context for overlay windows on the default display (window context on Android 11+). */
    private val uiContext: Context = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            app.createWindowContext(defaultDisplay(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        // Android 11: a window context comes from a display context.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            app.createDisplayContext(defaultDisplay()).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        else -> app
    }

    private fun defaultDisplay(): Display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    private val windowManager = uiContext.getSystemService(WindowManager::class.java)
    private val appOps = app.getSystemService(AppOpsManager::class.java)
    private val touchSlop = ViewConfiguration.get(uiContext).scaledTouchSlop.toFloat()
    private val main = Handler(Looper.getMainLooper())

    private var popup: Popup? = null

    /** Where the learner last left the popup (fractions of the free room). */
    private var place = OverlayGeometry.DEFAULT_PLACE

    val isShowing: Boolean get() = popup != null

    /** The window, for tests: its layout parameters and view (null when closed). */
    internal val window: Pair<View, WindowManager.LayoutParams>? get() = popup?.let { it.view to it.params }

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    /**
     * Shows [text] in the popup, opening it if needed (an open one is updated in place: the older
     * translation is cancelled). False, and nothing shown, when overlays are not allowed.
     */
    @MainThread
    fun show(text: SelectedText): Boolean {
        if (!canDraw()) {
            close()
            return false
        }
        val shown = popup ?: open() ?: return false
        shown.viewModel.show(text)
        return true
    }

    /** Closes the popup (nothing if none). */
    @MainThread
    fun close() {
        val shown = popup ?: return
        popup = null
        shown.dispose()
    }

    private fun open(): Popup? = try {
        Popup().also { popup = it }
    } catch (e: WindowManager.BadTokenException) {
        null // overlays refused after all (permission withdrawn in between)
    } catch (e: SecurityException) {
        null
    }

    /** The screen minus status bar, navigation bar and cutout, in screen pixels. */
    private fun safeArea(): PxRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val b = metrics.bounds
        PxRect(b.left + insets.left, b.top + insets.top, b.right - insets.right, b.bottom - insets.bottom)
    } else {
        legacySafeArea()
    }

    /** Android 8–10: no window metrics; the bars' own sizes from the system resources. */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    @Suppress("DEPRECATION")
    private fun legacySafeArea(): PxRect {
        val size = android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }
        fun dimen(name: String): Int {
            val id = app.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) app.resources.getDimensionPixelSize(id) else 0
        }
        val portrait = size.y >= size.x
        val nav = dimen(if (portrait) "navigation_bar_height" else "navigation_bar_height_landscape")
        return PxRect(0, dimen("status_bar_height"), size.x, size.y - nav)
    }

    /** One open popup: its window, its lifecycle (for Compose) and its view model. */
    private inner class Popup : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        val viewModel: QuickTranslateViewModel
        val view: ComposeView
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "N3 Sensei · Dịch nhanh"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        private val drag = DragTracker(touchSlop)

        /** Placed once its real size is known (the first layout). */
        private var placed = false

        private val configuration = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                // Rotation or a new window size: same relative place, inside the new bounds.
                main.post { if (popup === this@Popup) moveTo(OverlayGeometry.fromPlace(place, view.width, view.height, safeArea())) }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }

        private val permission = AppOpsManager.OnOpChangedListener { _, _ ->
            // Withdrawn while shown: close cleanly (the system hides the window anyway).
            main.postDelayed({ if (popup === this@Popup && !canDraw()) close() }, PERMISSION_RECHECK_MS)
        }

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
            viewModel = ViewModelProvider.create(this, viewModelFactory { initializer { newViewModel() } })[QuickTranslateViewModel::class]
            view = ComposeView(ContextThemeWrapper(uiContext, R.style.Theme_N3Sensei)).apply {
                setViewTreeLifecycleOwner(this@Popup)
                setViewTreeViewModelStoreOwner(this@Popup)
                setViewTreeSavedStateRegistryOwner(this@Popup)
                setContent { PopupContent() }
                addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                    if (r - l != or - ol || b - t != ob - ot) onSizeChanged()
                }
            }
            val start = OverlayGeometry.fromPlace(place, 0, 0, safeArea())
            params.x = start.x
            params.y = start.y
            try {
                windowManager.addView(view, params)
            } catch (e: RuntimeException) {
                viewModelStore.clear()
                throw e
            }
            registry.currentState = Lifecycle.State.RESUMED
            app.registerComponentCallbacks(configuration)
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.packageName, permission)
        }

        private fun newViewModel(): QuickTranslateViewModel {
            val c = container()
            // One speech engine per popup, from the application context.
            return QuickTranslateViewModel(
                c.userPreferencesRepository,
                c.languageDetector,
                c.translationRepository,
                TtsSpeaker(AndroidTtsEngine.factory(app)),
                c.userContentRepository,
            )
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        private fun PopupContent() {
            val preferences = container().userPreferencesRepository.preferences
            val themeMode by remember { preferences.map { it.themeMode }.catch { emit(ThemeMode.SYSTEM) } }.collectAsState(initial = null)
            val mode = themeMode ?: return
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val state by viewModel.state.collectAsState()
            val shown = state ?: return
            N3SenseiTheme(darkTheme = dark) {
                QuickTranslatePopup(
                    state = shown,
                    // After the click is handled: the window is not removed in the middle of its own touch event.
                    onClose = { main.post { if (popup === this) close() } },
                    // Room for the shadow inside the small window.
                    modifier = Modifier.padding(4.dp),
                    dragHandleModifier = Modifier.pointerInteropFilter(onTouchEvent = ::onDragEvent),
                    onSpeak = viewModel::speak,
                    onBookmark = viewModel::bookmark,
                    onDownloadModels = viewModel::downloadModels,
                    onRetry = viewModel::retry,
                )
            }
        }

        /** The header's touches: a drag moves the window (raw coordinates); a tap does nothing. */
        fun onDragEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> drag.down(event.rawX, event.rawY, PxPoint(params.x, params.y))
                MotionEvent.ACTION_MOVE -> drag.move(event.rawX, event.rawY)?.let(::moveTo)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (drag.up()) {
                    place = OverlayGeometry.toPlace(PxPoint(params.x, params.y), view.width, view.height, safeArea())
                }
            }
            return true
        }

        private fun onSizeChanged() {
            val target = if (placed) PxPoint(params.x, params.y) else OverlayGeometry.fromPlace(place, view.width, view.height, safeArea())
            placed = true
            moveTo(target)
        }

        /** Moves the window to [point], kept inside the safe area. */
        fun moveTo(point: PxPoint) {
            val inside = OverlayGeometry.clamp(point, view.width, view.height, safeArea())
            if (!DragTracker.moved(inside, PxPoint(params.x, params.y))) return
            params.x = inside.x
            params.y = inside.y
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
        }

        fun dispose() {
            viewModel.stopSpeaking()
            appOps.stopWatchingMode(permission)
            app.unregisterComponentCallbacks(configuration)
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            view.disposeComposition()
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear() // frees the speech engine and the translators
        }
    }

    private companion object {
        const val PERMISSION_RECHECK_MS = 300L
    }
}
