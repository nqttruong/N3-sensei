package com.n3sensei.app.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Every role is set: roles left to Material 3 defaults are purple-tinted and clash with moss.
internal val LightColors = lightColorScheme(
    primary = Moss40,
    onPrimary = Paper100,
    primaryContainer = Moss90,
    onPrimaryContainer = Moss10,
    inversePrimary = Moss80,
    secondary = Sage40,
    onSecondary = Paper100,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Ai40,
    onTertiary = Paper100,
    tertiaryContainer = Ai90,
    onTertiaryContainer = Ai10,
    error = Shu40,
    onError = Paper100,
    errorContainer = Shu90,
    onErrorContainer = Shu10,
    background = Paper98,
    onBackground = Ink10,
    surface = Paper98,
    onSurface = Ink10,
    surfaceVariant = Paper90,
    onSurfaceVariant = Ink30,
    surfaceTint = Moss40,
    inverseSurface = Ink20,
    inverseOnSurface = Paper94,
    outline = Ink50,
    outlineVariant = Paper80,
    scrim = Ink4,
    surfaceBright = Paper98,
    surfaceDim = Paper87,
    surfaceContainerLowest = Paper99,
    surfaceContainerLow = Paper96,
    surfaceContainer = Paper94,
    surfaceContainerHigh = Paper92,
    surfaceContainerHighest = Paper90,
)

internal val DarkColors = darkColorScheme(
    primary = Moss80,
    onPrimary = Moss20,
    primaryContainer = Moss30,
    onPrimaryContainer = Moss90,
    inversePrimary = Moss40,
    secondary = Sage80,
    onSecondary = Sage20,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Ai80,
    onTertiary = Ai20,
    tertiaryContainer = Ai30,
    onTertiaryContainer = Ai90,
    error = Shu80,
    onError = Shu20,
    errorContainer = Shu30,
    onErrorContainer = Shu90,
    background = Ink6,
    onBackground = Paper90,
    surface = Ink6,
    onSurface = Paper90,
    surfaceVariant = Ink30,
    onSurfaceVariant = Paper80,
    surfaceTint = Moss80,
    inverseSurface = Paper90,
    inverseOnSurface = Ink20,
    outline = Ink60,
    outlineVariant = Ink30,
    scrim = Ink4,
    surfaceBright = Ink24,
    surfaceDim = Ink6,
    surfaceContainerLowest = Ink4,
    surfaceContainerLow = Ink10,
    surfaceContainer = Ink12,
    surfaceContainerHigh = Ink17,
    surfaceContainerHighest = Ink22,
)

@Composable
fun N3SenseiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default to keep the brand palette; can be exposed as a setting later.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // enableEdgeToEdge() picks the status bar icon colour from the *system* dark mode; when the
    // user chose a different theme in the app the icons would be invisible (white on white).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
