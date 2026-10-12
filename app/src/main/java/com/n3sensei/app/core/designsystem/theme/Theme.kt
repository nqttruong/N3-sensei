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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Slate99,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Vermilion40,
    onSecondary = Slate99,
    secondaryContainer = Vermilion90,
    onSecondaryContainer = Vermilion10,
    tertiary = Matcha40,
    onTertiary = Slate99,
    tertiaryContainer = Matcha90,
    onTertiaryContainer = Matcha10,
    background = Slate99,
    onBackground = Slate10,
    surface = Slate99,
    onSurface = Slate10,
    surfaceVariant = Slate90,
    onSurfaceVariant = Slate30,
    surfaceContainer = Slate95,
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary = Vermilion80,
    onSecondary = Vermilion20,
    secondaryContainer = Vermilion30,
    onSecondaryContainer = Vermilion90,
    tertiary = Matcha80,
    onTertiary = Matcha20,
    tertiaryContainer = Matcha30,
    onTertiaryContainer = Matcha90,
    background = Slate10,
    onBackground = Slate90,
    surface = Slate10,
    onSurface = Slate90,
    surfaceVariant = Slate30,
    onSurfaceVariant = Slate80,
    surfaceContainer = Slate20,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
