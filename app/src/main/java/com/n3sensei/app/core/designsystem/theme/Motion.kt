package com.n3sensei.app.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens. Animations are short and only show a change of state (answer revealed, next
 * card, progress made); nothing loops or decorates. Compose already multiplies durations by the
 * system "animator duration scale"; [LocalReduceMotion] additionally drops movement (slides,
 * flips) when the learner turned animations off, leaving instant changes.
 */
object N3Motion {
    const val SHORT = 150
    const val MEDIUM = 220
    const val LONG = 320

    /** Things arriving on screen. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things changing in place. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** [tween] of [durationMillis], or an instant change when motion is reduced. */
    fun <T> spec(reduce: Boolean, durationMillis: Int = MEDIUM, easing: Easing = StandardEasing): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween(durationMillis, easing = easing)
}

/** True when the system asks for no animations ("Xóa hiệu ứng động" / animator scale 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Reads the system animator duration scale and follows changes while the app is open. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read(): Boolean = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = read()
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }.isSuccess
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return reduce
}
