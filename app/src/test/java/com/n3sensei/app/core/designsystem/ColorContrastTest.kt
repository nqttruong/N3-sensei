package com.n3sensei.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.n3sensei.app.core.designsystem.theme.DarkColors
import com.n3sensei.app.core.designsystem.theme.LightColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast of every text/background pair the screens use, in both themes:
 * at least 4.5:1 for text (AA, normal size). Colours not set by the app come from Material 3.
 */
class ColorContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun pairs(c: ColorScheme) = mapOf(
        "onSurface/surface" to (c.onSurface to c.surface),
        "onSurfaceVariant/surface (notes, captions)" to (c.onSurfaceVariant to c.surface),
        "onSurfaceVariant/surfaceVariant (badges, Khó)" to (c.onSurfaceVariant to c.surfaceVariant),
        "onSurfaceVariant/surfaceContainerLow (cards)" to (c.onSurfaceVariant to c.surfaceContainerLow),
        "primary/surface (section headers, labels)" to (c.primary to c.surface),
        "onPrimary/primary (buttons)" to (c.onPrimary to c.primary),
        "onPrimaryContainer/primaryContainer (Tốt)" to (c.onPrimaryContainer to c.primaryContainer),
        "onSecondaryContainer/secondaryContainer (selected chips)" to (c.onSecondaryContainer to c.secondaryContainer),
        "onTertiaryContainer/tertiaryContainer (Dễ, Ước tính)" to (c.onTertiaryContainer to c.tertiaryContainer),
        "onErrorContainer/errorContainer (Quên)" to (c.onErrorContainer to c.errorContainer),
        "error/surface (error messages)" to (c.error to c.surface),
        "tertiary/surface (goal reached)" to (c.tertiary to c.surface),
        "onSurface/surfaceContainerLow (N3Card text)" to (c.onSurface to c.surfaceContainerLow),
        "onSurfaceVariant/surfaceContainerHigh (InfoBanner)" to (c.onSurfaceVariant to c.surfaceContainerHigh),
        "onSurfaceVariant/surfaceContainerHighest (neutral Tag)" to (c.onSurfaceVariant to c.surfaceContainerHighest),
        "onSurface/surfaceContainerHighest (kanji words on cards)" to (c.onSurface to c.surfaceContainerHighest),
        "primary/surfaceContainerLow (headers inside cards)" to (c.primary to c.surfaceContainerLow),
        "onPrimaryContainer/primaryContainer (accent Tag, emphasis card, glyph tile)" to (c.onPrimaryContainer to c.primaryContainer),
        "onSurface/surfaceContainer (navigation bar labels)" to (c.onSurface to c.surfaceContainer),
        "onSurfaceVariant/surfaceContainer (unselected tab labels)" to (c.onSurfaceVariant to c.surfaceContainer),
    )

    private fun check(name: String, scheme: ColorScheme) {
        val failing = pairs(scheme).mapValues { (_, p) -> contrast(p.first, p.second) }.filterValues { it < 4.5 }
        assertTrue("$name below 4.5:1: ${failing.mapValues { "%.2f".format(it.value) }}", failing.isEmpty())
    }

    /**
     * Roles left at the Material 3 baseline are purple-tinted and clash with the moss palette;
     * every role the components read must be set by the app.
     */
    private fun checkAllRolesSet(name: String, scheme: ColorScheme, baseline: ColorScheme) {
        val roles = mapOf(
            "surfaceContainerLowest" to (scheme.surfaceContainerLowest to baseline.surfaceContainerLowest),
            "surfaceContainerLow" to (scheme.surfaceContainerLow to baseline.surfaceContainerLow),
            "surfaceContainer" to (scheme.surfaceContainer to baseline.surfaceContainer),
            "surfaceContainerHigh" to (scheme.surfaceContainerHigh to baseline.surfaceContainerHigh),
            "surfaceContainerHighest" to (scheme.surfaceContainerHighest to baseline.surfaceContainerHighest),
            "surfaceDim" to (scheme.surfaceDim to baseline.surfaceDim),
            "surfaceBright" to (scheme.surfaceBright to baseline.surfaceBright),
            "outline" to (scheme.outline to baseline.outline),
            "outlineVariant" to (scheme.outlineVariant to baseline.outlineVariant),
            "inversePrimary" to (scheme.inversePrimary to baseline.inversePrimary),
            "inverseSurface" to (scheme.inverseSurface to baseline.inverseSurface),
            "error" to (scheme.error to baseline.error),
            "errorContainer" to (scheme.errorContainer to baseline.errorContainer),
        )
        val unset = roles.filterValues { (app, base) -> app == base }.keys
        assertTrue("$name roles left at the Material baseline: $unset", unset.isEmpty())
    }

    @Test
    fun lightTheme_setsEveryRole() = checkAllRolesSet("light", LightColors, lightColorScheme())

    @Test
    fun darkTheme_setsEveryRole() = checkAllRolesSet("dark", DarkColors, darkColorScheme())

    @Test
    fun lightTheme_textContrastIsAtLeastAA() = check("light", LightColors)

    @Test
    fun darkTheme_textContrastIsAtLeastAA() = check("dark", DarkColors)
}
